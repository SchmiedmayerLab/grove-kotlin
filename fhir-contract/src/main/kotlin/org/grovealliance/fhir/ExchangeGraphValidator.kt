//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Base64

/**
 * Enforces the exchange protocol on one Bundle's JSON tree and reports the first registered rule it breaks.
 *
 * The checks run in the conformance kit's order over the same JSON shapes, so one mutation yields the same
 * code on every platform; a failure no registered rule names is reported as `mobile-exchange.unclassified`.
 * Rules the registry assigns to clients run after the kit-mirrored checks.
 */
@Suppress("TooManyFunctions", "LargeClass") // One rule table, one check per rule; splitting it would hide the order.
internal object ExchangeGraphValidator {
    private val opaqueRoles: Set<String> = OpaqueIdentityKind.entries.mapTo(mutableSetOf()) { it.identifierRole.code }
    private val versionPattern = Regex("0|[1-9][0-9]*")
    private val fhirCode = Regex("""[^\s]+(?: [^\s]+)*""")
    private val byteCount = Regex("0|[1-9][0-9]*")

    fun validate(kind: ExchangeGraphKind, bundle: JsonToken.Object) {
        val other = if (kind == ExchangeGraphKind.ACTIVE) ExchangeGraphKind.RETRACTION else ExchangeGraphKind.ACTIVE
        val profiles = bundle.profiles().orEmpty()
        if (kind.profile !in profiles || other.profile in profiles) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_BUNDLE_PROFILE,
                "Bundle.meta.profile",
                "The Bundle must claim its ${kind.name.lowercase()} event profile and not the other.",
            )
        }
        if (bundle.text("type") != "collection") unclassified("Bundle.type", "An exchange Bundle has type collection.")
        val event = completeIdentifier(bundle["identifier"], "Bundle.identifier")
        if (roleOf(bundle["identifier"], "Bundle.identifier", "Bundle.identifier") != GroveIdentifierRole.EVENT.code) {
            unclassified("Bundle.identifier.type", "The Bundle identifier must carry the event role.")
        }
        if (!ExchangeProtocol.eventIdentityValue.matches(event.value)) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_EVENT_IDENTITY,
                "Bundle.identifier.value",
                "The Bundle identifier is not a canonical event identity.",
            )
        }
        validateIdentitySystemRoles(bundle)
        val entries = bundle.items("entry")
        if (entries.isNullOrEmpty()) {
            fail(ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_REQUIRED, "Bundle.entry", "An exchange Bundle contains entries.")
        }
        val graph = GraphIndex(kind, event)
        entries.forEachIndexed { index, entry -> validateEntry(graph, index, entry) }
        if (kind == ExchangeGraphKind.RETRACTION) validateLogicalRetractionTargets(graph)
        graph.resources.forEachIndexed { index, resource -> validateReferences(graph, index, resource) }
        if (kind == ExchangeGraphKind.ACTIVE) validateConnectivity(graph)
        val lifecycle = classifyLifecycle(graph)
        when (kind) {
            ExchangeGraphKind.ACTIVE -> validateActiveEvent(graph, lifecycle)
            ExchangeGraphKind.RETRACTION -> validateRetractionEvent(graph, lifecycle)
        }
        validateClientRules(graph, bundle, entries, lifecycle)
    }

    // --- entries -------------------------------------------------------------------------------

    private fun validateEntry(graph: GraphIndex, index: Int, entryToken: JsonToken) {
        val label = "Bundle.entry[$index]"
        val entry = entryToken.asObject()
        val resource = entry?.obj("resource") ?: unclassified(label, "Every entry contains a resource.")
        val type = resource.resourceType
        val admitted = if (graph.kind == ExchangeGraphKind.ACTIVE) graph.activeTypes else RETRACTION_TYPES
        if (type !in admitted) {
            if (graph.kind == ExchangeGraphKind.RETRACTION) {
                fail(
                    ExchangeGraphRule.MOBILE_RETRACTION_NO_CLINICAL_COPY,
                    "$label.resource",
                    "A retraction carries only its Provenance and Device agents.",
                )
            }
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_RESOURCE_TYPE,
                "$label.resource.resourceType",
                "The resource type is not admitted by the active event profile.",
            )
        }
        if ("contained" in resource) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_CONTAINED_RESOURCE_PROHIBITED,
                "$label.resource.contained",
                "Every graph node is an addressable Bundle entry.",
            )
        }
        if (graph.kind == ExchangeGraphKind.ACTIVE && type in ExchangeContract.activeOutputResourceTypes) {
            val typed = typedIdentifiers(resource, "$label.resource")
            if (!typed.keys.containsAll(listOf(GroveIdentifierRole.SOURCE_RECORD, GroveIdentifierRole.SOURCE_OUTPUT))) {
                fail(
                    ExchangeGraphRule.MOBILE_OUTPUT_SOURCE_OUTPUT_REQUIRED,
                    "$label.resource.identifier",
                    "An active output carries typed source-record and source-output identities.",
                )
            }
        }
        val fullUrl = validateEntryKey(graph, entry, resource, label)
        if (!graph.add(fullUrl, resource)) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_DISTINCT_ENTRY_KEY,
                "$label.extension.valueIdentifier",
                "No two entries share one entry key.",
            )
        }
        validateEntryClaims(graph, resource, label, index)
    }

    /** The deterministic full URL of one entry, after its key and fullUrl checks. */
    private fun validateEntryKey(
        graph: GraphIndex,
        entry: JsonToken.Object,
        resource: JsonToken.Object,
        label: String,
    ): String {
        val identities = entry.items("extension").orEmpty().mapNotNull { it.asObject() }
            .filter { it.text("url") == ExchangeContract.ENTRY_NODE_KEY_EXTENSION }
            .map { it["valueIdentifier"] }
        if (identities.size != 1) {
            fail(ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_NODE_KEY, label, "Every entry carries one entry node key.")
        }
        val keyLocation = "$label.extension.valueIdentifier"
        val key = identities.single()
        val keyPair = completeIdentifier(key, "$label entry key")
        val keyRole = roleOf(key, "$label entry key", keyLocation)
        val selected = selectedEntryIdentifier(resource, "$label.resource")
        if (selected == null) {
            graph.nodeRoles[keyPair.fullUrl] = validateEntryNodeKey(graph, keyPair, keyRole, keyLocation)
        } else if (keyRole != selected.role.code || keyPair != selected.identifier) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY_SELECTION,
                keyLocation,
                "The entry key is the resource's highest-priority typed identifier.",
            )
        }
        val expectedFullUrl = keyPair.fullUrl
        if (entry.text("fullUrl") != expectedFullUrl) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_DETERMINISTIC_FULL_URL,
                "$label.fullUrl",
                "The fullUrl is the deterministic UUID URN of the entry key.",
            )
        }
        return expectedFullUrl
    }

    /** Checks one entry-node key and returns its node role. */
    private fun validateEntryNodeKey(
        graph: GraphIndex,
        keyPair: BusinessIdentifier,
        keyRole: String,
        keyLocation: String,
    ): String {
        val match = ExchangeProtocol.entryNodeIdentityValue.matchEntire(keyPair.value)
        if (keyRole != GroveIdentifierRole.ENTRY_NODE.code || match == null) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY_SELECTION,
                keyLocation,
                "A resource without a typed business identity uses a canonical entry-node key.",
            )
        }
        val nodeRole = match.groupValues[1]
        if (match.groupValues[2].toLong() != graph.nextOrdinal(nodeRole)) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_NODE_ORDINAL,
                "$keyLocation.value",
                "The entry-node ordinal is the zero-based position of its role in entry order.",
            )
        }
        if (keyPair.value != EntryNodeKey.value(graph.eventIdentifier, nodeRole, match.groupValues[2])) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_NODE_DIGEST,
                "$keyLocation.value",
                "The entry-node digest does not match its event, role and ordinal.",
            )
        }
        return nodeRole
    }

    private fun validateEntryClaims(graph: GraphIndex, resource: JsonToken.Object, label: String, index: Int) {
        val resourceLabel = "$label.resource"
        validateWriterRecordRevision(resource, resourceLabel)
        validateAdapterProfileClaim(resource, resourceLabel)
        validateAdapterSourceMarker(resource, resourceLabel)
        validateAdapterConversionProvenance(resource, resourceLabel)
        validateRecordingFormat(resource, resourceLabel)
        if (graph.kind == ExchangeGraphKind.ACTIVE) {
            validateActiveObservationClaim(resource, resourceLabel, index)
            validateAdapterOnlyOutputClaim(resource, resourceLabel)
            validateDocumentReferenceClaim(resource, resourceLabel)
            validateActiveProvenanceClaim(resource, resourceLabel)
            validateFixedQuantitySemantics(resource, index)
        } else {
            validateRetractionProvenanceClaim(resource, resourceLabel)
        }
        validateSupportingProfileClaim(resource, resourceLabel)
    }

    private fun validateWriterRecordRevision(resource: JsonToken.Object, label: String) {
        val identifiers = resource["identifier"].let { it.asItems() ?: listOf(it) }.mapNotNull { it.asObject() }
        val hasWriterMarker = identifiers.any { GroveIdentifierRole.WRITER_RECORD.code in it.groveRoleCodes() }
        val versions = resource.topLevelExtensions(ExchangeContract.WRITER_RECORD_VERSION_EXTENSION)
        if (!hasWriterMarker && versions.isEmpty()) return
        val writerPresent = GroveIdentifierRole.WRITER_RECORD in typedIdentifiers(resource, label)
        if (versions.size > 1 || (versions.isNotEmpty() && !writerPresent)) {
            unclassified(label, "A writer record version requires exactly one writer-record identity.")
        }
        val pairedAdapter = resource.profiles().orEmpty().any { profile -> ADAPTER_PROFILE_PREFIXES.any(profile::startsWith) }
        if (pairedAdapter && writerPresent != (versions.size == 1)) {
            unclassified(label, "An adapter output carries writer-record identity and version together.")
        }
        val version = versions.singleOrNull() ?: return
        val value = version.text("valueString")
        val canonical = version.members.keys == setOf("url", "valueString") && value != null && versionPattern.matches(value)
        if (!canonical) unclassified("$label.extension", "A writer record version is a canonical non-negative decimal integer.")
    }

    private fun validateAdapterProfileClaim(resource: JsonToken.Object, label: String) {
        if (resource.resourceType != "Observation") return
        val profiles = resource.profiles() ?: unclassified("$label.meta.profile", "Profiles are strings.")
        val adapters = profiles.filter { it in ExchangeContract.adapterObservationProfiles }.toSet()
        val semantic = profiles.filter { it in ExchangeContract.semanticObservationProfiles }.toSet()
        if (adapters.isEmpty()) return
        val exactPair = adapters.size == 1 && semantic.size == 1 && profiles.size == 2 && profiles.toSet().size == 2
        if (!exactPair) {
            unclassified("$label.meta.profile", "An adapter Observation claims exactly one semantic and one adapter profile.")
        }
    }

    private fun validateAdapterSourceMarker(resource: JsonToken.Object, label: String) {
        if (resource.resourceType != "Observation") return
        val profiles = resource.profiles()?.toSet() ?: unclassified("$label.meta.profile", "Profiles are strings.")
        val urls = resource.items("extension").orEmpty().mapNotNull { it.asObject()?.text("url") }.toSet()
        ExchangeContract.adapterSourceMarkers.forEach { (markerUrl, admitted) ->
            if (markerUrl in urls && profiles.none { it in admitted }) {
                fail(
                    ExchangeGraphRule.MOBILE_OUTPUT_ADAPTER_SOURCE_MARKER,
                    "Observation.extension",
                    "An adapter source marker requires that adapter's profile.",
                )
            }
        }
    }

    private fun validateAdapterConversionProvenance(resource: JsonToken.Object, label: String) {
        if (resource.resourceType != "Provenance") return
        val profiles = resource.profiles() ?: unclassified("$label.meta.profile", "Profiles are strings.")
        val matches = ExchangeContract.adapterConversionProvenanceClaims.filter { it.profile in profiles }
        val claim = matches.singleOrNull()?.takeIf { profiles == listOf(it.profile) } ?: when {
            matches.isEmpty() -> return
            else -> unclassified("$label.meta.profile", "An adapter Provenance claims exactly its adapter profile.")
        }
        val targets = resource.items("target")
        if (targets.isNullOrEmpty()) unclassified("$label.target", "A conversion Provenance targets its outputs.")
        targets.forEachIndexed { index, target ->
            if (target.asObject()?.get("reference") !is JsonToken.Text) {
                unclassified("$label.target[$index]", "Every conversion target carries an exact reference.")
            }
        }
        val entity = adapterSourceEntity(resource, label)
        if (claim.adapter == HEALTH_CONNECT_ADAPTER) validateWriterAgent(entity)
    }

    private fun adapterSourceEntity(resource: JsonToken.Object, label: String): JsonToken.Object {
        val entity = resource.items("entity")?.singleOrNull()?.asObject()
            ?: unclassified("$label.entity", "A conversion Provenance carries exactly one source entity.")
        val what = entity.obj("what")
        if (entity.text("role") != "source" || what == null || "reference" in what) {
            unclassified("$label.entity", "The source is exactly one Identifier entity.")
        }
        val identifier = what["identifier"]
        val source = completeIdentifier(identifier, "$label source entity")
        val role = roleOf(identifier, "$label source entity", "Provenance.entity[0].what.identifier")
        if (role != GroveIdentifierRole.SOURCE_RECORD.code) {
            unclassified("$label.entity", "The source entity carries the source-record role.")
        }
        if (!ExchangeProtocol.opaqueIdentityValue.matches(source.value)) {
            unclassified("$label.entity", "The source entity is a canonical opaque identity.")
        }
        return entity
    }

    private fun validateWriterAgent(entity: JsonToken.Object) {
        val rule = ExchangeGraphRule.HEALTH_CONNECT_PROVENANCE_DATA_ORIGIN_AGENT
        val location = "Provenance.entity[0].agent"
        val agent = entity.items("agent")?.singleOrNull()
            ?: fail(rule, location, "A Health Connect source entity carries exactly one enterer agent.")
        val agentObject = agent.asObject()
        val who = agentObject?.obj("who")
        val entererCodes = agentObject?.get("type")?.codingPairs().orEmpty()
            .filter { (system, _) -> system == ExchangeContract.PROVENANCE_PARTICIPANT }
            .map { (_, code) -> code }
        val identifierOnlyDevice = who != null && "reference" !in who && "resource" !in who && who.text("type") == "Device"
        if (who == null || entererCodes != listOf(ENTERER) || !identifierOnlyDevice) {
            fail(rule, "$location[0].who", "A Health Connect writer is an identifier-only Device Reference.")
        }
        val writer = who["identifier"].identifierPair()
            ?: fail(rule, "$location[0].who.identifier", "A Health Connect writer carries a complete Identifier.")
        if (writer.system.value != ExchangeContract.HEALTH_CONNECT_WRITER_PACKAGE_SYSTEM || writer.value.isBlank()) {
            fail(rule, "$location[0].who.identifier", "A Health Connect writer carries its non-blank Android package name.")
        }
    }

    private fun validateRecordingFormat(resource: JsonToken.Object, label: String) {
        if (admittedDocumentClaim(resource) == null) return
        val contents = resource.items("content")
        if (contents.isNullOrEmpty()) {
            fail(
                ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT,
                "DocumentReference.content",
                "A recording document carries its payload.",
            )
        }
        contents.forEachIndexed { index, content ->
            val rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_FORMAT
            val formatLocation = "DocumentReference.content[$index].format"
            val format = content.asObject()?.obj("format")
                ?: fail(rule, formatLocation, "$label content[$index] declares no registry payload format.")
            if (format.text("system") != ExchangeContract.RECORDING_FORMAT_SYSTEM) {
                fail(rule, "$formatLocation.system", "The format system is the Grove recording-format registry.")
            }
            val registered = format.text("code")?.let(ExchangeContract.recordingFormats::get)
                ?: fail(rule, "$formatLocation.code", "The format code is registered.")
            if ("version" in format) fail(rule, "$formatLocation.version", "The format omits a release-coupled Coding.version.")
            val attachment = content.asObject()?.obj("attachment")
            val attachmentLocation = "DocumentReference.content[$index].attachment"
            if (attachment?.text("contentType") !in registered.contentTypes) {
                fail(rule, "$attachmentLocation.contentType", "The attachment media type matches its registry format.")
            }
            validateRecordingAttachment(requireNotNull(attachment), "$label.content[$index].attachment", attachmentLocation)
        }
    }

    private fun validateRecordingAttachment(attachment: JsonToken.Object, label: String, location: String) {
        val data = attachment.text("data")
        val hasUrl = !attachment.text("url").isNullOrEmpty()
        if ((data != null) == hasUrl) unclassified(label, "An attachment carries exactly one of data or url.")
        val size = (attachment["size"] as? JsonToken.Number)?.lexeme?.takeIf(byteCount::matches)
            ?: unclassified("$label.size", "Attachment.size is a byte count.")
        val digest = attachment.text("hash")?.let(::strictBase64)?.takeIf { it.size == SHA1_BYTES }
            ?: unclassified("$label.hash", "Attachment.hash is one base64 SHA-1 digest.")
        if (data == null) return
        val payload = strictBase64(data) ?: unclassified("$label.data", "Attachment.data is valid base64.")
        val rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_EMBEDDED_INTEGRITY
        if (payload.size.toString() != size) fail(rule, "$location.size", "Attachment.size does not match the embedded bytes.")
        if (!MessageDigest.getInstance("SHA-1").digest(payload).contentEquals(digest)) {
            fail(rule, "$location.hash", "Attachment.hash does not match the embedded bytes.")
        }
    }

    private fun validateActiveObservationClaim(resource: JsonToken.Object, label: String, index: Int) {
        if (resource.resourceType != "Observation") return
        val profiles = resource.profiles()
        if (profiles.isNullOrEmpty() || profiles.size != profiles.toSet().size) {
            fail(
                ExchangeGraphRule.MOBILE_OUTPUT_SEMANTIC_PROFILE,
                "Observation.meta.profile",
                "An active Observation carries a non-repeated direct profile claim.",
            )
        }
        val direct = profiles.toSet()
        if (direct.size == 1 && direct.single() in ExchangeContract.singleProfileObservationClaims) {
            validateQuantityValueDomain(resource, index, direct.single())
            return
        }
        if (direct == ExchangeContract.hybridObservationProfiles) return
        val semantic = direct.filter { it in ExchangeContract.semanticObservationProfiles }.toSet()
        val adapters = direct.filter { it in ExchangeContract.adapterObservationProfiles }.toSet()
        if (semantic.size != 1 || direct != semantic + adapters) {
            unclassified("$label.meta.profile", "An active Observation claims exactly one admitted semantic profile.")
        }
        validateQuantityValueDomain(resource, index, semantic.single())
    }

    private fun validateQuantityValueDomain(observation: JsonToken.Object, index: Int, semanticProfile: String) {
        val domain = ExchangeContract.quantityValueDomainsByProfile[semanticProfile] ?: return
        val raw = observation.obj("valueQuantity")?.get("value") ?: return
        if (raw is JsonToken.Literal && raw.value == "null") return
        val rule = ExchangeGraphRule.MOBILE_OUTPUT_QUANTITY_VALUE_DOMAIN
        val location = "Bundle.entry[$index].resource.valueQuantity.value"
        val value = (raw as? JsonToken.Number)?.lexeme?.let(::BigDecimal) ?: fail(rule, location, "The value is a number.")
        domain.violation(value)?.let { fail(rule, location, "The value $it.") }
    }

    private fun validateAdapterOnlyOutputClaim(resource: JsonToken.Object, label: String) {
        val type = resource.resourceType ?: return
        val expected = ExchangeContract.adapterOnlyOutputProfiles[type] ?: return
        if (resource.profiles() != listOf(expected)) {
            fail(
                ExchangeGraphRule.MOBILE_OUTPUT_ADAPTER_ONLY_PROFILE,
                "$type.meta.profile",
                "$label claims exactly its adapter-only profile.",
            )
        }
    }

    private fun admittedDocumentClaim(resource: JsonToken.Object): Set<GroveIdentifierRole>? {
        if (resource.resourceType != "DocumentReference") return null
        val profiles = resource.profiles()?.takeIf { it.size == it.toSet().size } ?: return null
        return ExchangeContract.activeDocumentProfileClaims[profiles.toSet()]
    }

    private fun validateDocumentReferenceClaim(resource: JsonToken.Object, label: String) {
        if (resource.resourceType != "DocumentReference") return
        val required = admittedDocumentClaim(resource)
            ?: fail(
                ExchangeGraphRule.MOBILE_OUTPUT_DOCUMENT_PROFILE,
                "DocumentReference.meta.profile",
                "An active DocumentReference claims one admitted document profile mode.",
            )
        val typed = typedIdentifiers(resource, label).keys
        val rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT
        if (!typed.containsAll(required) || (typed - required - GroveIdentifierRole.WRITER_RECORD).isNotEmpty()) {
            fail(rule, "DocumentReference.identifier", "An active DocumentReference carries exactly its claim's identifier roles.")
        }
        if (resource.items("content")?.size != 1) {
            fail(rule, "DocumentReference.content", "A recording document carries exactly one attachment payload.")
        }
    }

    private fun validateActiveProvenanceClaim(resource: JsonToken.Object, label: String) {
        if (resource.resourceType != "Provenance") return
        val profiles = resource.profiles()
        if (profiles?.size != 1 || profiles.single() !in ExchangeContract.activeConversionProvenanceProfiles) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_PROFILE,
                "Provenance.meta.profile",
                "$label claims exactly one admitted conversion Provenance profile.",
            )
        }
    }

    private fun validateRetractionProvenanceClaim(resource: JsonToken.Object, label: String) {
        if (resource.resourceType != "Provenance") return
        if (resource.profiles() != listOf(ExchangeContract.MOBILE_RETRACTION_PROVENANCE_PROFILE)) {
            unclassified("$label.meta.profile", "A retraction Provenance claims exactly the retraction Provenance profile.")
        }
    }

    private fun validateFixedQuantitySemantics(resource: JsonToken.Object, index: Int) {
        if (resource.resourceType != "Observation") return
        val expected = resource.profiles()?.mapNotNull { ExchangeContract.quantitySemanticsByProfile[it] }?.singleOrNull()
        val quantity = resource.obj("valueQuantity")
        if (expected == null || quantity == null) return
        if (quantity.text("system") != expected.system || quantity.text("code") != expected.code) {
            fail(
                ExchangeGraphRule.MOBILE_OUTPUT_FIXED_QUANTITY_UNIT,
                "Bundle.entry[$index].resource.valueQuantity.code",
                "The Quantity uses the catalog-fixed system and code.",
            )
        }
    }

    private fun validateSupportingProfileClaim(resource: JsonToken.Object, label: String) {
        val type = resource.resourceType
        if (type != "Device" && type != "QuestionnaireResponse") return
        val profiles = resource.profiles()?.takeIf { it.size == 1 }
        if (type == "QuestionnaireResponse") {
            if (profiles != listOf(ExchangeContract.ACTIVE_QUESTIONNAIRE_RESPONSE_PROFILE)) {
                fail(
                    ExchangeGraphRule.MOBILE_SUPPORT_QUESTIONNAIRE_RESPONSE_PROFILE,
                    "QuestionnaireResponse.meta.profile",
                    "An active QuestionnaireResponse claims exactly its Grove profile.",
                )
            }
            return
        }
        val profile = profiles?.single()
        val requiredRoles = profile?.let(ExchangeContract.activeDeviceProfileClaims::get)
            ?: fail(
                ExchangeGraphRule.MOBILE_SUPPORT_DEVICE_PROFILE,
                "Device.meta.profile",
                "An active Device claims exactly one admitted Device profile.",
            )
        if (requiredRoles.isEmpty() || typedIdentifiers(resource, label).keys == requiredRoles) return
        when (profile) {
            ExchangeContract.MOBILE_HOST_DEVICE_PROFILE -> fail(
                ExchangeGraphRule.MOBILE_DEVICE_HOST_DEVICE_IDENTITY,
                "Device.identifier",
                "A host Device carries exactly one device-snapshot identity.",
            )
            ExchangeContract.MOBILE_RECORDING_DEVICE_PROFILE -> fail(
                ExchangeGraphRule.MOBILE_DEVICE_RECORDING_DEVICE_DUAL_IDENTITY,
                "Device.identifier",
                "A recording Device carries its recording-device and device-snapshot identities.",
            )
            else -> unclassified("$label.identifier", "An active Device carries exactly its profile's identifier roles.")
        }
    }

    // --- references ----------------------------------------------------------------------------

    private fun validateLogicalRetractionTargets(graph: GraphIndex) {
        graph.provenances().forEach { provenance ->
            provenance.items("target").orEmpty().forEachIndexed { index, target ->
                if (target.asObject()?.contains("reference") == true) {
                    fail(
                        ExchangeGraphRule.MOBILE_RETRACTION_LOGICAL_TARGET,
                        "Provenance.target[$index]",
                        "A retraction target is a typed logical Reference.",
                    )
                }
            }
        }
    }

    private fun validateReferences(graph: GraphIndex, index: Int, resource: JsonToken.Object) {
        val type = resource.resourceType
        resource.literalReferences().forEach { (path, reference) ->
            if (reference.text("reference") !in graph.byFullUrl) {
                fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_RESOLVED_REFERENCE,
                    "Bundle.entry[$index].resource.$path.reference",
                    "Every literal reference resolves to an entry fullUrl.",
                )
            }
            resolveTarget(graph, reference, "$type.$path")
        }
        val label = "Bundle.entry[$index].resource"
        ExchangeContract.governedReferencePaths.filter { it.resourceType == type }.forEach { rule ->
            val pathLabel = "$type.${rule.path}"
            referencesAt(resource, rule, pathLabel).forEach { validateGovernedReference(graph, it, rule.targetTypes, pathLabel) }
        }
        resource.allExtensions().forEachIndexed { extensionIndex, extension ->
            val targets = extension.text("url")?.let(ExchangeContract.governedExtensionReferenceTargets::get)
                ?: return@forEachIndexed
            val reference = extension.obj("valueReference")
            if (extension.members.keys.filter { it.startsWith("value") } != listOf("valueReference") || reference == null) {
                fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_SHAPE,
                    "$label.extension[$extensionIndex]",
                    "A governed extension carries exactly one valueReference.",
                )
            }
            validateGovernedReference(graph, reference, targets, "$label.extension[$extensionIndex].valueReference")
        }
    }

    private fun referencesAt(resource: JsonToken.Object, rule: GovernedReferencePath, label: String): List<JsonToken.Object> {
        val value = resource[rule.path] ?: return emptyList()
        if (rule.repeating) {
            return value.asItems()?.map { it.asObject() ?: fail(REFERENCE_SHAPE, label, "$label holds only Reference objects.") }
                ?: fail(REFERENCE_SHAPE, label, "$label is an array of Reference objects.")
        }
        return listOf(value.asObject() ?: fail(REFERENCE_SHAPE, label, "$label is one Reference object."))
    }

    private fun resolveTarget(graph: GraphIndex, reference: JsonToken.Object, label: String): JsonToken.Object? {
        val literal = reference.text("reference") ?: return null
        val target = graph.byFullUrl[literal]
            ?: unclassified(label, "The literal reference does not resolve inside its exchange graph.")
        if ("type" in reference && reference.text("type") != target.resourceType) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_DECLARED_TYPE,
                "$label.type",
                "A declared Reference.type equals the referenced resource type.",
            )
        }
        return target
    }

    private fun validateGovernedReference(
        graph: GraphIndex,
        reference: JsonToken.Object,
        allowed: Set<String>,
        label: String,
    ) {
        if ("reference" in reference) {
            validateLiteralGovernedReference(graph, reference, allowed, label)
        } else {
            validateLogicalGovernedReference(reference, allowed, label)
        }
    }

    private fun validateLiteralGovernedReference(
        graph: GraphIndex,
        reference: JsonToken.Object,
        allowed: Set<String>,
        label: String,
    ) {
        if (reference["reference"] !is JsonToken.Text) fail(REFERENCE_SHAPE, "$label.reference", "A literal reference is a string.")
        if ("identifier" in reference) fail(REFERENCE_SHAPE, label, "A governed reference is either literal or logical, never both.")
        val target = resolveTarget(graph, reference, label)
        if (target == null || target.resourceType in allowed) return
        if (allowed == PATIENT_ONLY) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_TARGET_TYPE,
                "$label.reference",
                "The reference must target a Patient.",
            )
        }
        unclassified("$label.reference", "The reference targets a resource type the path does not admit.")
    }

    private fun validateLogicalGovernedReference(reference: JsonToken.Object, allowed: Set<String>, label: String) {
        val identifier = reference.obj("identifier")
            ?: fail(REFERENCE_SHAPE, "$label.identifier", "A logical reference contains one Identifier.")
        val pair = completeIdentifier(identifier, "$label.identifier")
        val declared = reference.text("type")
        if (declared == null || declared !in allowed) {
            if (allowed == PATIENT_ONLY) fail(LOGICAL_PATIENT, label, "A logical Patient reference declares type Patient.")
            unclassified(label, "The logical reference type is not admitted at this path.")
        }
        if (declared != "Patient") return
        if (pair.system.value in ExchangeContract.reservedPatientIdentifierSystems) {
            fail(LOGICAL_PATIENT, "$label.identifier.system", "A logical Patient pseudonym never uses a protocol-reserved system.")
        }
        val identifierType = identifier["type"] ?: return
        val codings = identifierType.asObject()?.let { type -> type["coding"]?.asItems() ?: emptyList() }
            ?: fail(LOGICAL_PATIENT, "$label.identifier.type", "A logical Patient Identifier.type is a CodeableConcept.")
        val codingObjects = codings.map {
            it.asObject() ?: fail(LOGICAL_PATIENT, "$label.identifier.type", "A logical Patient Identifier.type has valid codings.")
        }
        if (codingObjects.any { it.text("system") == ExchangeContract.GROVE_IDENTIFIER_ROLE }) {
            fail(LOGICAL_PATIENT, "$label.identifier.type", "A logical Patient pseudonym never claims a Grove identifier role.")
        }
    }

    private fun validateConnectivity(graph: GraphIndex) {
        val adjacency = graph.byFullUrl.keys.associateWith { linkedSetOf<String>() }
        graph.byFullUrl.forEach { (sourceUrl, resource) ->
            resource.literalReferences().forEach { (_, reference) ->
                val targetUrl = reference.text("reference")?.takeIf(graph.byFullUrl::containsKey) ?: return@forEach
                adjacency.getValue(sourceUrl).add(targetUrl)
                adjacency.getValue(targetUrl).add(sourceUrl)
            }
        }
        val reachable = graph.byFullUrl.filterValues {
            it.resourceType in ExchangeContract.activeOutputResourceTypes ||
                it.resourceType == ExchangeContract.ACTIVE_LIFECYCLE_RESOURCE_TYPE
        }.keys.toMutableSet()
        val pending = ArrayDeque(reachable)
        while (pending.isNotEmpty()) {
            adjacency.getValue(pending.removeFirst()).forEach { if (reachable.add(it)) pending.addLast(it) }
        }
        val disconnected = graph.byFullUrl.any { (url, resource) ->
            resource.resourceType in ExchangeContract.activeSupportingResourceTypes && url !in reachable
        }
        if (disconnected) {
            fail(
                ExchangeGraphRule.MOBILE_SUPPORT_CONNECTED,
                "Bundle.entry",
                "Every supporting resource connects to an output or the lifecycle Provenance.",
            )
        }
    }

    // --- lifecycle -----------------------------------------------------------------------------

    private data class Lifecycle(val transform: List<JsonToken.Object>, val retraction: List<JsonToken.Object>, val total: Int)

    private fun classifyLifecycle(graph: GraphIndex): Lifecycle {
        val transform = mutableListOf<JsonToken.Object>()
        val retraction = mutableListOf<JsonToken.Object>()
        val provenances = graph.provenances()
        provenances.forEach { provenance ->
            val codings = provenance.obj("activity")?.items("coding").orEmpty().mapNotNull { it.asObject() }
            val iso = codings.filter { it.text("system") == ExchangeContract.RECORD_LIFECYCLE }
            val grove = codings.filter { it.text("system") == ExchangeContract.GROVE_LIFECYCLE_EVENT }
            if (iso.isEmpty() && grove.isEmpty()) return@forEach
            if (iso.size + grove.size != 1) {
                fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_LIFECYCLE_CODING,
                    "Provenance.activity.coding",
                    "A lifecycle Provenance carries exactly one lifecycle coding.",
                )
            }
            if (iso.isNotEmpty()) {
                if (iso.single().text("code") != ExchangeContract.ACTIVE_LIFECYCLE_ACTIVITY_CODE) {
                    unclassified("Provenance.activity.coding", "An unadmitted ISO lifecycle code.")
                }
                transform.add(provenance)
            } else {
                if (grove.single().text("code") != ExchangeContract.RETRACTION_ACTIVITY_CODE) {
                    unclassified("Provenance.activity.coding", "An unadmitted Grove lifecycle code.")
                }
                retraction.add(provenance)
            }
        }
        return Lifecycle(transform, retraction, provenances.size)
    }

    private fun validateActiveEvent(graph: GraphIndex, lifecycle: Lifecycle) {
        if (lifecycle.total != 1 || lifecycle.transform.size != 1 || lifecycle.retraction.isNotEmpty()) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_TRANSFORM_PROVENANCE,
                "Bundle.entry",
                "An active event contains exactly one transform Provenance and no retraction.",
            )
        }
        val outputUrls = mutableSetOf<String>()
        val sources = mutableSetOf<BusinessIdentifier>()
        graph.byFullUrl.forEach { (url, resource) ->
            if (resource.resourceType !in ExchangeContract.activeOutputResourceTypes) return@forEach
            val typed = typedIdentifiers(resource, "$url output")
            val source = typed[GroveIdentifierRole.SOURCE_RECORD]
            if (source == null || GroveIdentifierRole.SOURCE_OUTPUT !in typed) {
                unclassified("Bundle.entry", "Every active output carries typed source-record and source-output identities.")
            }
            outputUrls.add(url)
            sources.add(source)
        }
        if (outputUrls.isEmpty()) {
            fail(ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_REQUIRED, "Bundle.entry", "An active event contains at least one output.")
        }
        if (sources.size != 1) unclassified("Bundle.entry", "An active event contains outputs for exactly one source record.")
        val provenance = lifecycle.transform.single()
        if (exactSourceEntity(provenance) !in sources) {
            unclassified("Provenance.entity", "The transform source equals the outputs' source-record identity.")
        }
        val targetUrls = provenance.items("target").orEmpty().mapNotNull { it.asObject()?.text("reference") }
        if (targetUrls.size != targetUrls.toSet().size || targetUrls.toSet() != outputUrls) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_TARGETS,
                "Provenance.target",
                "The transform Provenance targets every and only source-derived output once.",
            )
        }
        validateGovernedSourceIdentifiers(graph)
        validateAdapterProvenanceGraph(graph)
    }

    private fun validateGovernedSourceIdentifiers(graph: GraphIndex) {
        val policy = ExchangeContract.governedSourceIdentifierPolicy
        val outputs = graph.resources.filter { it.resourceType in ExchangeContract.activeOutputResourceTypes }
        val governed = mutableListOf<Pair<JsonToken.Object, BusinessIdentifier>>()
        outputs.forEach { resource ->
            val label = "${resource.resourceType} output"
            if (GroveIdentifierRole.SOURCE_RECORD !in typedIdentifiers(resource, label)) {
                unclassified("Bundle.entry", "An active output has a source-record identity for primary selection.")
            }
            val pairs = nonGroveIdentifiers(resource, label)
            if (pairs.size > policy.maxPerPrimaryOutput) {
                unclassified("Bundle.entry", "An active output carries at most one governed source identifier.")
            }
            pairs.forEach { governed.add(resource to it) }
        }
        if (governed.size > policy.maxPrimaryOutputsPerEvent) {
            unclassified("Bundle.entry", "A governed source identifier designates only one primary output.")
        }
        governed.forEach { (resource, pair) ->
            val profiles = resource.profiles().orEmpty()
            if (resource.resourceType in policy.neverResourceTypes || profiles.any { it in policy.neverProfiles }) {
                unclassified("Bundle.entry", "A governed source identifier never sits on a secondary or support output.")
            }
            if (resource.resourceType == "DocumentReference" && outputs.size != 1) {
                unclassified("Bundle.entry", "A companion source artifact never carries the governed source identifier.")
            }
            val occurrences = graph.resources.sumOf { it.identifierOccurrences(pair) }
            if (occurrences != 1) unclassified("Bundle.entry", "A governed source identifier appears exactly once.")
        }
    }

    private fun nonGroveIdentifiers(resource: JsonToken.Object, label: String): List<BusinessIdentifier> {
        typedIdentifiers(resource, label)
        val identifiers = resource["identifier"]?.let { it.asItems() ?: listOf(it) } ?: return emptyList()
        return identifiers.mapIndexedNotNull { index, token ->
            val identifier = token.asObject() ?: unclassified("$label.identifier[$index]", "Every identifier is an Identifier.")
            if (identifier.groveRoleCodings().isNotEmpty()) return@mapIndexedNotNull null
            validateGovernedIdentifierType(identifier, "$label.identifier[$index]")
            completeIdentifier(identifier, "$label.identifier[$index]")
        }
    }

    private fun validateGovernedIdentifierType(identifier: JsonToken.Object, label: String) {
        val type = identifier["type"] ?: return
        val typeObject = type.asObject() ?: unclassified("$label.type", "A governed identifier type is a CodeableConcept.")
        val codings = typeObject["coding"]?.let { it.asItems() ?: unclassified("$label.type.coding", "Codings are an array.") }
            .orEmpty()
        val text = typeObject.text("text")
        if (codings.isEmpty() && text.isNullOrBlank()) {
            unclassified("$label.type", "A governed identifier type carries text or a Coding.")
        }
        codings.forEachIndexed { index, token ->
            val codingLabel = "$label.type.coding[$index]"
            val coding = token.asObject() ?: unclassified(codingLabel, "Every coding is a Coding.")
            val system = coding.text("system")
            if (system == ExchangeContract.GROVE_IDENTIFIER_ROLE) {
                unclassified(codingLabel, "A governed identifier never claims a Grove role.")
            }
            if (system.isNullOrEmpty() || !ExchangeProtocol.isAbsoluteAsciiUri(system)) {
                unclassified("$codingLabel.system", "A governed identifier type system is an absolute URI.")
            }
            val code = coding.text("code")
            if (code == null || !fhirCode.matches(code)) {
                unclassified("$codingLabel.code", "A governed identifier type code is a FHIR code.")
            }
        }
    }

    private fun validateAdapterProvenanceGraph(graph: GraphIndex) {
        ExchangeContract.adapterConversionProvenanceClaims.forEach { claim ->
            val outputsBySource = linkedMapOf<BusinessIdentifier, MutableSet<String>>()
            val provenancesBySource = linkedMapOf<BusinessIdentifier, MutableList<JsonToken.Object>>()
            graph.byFullUrl.forEach { (url, resource) ->
                val profiles = resource.profiles().orEmpty().toSet()
                if (profiles.any { it in claim.targetAdapterProfiles }) {
                    outputsBySource.getOrPut(sourceOf(resource, "output")) { linkedSetOf() }.add(url)
                }
                if (claim.profile in profiles) {
                    provenancesBySource.getOrPut(adapterProvenanceSource(resource)) { mutableListOf() }.add(resource)
                }
            }
            outputsBySource.forEach { (source, outputUrls) ->
                val provenance = provenancesBySource[source]?.singleOrNull()
                    ?: fail(ADAPTER_GRAPH, "Bundle.entry", "A ${claim.adapter} source record has exactly one conversion Provenance.")
                validateAdapterProvenanceTargets(graph, claim, source, provenance, outputUrls)
            }
            if ((provenancesBySource.keys - outputsBySource.keys).isNotEmpty()) {
                fail(ADAPTER_GRAPH, "Provenance.entity", "An adapter Provenance has no output for its source record.")
            }
        }
    }

    private fun adapterProvenanceSource(provenance: JsonToken.Object): BusinessIdentifier {
        val entity = provenance.items("entity")?.firstOrNull()?.asObject()?.obj("what")?.get("identifier")
        val role = roleOf(entity, "Provenance source entity", "Provenance.entity[0].what.identifier")
        if (role != GroveIdentifierRole.SOURCE_RECORD.code) {
            unclassified("Provenance.entity", "The Provenance source entity carries the source-record role.")
        }
        val source = completeIdentifier(entity, "Provenance source entity")
        if (!ExchangeProtocol.opaqueIdentityValue.matches(source.value)) {
            unclassified("Provenance.entity", "The Provenance source entity is a canonical opaque identity.")
        }
        return source
    }

    private fun validateAdapterProvenanceTargets(
        graph: GraphIndex,
        claim: AdapterConversionProvenanceClaim,
        source: BusinessIdentifier,
        provenance: JsonToken.Object,
        outputUrls: Set<String>,
    ) {
        val targetUrls = provenance.items("target").orEmpty().mapNotNull { it.asObject()?.text("reference") }
        if (targetUrls.any { !it.startsWith(URN_UUID) }) {
            fail(ADAPTER_GRAPH, "Provenance.target", "Adapter targets are internal UUID references.")
        }
        if (targetUrls.size != targetUrls.toSet().size) {
            fail(ADAPTER_GRAPH, "Provenance.target", "An adapter Provenance repeats a target.")
        }
        targetUrls.forEach { targetUrl ->
            val target = graph.byFullUrl[targetUrl]
                ?: fail(ADAPTER_GRAPH, "Provenance.target", "An adapter Provenance target is unresolved.")
            if (target.profiles().orEmpty().none { it in claim.targetAdapterProfiles }) {
                fail(ADAPTER_GRAPH, "Provenance.target", "An adapter Provenance targets only its adapter's outputs.")
            }
            if (sourceOf(target, "target") != source) {
                fail(ADAPTER_GRAPH, "Provenance.target", "An adapter Provenance and its targets share one source-record identity.")
            }
        }
        if (targetUrls.toSet() != outputUrls) {
            fail(ADAPTER_GRAPH, "Provenance.target", "An adapter Provenance targets every adapter output of its source record.")
        }
    }

    private fun sourceOf(resource: JsonToken.Object, role: String): BusinessIdentifier =
        typedIdentifiers(resource, role)[GroveIdentifierRole.SOURCE_RECORD]
            ?: unclassified("Bundle.entry", "An adapter $role carries exactly one typed source-record identity.")

    private fun validateRetractionEvent(graph: GraphIndex, lifecycle: Lifecycle) {
        if (lifecycle.total != 1 || lifecycle.retraction.size != 1 || lifecycle.transform.isNotEmpty()) {
            fail(
                ExchangeGraphRule.MOBILE_RETRACTION_PROVENANCE,
                "Bundle.entry",
                "A retraction event contains exactly one retraction Provenance and no transform.",
            )
        }
        val provenance = lifecycle.retraction.single()
        val targets = provenance.items("target")
        if (targets.isNullOrEmpty()) {
            fail(ExchangeGraphRule.MOBILE_RETRACTION_TARGET_REQUIRED, "Provenance.target", "A retraction names at least one target.")
        }
        val seen = mutableSetOf<BusinessIdentifier>()
        targets.forEachIndexed { index, token ->
            val target = token.asObject()?.takeIf { "reference" !in it }
                ?: unclassified("Provenance.target[$index]", "A retraction target is a logical Reference without a literal reference.")
            val pair = validateRetractionTarget(target, "Provenance.target[$index]")
            if (!seen.add(pair)) {
                fail(
                    ExchangeGraphRule.MOBILE_RETRACTION_DISTINCT_TARGET,
                    "Provenance.target[$index].identifier",
                    "No two targets share one identifier pair.",
                )
            }
            validateRetractionTargetRole(target, "Provenance.target[$index]")
        }
        exactSourceEntity(provenance)
    }

    /** The target's identifier pair, after its type, role and opaque-identity checks. */
    private fun validateRetractionTarget(target: JsonToken.Object, label: String): BusinessIdentifier {
        if (target.text("type").isNullOrEmpty()) unclassified("$label.type", "A retraction target states its resource type.")
        val identifier = target["identifier"]
        val role = roleOf(identifier, "$label.identifier", "$label.identifier")
        if (role !in opaqueRoles) {
            unclassified("$label.identifier.type", "A retraction target identifier role is an opaque identity role.")
        }
        val pair = completeIdentifier(identifier, "$label.identifier")
        if (!ExchangeProtocol.opaqueIdentityValue.matches(pair.value)) {
            fail(
                ExchangeGraphRule.MOBILE_RETRACTION_OPAQUE_TARGET,
                "$label.identifier.value",
                "A retraction target is a canonical opaque identity.",
            )
        }
        return pair
    }

    private fun validateRetractionTargetRole(target: JsonToken.Object, label: String) {
        val roleExtensions = target.topLevelExtensions(ExchangeContract.RETRACTION_TARGET_ROLE_EXTENSION)
        val targetRole = roleExtensions.singleOrNull()?.text("valueCode")?.let(RetractionTargetRole::of)
            ?: fail(
                ExchangeGraphRule.MOBILE_RETRACTION_TARGET_ROLE,
                "$label.extension",
                "Every target carries exactly one admitted target role.",
            )
        if (requireNotNull(target["identifier"].asObject()).groveRoleCodes().single() != targetRole.identifierRole.code) {
            unclassified("$label.identifier.type", "The target role requires the ${targetRole.identifierRole.code} identifier role.")
        }
        if (target.text("type") !in targetRole.resourceTypes) {
            fail(
                ExchangeGraphRule.MOBILE_RETRACTION_ROLE_TARGET_TYPE,
                "$label.type",
                "The target role does not admit this resource type.",
            )
        }
        validateNativeRecordIdentifier(target, label)
    }

    private fun validateNativeRecordIdentifier(target: JsonToken.Object, label: String) {
        val carried = target.topLevelExtensions(ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION)
        if (carried.isEmpty()) return
        val rule = ExchangeGraphRule.MOBILE_RETRACTION_NATIVE_RECORD_IDENTIFIER
        val location = "$label.extension"
        val extension = carried.singleOrNull() ?: fail(rule, location, "A target carries at most one native record identifier.")
        val identifier = extension.obj("valueIdentifier")?.takeIf { extension.members.keys == setOf("url", "valueIdentifier") }
            ?: fail(rule, location, "A native record identifier is one valueIdentifier.")
        val system = identifier.text("system")
        if (system == null || !ExchangeProtocol.isAbsoluteAsciiUri(system)) {
            fail(rule, "$location.valueIdentifier.system", "A native record identifier system is an absolute URI.")
        }
        if (identifier.text("value").isNullOrEmpty()) {
            fail(rule, "$location.valueIdentifier.value", "A native record identifier carries the exact native value.")
        }
        if (identifier.groveRoleCodings().isNotEmpty()) {
            fail(rule, "$location.valueIdentifier.type", "A native record identifier never carries a Grove identifier-role coding.")
        }
    }

    private fun exactSourceEntity(provenance: JsonToken.Object): BusinessIdentifier {
        val entity = provenance.items("entity")?.singleOrNull()
            ?: fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_SINGLE_SOURCE_ENTITY,
                "Provenance.entity",
                "A lifecycle Provenance identifies exactly one source record.",
            )
        val entityObject = entity.asObject()
        val what = entityObject?.obj("what")
        val logical = what != null && "reference" !in what && "resource" !in what
        if (entityObject?.text("role") != "source" || !logical) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_SOURCE_ENTITY,
                "Provenance.entity[0].what",
                "The source is one logical Identifier entity with role source.",
            )
        }
        val identifier = requireNotNull(what)["identifier"]
        val location = "Provenance.entity[0].what.identifier"
        if (roleOf(identifier, location, location) != GroveIdentifierRole.SOURCE_RECORD.code) {
            unclassified("$location.type", "The source entity carries the source-record role.")
        }
        val pair = completeIdentifier(identifier, location)
        if (!ExchangeProtocol.opaqueIdentityValue.matches(pair.value)) {
            unclassified("$location.value", "The source identifier is a canonical opaque identity.")
        }
        return pair
    }

    // --- client rules ----------------------------------------------------------------------------

    private fun validateClientRules(
        graph: GraphIndex,
        bundle: JsonToken.Object,
        entries: List<JsonToken>,
        lifecycle: Lifecycle,
    ) {
        entries.forEachIndexed { index, entry ->
            val entryObject = requireNotNull(entry.asObject())
            if ("request" in entryObject || "response" in entryObject) {
                fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_COLLECTION_ENTRY_OPERATION,
                    "Bundle.entry[$index]",
                    "An event entry carries neither a request nor a response.",
                )
            }
        }
        val times = ExchangeGraphRule.MOBILE_EXCHANGE_EVENT_TIMES
        if (bundle.text("timestamp").isNullOrEmpty()) fail(times, "Bundle.timestamp", "An event states its Bundle timestamp.")
        (lifecycle.transform + lifecycle.retraction).forEach { provenance ->
            if ("occurredDateTime" !in provenance && "occurredPeriod" !in provenance) {
                fail(times, "Provenance.occurred[x]", "A lifecycle Provenance states occurred[x].")
            }
            if (provenance.text("recorded").isNullOrEmpty()) fail(times, "Provenance.recorded", "A lifecycle Provenance states recorded.")
        }
        if (graph.kind == ExchangeGraphKind.ACTIVE) {
            validateAssemblerAgent(graph, lifecycle.transform.single())
            validateStudyContext(graph)
        }
    }

    /** One ResearchStudy, its exact-revision PlanDefinition and one ResearchSubject per bundled enrollment. */
    private fun validateStudyContext(graph: GraphIndex) {
        val rule = ExchangeGraphRule.MOBILE_SUPPORT_STUDY_CONTEXT
        STUDY_CONTEXT_ROLES.forEach { (type, role) ->
            graph.ofType(type).keys.forEach { url ->
                if (graph.nodeRoles[url] != role.code) {
                    fail(rule, "$type entry", "A study-context entry is keyed as an entry node with its study-context role.")
                }
            }
        }
        val studies = graph.ofType("ResearchStudy")
        val plans = graph.ofType("PlanDefinition")
        val subjects = graph.ofType("ResearchSubject")
        if (plans.size != studies.size || subjects.size != studies.size) {
            fail(rule, "Bundle.entry", "A bundled study carries one PlanDefinition and one ResearchSubject.")
        }
        plans.values.forEach { plan ->
            if (plan.text("url").isNullOrBlank() || plan.text("version").isNullOrBlank()) {
                fail(rule, "PlanDefinition", "A bundled PlanDefinition states its canonical url and version.")
            }
        }
        val protocols = studies.values.map { study -> study.items("protocol").orEmpty().mapNotNull { it.asObject()?.text("reference") } }
        if (protocols.any { it.size != 1 || it.single() !in plans } || protocols.flatten().toSet().size != studies.size) {
            fail(rule, "ResearchStudy.protocol", "A bundled study references its own exact-revision PlanDefinition.")
        }
        validateResearchSubjects(graph, subjects.values, studies.keys)
    }

    private fun validateResearchSubjects(graph: GraphIndex, subjects: Collection<JsonToken.Object>, studyUrls: Set<String>) {
        val rule = ExchangeGraphRule.MOBILE_SUPPORT_STUDY_CONTEXT
        val subjectStudies = subjects.map { it.obj("study")?.text("reference") }
        if (subjectStudies.any { it !in studyUrls } || subjectStudies.toSet().size != studyUrls.size) {
            fail(rule, "ResearchSubject.study", "A bundled ResearchSubject links its own study.")
        }
        val individual = graph.byFullUrl.values.first { it.resourceType in ExchangeContract.activeOutputResourceTypes }
            .let { output -> output["subject"] ?: output["patient"] }
        if (subjects.any { it["individual"] != individual }) {
            fail(rule, "ResearchSubject.individual", "A bundled ResearchSubject links the subject of the outputs.")
        }
    }

    private fun validateAssemblerAgent(graph: GraphIndex, provenance: JsonToken.Object) {
        val rule = ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_ASSEMBLER
        val agents = provenance.items("agent").orEmpty().mapNotNull { it.asObject() }.filter { agent ->
            agent["type"]?.codingPairs().orEmpty().contains(ExchangeContract.PROVENANCE_PARTICIPANT to ASSEMBLER)
        }
        val who = agents.singleOrNull()?.obj("who")
            ?: fail(rule, "Provenance.agent", "A conversion Provenance names exactly one assembler agent.")
        val target = who.text("reference")?.let(graph.byFullUrl::get)
        if (target?.resourceType != "Device" || target.profiles() != listOf(ExchangeContract.MOBILE_APPLICATION_DEVICE_PROFILE)) {
            fail(rule, "Provenance.agent[0].who", "The assembler is the event's application Device snapshot.")
        }
    }

    // --- identities ----------------------------------------------------------------------------

    private fun validateIdentitySystemRoles(bundle: JsonToken.Object) {
        val rolesBySystem = mutableMapOf<String, MutableSet<String>>()
        bundle.walkObjects("Bundle") { _, node ->
            val roles = node.groveRoleCodes()
            val system = node.text("system")
            if (roles.isNotEmpty() && system != null) rolesBySystem.getOrPut(system) { mutableSetOf() }.addAll(roles)
        }
        if (rolesBySystem.values.any { it.size > 1 }) {
            fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_IDENTITY_SYSTEM_ROLE,
                "Bundle",
                "One identifier system keeps one role meaning within an event.",
            )
        }
    }

    private fun completeIdentifier(identifier: JsonToken?, label: String): BusinessIdentifier =
        identifier.identifierPair() ?: unclassified(label, "A complete Identifier.system and Identifier.value are required.")

    private fun roleOf(identifier: JsonToken?, label: String, location: String): String {
        completeIdentifier(identifier, label)
        return requireNotNull(identifier.asObject()).groveRoleCodes().singleOrNull()
            ?: fail(
                ExchangeGraphRule.MOBILE_EXCHANGE_IDENTIFIER_ROLE,
                location,
                "$label carries exactly one Grove identifier-role Coding.",
            )
    }

    /** The resource's unique typed Grove business identifiers by role; entry-node and event roles never appear here. */
    private fun typedIdentifiers(
        resource: JsonToken.Object,
        label: String,
    ): Map<GroveIdentifierRole, BusinessIdentifier> {
        val member = resource["identifier"] ?: return emptyMap()
        if (member is JsonToken.Literal && member.value == "null") return emptyMap()
        val singular = member is JsonToken.Object
        val identifiers = if (singular) {
            listOf(member)
        } else {
            member.asItems() ?: unclassified("$label.identifier", "Identifiers are an array.")
        }
        val type = resource.resourceType ?: "Resource"
        val result = linkedMapOf<GroveIdentifierRole, BusinessIdentifier>()
        identifiers.forEachIndexed { index, token ->
            val identifier = token.asObject() ?: unclassified("$label.identifier[$index]", "Every identifier is an Identifier.")
            val location = if (singular) "$type.identifier" else "$type.identifier[$index]"
            val codings = identifier.groveRoleCodings()
            if (codings.isEmpty()) return@forEachIndexed
            val role = codings.singleOrNull()?.text("code")?.takeIf { it in opaqueRoles }?.let(GroveIdentifierRole::of)
                ?: fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_IDENTIFIER_ROLE,
                    location,
                    "The identifier has an unknown or repeated Grove identifier role.",
                )
            if (role in result) {
                fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_DISTINCT_RESOURCE_IDENTITY_ROLE,
                    location,
                    "The resource repeats the ${role.code} identifier role.",
                )
            }
            result[role] = identifier.identifierPair()?.takeIf { ExchangeProtocol.opaqueIdentityValue.matches(it.value) }
                ?: fail(
                    ExchangeGraphRule.MOBILE_EXCHANGE_OPAQUE_RESOURCE_IDENTITY,
                    location,
                    "The identifier is not a canonical opaque identity.",
                )
        }
        return result
    }

    private fun selectedEntryIdentifier(resource: JsonToken.Object, label: String): RoledIdentifier? {
        val byRole = typedIdentifiers(resource, label)
        val role = ExchangeContract.entryIdentifierPriority.firstOrNull { it in byRole } ?: return null
        return RoledIdentifier(byRole.getValue(role), role)
    }

    private fun JsonToken.Object.topLevelExtensions(url: String): List<JsonToken.Object> =
        items("extension").orEmpty().mapNotNull { it.asObject() }.filter { it.text("url") == url }

    private fun strictBase64(text: String): ByteArray? = try {
        text.takeIf { it.length % BASE64_QUANTUM == 0 }?.let { Base64.getDecoder().decode(it) }
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun fail(rule: ExchangeGraphRule, location: String, detail: String): Nothing =
        throw ExchangeGraphException(ExchangeGraphError.RuleViolation(rule, location, detail))

    private fun unclassified(location: String, detail: String): Nothing =
        fail(ExchangeGraphRule.MOBILE_EXCHANGE_UNCLASSIFIED, location, detail)

    /** Entry bookkeeping for one validation run. */
    private class GraphIndex(val kind: ExchangeGraphKind, val eventIdentifier: BusinessIdentifier) {
        val byFullUrl = linkedMapOf<String, JsonToken.Object>()
        val resources: List<JsonToken.Object>
            get() = byFullUrl.values.toList()
        val activeTypes: Set<String> =
            ExchangeContract.activeOutputResourceTypes + ExchangeContract.activeSupportingResourceTypes +
                ExchangeContract.ACTIVE_LIFECYCLE_RESOURCE_TYPE
        private val ordinals = mutableMapOf<String, Long>()

        /** The node role of every entry keyed by an entry-node key, by full URL. */
        val nodeRoles = mutableMapOf<String, String>()

        fun add(fullUrl: String, resource: JsonToken.Object): Boolean = byFullUrl.put(fullUrl, resource) == null

        fun ofType(resourceType: String): Map<String, JsonToken.Object> = byFullUrl.filterValues { it.resourceType == resourceType }

        fun nextOrdinal(nodeRole: String): Long {
            val ordinal = ordinals.getOrDefault(nodeRole, 0L)
            ordinals[nodeRole] = ordinal + 1
            return ordinal
        }

        fun provenances(): List<JsonToken.Object> = byFullUrl.values.filter { it.resourceType == "Provenance" }
    }

    private val REFERENCE_SHAPE = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_SHAPE
    private val LOGICAL_PATIENT = ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_PATIENT_REFERENCE
    private val ADAPTER_GRAPH = ExchangeGraphRule.MOBILE_EXCHANGE_ADAPTER_PROVENANCE_GRAPH
    private val PATIENT_ONLY = setOf("Patient")
    private val STUDY_CONTEXT_ROLES = mapOf(
        "Patient" to StudyContextEntryNodeRole.PATIENT,
        "ResearchStudy" to StudyContextEntryNodeRole.RESEARCH_STUDY,
        "ResearchSubject" to StudyContextEntryNodeRole.RESEARCH_SUBJECT,
        "PlanDefinition" to StudyContextEntryNodeRole.PLAN_DEFINITION,
    )
    private val RETRACTION_TYPES = setOf(ExchangeContract.ACTIVE_LIFECYCLE_RESOURCE_TYPE, "Device")
    private val ADAPTER_PROFILE_PREFIXES = listOf(
        "${ExchangeContract.CANONICAL_ROOT}/healthkit/StructureDefinition/",
        "${ExchangeContract.CANONICAL_ROOT}/health-connect/StructureDefinition/",
    )
    private const val HEALTH_CONNECT_ADAPTER = "health-connect"
    private const val ENTERER = "enterer"
    private const val ASSEMBLER = "assembler"
    private const val URN_UUID = "urn:uuid:"
    private const val SHA1_BYTES = 20
    private const val BASE64_QUANTUM = 4
}
