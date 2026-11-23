//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.ExchangeContract
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RoledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roleCodings
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The identity rules of a graph: every Grove-typed identifier is well formed, one system has one
 * role, every entry carries one complete key that selects the right identifier, no two entries share
 * a key, each fullUrl is its key's UUID, and every literal reference resolves to an entry.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:TooManyFunctions")
internal class IdentityRules(
    private val document: GraphDocument,
    private val kind: ExchangeRecord.Kind,
) {

    fun violation(): ExchangeGraphError? = resourceIdentifierViolation()
        ?: systemRoleViolation()
        ?: entryKeyViolation()
        ?: literalReferenceViolation()

    private fun resourceIdentifierViolation(): ExchangeGraphError? {
        document.entries.indices.forEach { entryIndex ->
            val resource = document.resource(index = entryIndex) ?: return@forEach
            val roles = mutableSetOf<GroveIdentifierRole>()
            resource.array(name = "identifier").orEmpty().forEachIndexed { index, value ->
                val identifier = value as? JsonObject ?: return@forEachIndexed
                val codings = identifier.roleCodings()
                if (codings.isEmpty()) return@forEachIndexed
                val location = "${resource.string(name = "resourceType")}.identifier[$index]"
                val roleCodes = codings.map { it.string(name = "code") }
                val role = roleCodes.singleOrNull()?.let { GroveIdentifierRole.fromCode(code = it) }
                    ?: return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_IDENTIFIER_ROLE, location = location)
                val roled = identifier.roledIdentifier()
                val isResourceRole = role != GroveIdentifierRole.RECORD_VERSION && role != GroveIdentifierRole.RECORD_RETRACTION
                val isOpaque = roled != null && isResourceRole &&
                    OpaqueIdentityScope.isCanonicalOpaqueValue(value = roled.identifier.value)
                if (!isOpaque) return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_OPAQUE_RESOURCE_IDENTITY, location = location)
                if (!roles.add(role)) {
                    return violation(
                        rule = ExchangeGraphRule.MOBILE_EXCHANGE_DISTINCT_RESOURCE_IDENTITY_ROLE,
                        location = location,
                    )
                }
            }
        }
        return null
    }

    /**
     * One Grove identifier system has one role throughout the Bundle, nested references included.
     */
    private fun systemRoleViolation(): ExchangeGraphError? {
        val roleBySystem = mutableMapOf<String, String>()
        document.objects(value = document.bundle).forEach { identifier ->
            val codings = identifier.roleCodings()
            if (codings.isEmpty()) return@forEach
            val code = codings.singleOrNull()?.string(name = "code")
            if (code == null || GroveIdentifierRole.fromCode(code = code) == null || identifier.string(name = "system") == null) {
                return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_IDENTIFIER_ROLE)
            }
            val first = roleBySystem.putIfAbsent(identifier.string(name = "system").orEmpty(), code)
            if (first != null && first != code) return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_IDENTITY_SYSTEM_ROLE)
        }
        return null
    }

    private fun entryKeyViolation(): ExchangeGraphError? {
        val keys = mutableSetOf<BusinessIdentifier>()
        val fullUrls = mutableSetOf<String>()
        document.entries.indices.forEach { index ->
            val key = document.entryKey(index = index)?.takeIf { it.role in ALLOWED_KEY_ROLES }
                ?: return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY)
            keyViolation(key = key, index = index)?.let { return it }
            if (!keys.add(key.identifier)) {
                return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_DISTINCT_ENTRY_KEY, location = keyLocation(index = index))
            }
            val fullUrl = document.fullUrl(index = index)
            if (fullUrl == null || !fullUrls.add(fullUrl) || fullUrl != key.identifier.fullUrl) {
                return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_DETERMINISTIC_FULL_URL)
            }
        }
        return null
    }

    private fun keyViolation(key: RoledIdentifier, index: Int): ExchangeGraphError? = when (document.type(index = index)) {
        ResourceType.PROVENANCE -> provenanceKeyViolation(key = key, index = index)
        ResourceType.QUESTIONNAIRE_RESPONSE -> questionnaireResponseKeyViolation(key = key, index = index)
        else -> typedKeyViolation(key = key, index = index)
    }

    /**
     * A Provenance is keyed by its record version or retraction key, which a conversion Provenance repeats in
     * its one `grove-record-version` and a retraction Provenance never states.
     */
    private fun provenanceKeyViolation(key: RoledIdentifier, index: Int): ExchangeGraphError? {
        val isActive = kind == ExchangeRecord.Kind.ACTIVE
        val expected = if (isActive) GroveIdentifierRole.RECORD_VERSION else GroveIdentifierRole.RECORD_RETRACTION
        val versions = document.extensionIdentifiers(index = index, url = ExchangeContract.RECORD_VERSION_EXTENSION)
        return when {
            key.role != expected -> violation(
                rule = ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY_SELECTION,
                location = keyLocation(index = index),
            )
            !OpaqueIdentityScope.isCanonicalOpaqueValue(value = key.identifier.value) -> violation(
                rule = if (isActive) RECORD_VERSION_KEY else RETRACTION_KEY,
                location = "${keyLocation(index = index)}.value",
            )
            if (isActive) versions != listOf(key) else versions.isNotEmpty() ->
                violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_RECORD_VERSION_EXTENSION,
                    location = "Bundle.entry[$index].resource.extension",
                )
            else -> null
        }
    }

    private fun questionnaireResponseKeyViolation(key: RoledIdentifier, index: Int): ExchangeGraphError? {
        val snapshots = document.extensionIdentifiers(index = index, url = ExchangeContract.CONTEXT_SNAPSHOT_EXTENSION)
        return when {
            key.role != GroveIdentifierRole.CONTEXT_SNAPSHOT ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY_SELECTION, location = keyLocation(index = index))
            !OpaqueIdentityScope.isCanonicalOpaqueValue(value = key.identifier.value) ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTENT_SNAPSHOT_KEY, location = "${keyLocation(index = index)}.value")
            snapshots != listOf(key) ->
                violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTEXT_SNAPSHOT_EXTENSION,
                    location = "Bundle.entry[$index].resource.extension",
                )
            else -> null
        }
    }

    /**
     * Every other resource is keyed by its highest-priority typed identifier, a shared node by its content snapshot.
     */
    private fun typedKeyViolation(key: RoledIdentifier, index: Int): ExchangeGraphError? {
        val typed = document.typedIdentifiers(index = index)
        val snapshotRole = when (document.type(index = index)) {
            ResourceType.DEVICE -> GroveIdentifierRole.DEVICE_SNAPSHOT
            in CONTEXT_RESOURCE_TYPES -> GroveIdentifierRole.CONTEXT_SNAPSHOT
            else -> null
        }
        val selected = IDENTIFIER_PRIORITY.firstNotNullOfOrNull { role -> typed.firstOrNull { it.role == role } }
        return when {
            document.extensionIdentifiers(index = index, url = ExchangeContract.CONTEXT_SNAPSHOT_EXTENSION).isNotEmpty() ->
                violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTEXT_SNAPSHOT_EXTENSION,
                    location = "Bundle.entry[$index].resource.extension",
                )
            snapshotRole != null && typed.none { it.role == snapshotRole } ->
                violation(
                    rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTENT_SNAPSHOT_KEY,
                    location = "Bundle.entry[$index].resource.identifier",
                )
            !OpaqueIdentityScope.isCanonicalOpaqueValue(value = key.identifier.value) ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_OPAQUE_RESOURCE_IDENTITY, location = keyLocation(index = index))
            key !in typed ->
                violation(
                    rule = ExchangeGraphRule.MOBILE_OUTPUT_SOURCE_OUTPUT_REQUIRED,
                    location = "Bundle.entry[$index].resource.identifier",
                )
            selected != key || (snapshotRole != null && key.role != snapshotRole) ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY_SELECTION, location = keyLocation(index = index))
            else -> null
        }
    }

    /**
     * Every literal reference names an entry of the Bundle, of the type it declares, and never a contained resource.
     */
    private fun literalReferenceViolation(): ExchangeGraphError? {
        val types = document.entries.indices.associate { document.fullUrl(index = it) to document.resourceType(index = it) }
        document.entries.indices.forEach { index ->
            val resource = document.resource(index = index) ?: return@forEach
            if (resource.members.containsKey("contained")) return violation(rule = CONTAINED)
            document.objects(value = resource).forEach { node ->
                val reference = node.string(name = "reference")
                if (reference != null && !node.members.containsKey("identifier")) {
                    val declared = node.string(name = "type")
                    val actual = types[reference]
                    when {
                        reference.startsWith("#") -> return violation(rule = CONTAINED)
                        !types.containsKey(reference) -> return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_RESOLVED_REFERENCE)
                        declared != null && declared != actual ->
                            return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_DECLARED_TYPE)
                    }
                }
            }
        }
        return null
    }

    private fun keyLocation(index: Int) = "Bundle.entry[$index].extension.valueIdentifier"

    private companion object {
        val RECORD_VERSION_KEY = ExchangeGraphRule.MOBILE_EXCHANGE_RECORD_VERSION_KEY
        val RETRACTION_KEY = ExchangeGraphRule.MOBILE_RETRACTION_RETRACTION_KEY
        val CONTAINED = ExchangeGraphRule.MOBILE_EXCHANGE_CONTAINED_RESOURCE_PROHIBITED
        val ALLOWED_KEY_ROLES = setOf(
            GroveIdentifierRole.SOURCE_OUTPUT,
            GroveIdentifierRole.SOURCE_ARTIFACT,
            GroveIdentifierRole.SOURCE_RECORD,
            GroveIdentifierRole.WRITER_RECORD,
            GroveIdentifierRole.RECORDING_DEVICE,
            GroveIdentifierRole.DEVICE_SNAPSHOT,
            GroveIdentifierRole.CONTEXT_SNAPSHOT,
            GroveIdentifierRole.RECORD_VERSION,
            GroveIdentifierRole.RECORD_RETRACTION,
        )
        val IDENTIFIER_PRIORITY = listOf(
            GroveIdentifierRole.SOURCE_OUTPUT,
            GroveIdentifierRole.SOURCE_ARTIFACT,
            GroveIdentifierRole.SOURCE_RECORD,
            GroveIdentifierRole.WRITER_RECORD,
            GroveIdentifierRole.DEVICE_SNAPSHOT,
            GroveIdentifierRole.CONTEXT_SNAPSHOT,
            GroveIdentifierRole.RECORDING_DEVICE,
        )
        val CONTEXT_RESOURCE_TYPES = setOf(
            ResourceType.PATIENT,
            ResourceType.RESEARCH_STUDY,
            ResourceType.RESEARCH_SUBJECT,
            ResourceType.PLAN_DEFINITION,
            ResourceType.QUESTIONNAIRE_RESPONSE,
        )
    }
}
