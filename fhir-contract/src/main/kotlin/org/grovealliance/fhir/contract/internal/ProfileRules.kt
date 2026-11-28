//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.DirectProfileClaim
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.HealthConnectContract
import org.grovealliance.fhir.contract.HealthKitContract
import org.grovealliance.fhir.contract.Hl7Url
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonNumber
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.ProfileClaims
import org.grovealliance.fhir.contract.RecordingFormats
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.obj
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Base64

/**
 * The profile claims of an active graph: every entry declares one exact claim its type admits, carries
 * the identifier roles that claim names, and states the fixed unit, recording format and payload
 * integrity the claim implies.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:TooManyFunctions")
internal class ProfileRules(
    private val document: GraphDocument,
    private val facts: GraphFacts,
) {

    fun violation(): ExchangeGraphError? {
        document.entries.indices.forEach { index -> entryViolation(index = index)?.let { return it } }
        return facts.provenances().firstNotNullOfOrNull { dataOriginViolation(index = it) }
    }

    private fun entryViolation(index: Int): ExchangeGraphError? {
        val profiles = facts.profiles(index = index)
        return when (document.type(index = index)) {
            ResourceType.OBSERVATION ->
                observationViolation(profiles = profiles) ?: fixedQuantityViolation(index = index, profiles = profiles)
            ResourceType.DOCUMENT_REFERENCE -> documentViolation(index = index, profiles = profiles)
            ResourceType.DEVICE -> claim(
                profiles = profiles,
                modes = ProfileClaims.deviceProfileModes,
                rule = ExchangeGraphRule.MOBILE_SUPPORT_DEVICE_PROFILE,
            )
                .fold(
                    onSuccess = { identifierRoleViolation(
                        index = index,
                        claim = it,
                        rule = ExchangeGraphRule.MOBILE_DEVICE_RECORDING_DEVICE_DUAL_IDENTITY,
                    ) },
                    onFailure = { it as ExchangeGraphError },
                )
            ResourceType.QUESTIONNAIRE_RESPONSE -> claim(
                profiles = profiles,
                modes = ProfileClaims.questionnaireResponseProfileModes,
                rule = ExchangeGraphRule.MOBILE_SUPPORT_QUESTIONNAIRE_RESPONSE_PROFILE,
            ).exceptionOrNull() as? ExchangeGraphError
            ResourceType.PROVENANCE -> provenanceViolation(profiles = profiles)
            else -> null
        }
    }

    /**
     * An Observation declares one exact mode, one single-profile claim, or one semantic profile with the adapter it requires.
     */
    private fun observationViolation(profiles: List<String>): ExchangeGraphError? {
        val direct = profiles.toSet()
        if (profiles.isEmpty() || direct.size != profiles.size) return violation(rule = ExchangeGraphRule.MOBILE_OUTPUT_SEMANTIC_PROFILE)
        if (ProfileClaims.exactObservationProfileModes.any { it.toSet() == direct }) return null
        if (profiles.size == 1 && profiles[0] in ProfileClaims.singleObservationProfiles) return null
        val shared = direct.intersect(ProfileClaims.sharedObservationProfiles)
        val adapters = direct.intersect(ProfileClaims.observationAdapterProfiles.toSet())
        val semantic = shared.singleOrNull()
        val required = semantic?.let { ProfileClaims.providerOwnedSemanticAdapters[it] }
        val isValid = semantic != null && direct == shared + adapters &&
            if (required != null) adapters == setOf(required) else adapters.size <= 1
        return if (isValid) null else violation(rule = ExchangeGraphRule.MOBILE_OUTPUT_SEMANTIC_PROFILE)
    }

    private fun fixedQuantityViolation(index: Int, profiles: List<String>): ExchangeGraphError? {
        val claim = profiles.mapNotNull { ProfileClaims.fixedMeasurementQuantities[it] }.singleOrNull() ?: return null
        val quantity = document.resource(index = index)?.obj(name = "valueQuantity") ?: return null
        val contract = claim.quantity
        if (quantity.string(name = "system") != contract.system || quantity.string(name = "code") != contract.code) {
            return violation(
                rule = ExchangeGraphRule.MOBILE_OUTPUT_FIXED_QUANTITY_UNIT,
                location = "Bundle.entry[$index].resource.valueQuantity.code",
            )
        }
        val value = (quantity.members["value"] as? JsonNumber)?.lexeme?.toBigDecimalOrNull() ?: return null
        return if (contract.valueDomain?.contains(value = value) == false) {
            violation(
                rule = ExchangeGraphRule.MOBILE_OUTPUT_QUANTITY_VALUE_DOMAIN,
                location = "Bundle.entry[$index].resource.valueQuantity.value",
            )
        } else {
            null
        }
    }

    private fun documentViolation(index: Int, profiles: List<String>): ExchangeGraphError? {
        val claim = claim(
            profiles = profiles,
            modes = ProfileClaims.documentProfileModes,
            rule = ExchangeGraphRule.MOBILE_OUTPUT_DOCUMENT_PROFILE,
        )
            .getOrElse { return it as ExchangeGraphError }
        val content = document.resource(index = index)?.array(name = "content").orEmpty()
        return identifierRoleViolation(
            index = index,
            claim = claim,
            rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT,
            additionallyAllowed = setOf(GroveIdentifierRole.WRITER_RECORD),
        )
            ?: (if (content.size != 1) violation(rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT) else null)
            ?: (content.single() as? JsonObject)?.let { contentViolation(content = it, claim = claim) }
    }

    private fun contentViolation(content: JsonObject, claim: DirectProfileClaim): ExchangeGraphError? {
        val format = content.obj(name = "format")
        val attachment = content.obj(name = "attachment")
        val contentType = attachment?.string(name = "contentType")
        val isClinical = claim.profiles == listOf(HealthKitContract.CLINICAL_RECORD_PROFILE)
        if (isClinical && (format?.string(name = "code") != HealthKitContract.CLINICAL_FHIR_PAYLOAD_FORMAT ||
                contentType !in HealthKitContract.clinicalFhirContentTypes.values)
        ) {
            return violation(rule = ExchangeGraphRule.HEALTHKIT_CLINICAL_FHIR_REPRESENTATION)
        }
        if (format != null) {
            val registered = format.string(name = "code")?.let { RecordingFormats.contentTypes[it] }
            val isRegistered = format.members["version"] == null && format.string(name = "system") == RecordingFormats.CODE_SYSTEM &&
                registered != null && (contentType == null || contentType in registered)
            if (!isRegistered) {
                return violation(
                    rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_FORMAT,
                    location = "DocumentReference.content[0].format.code",
                )
            }
        }
        return attachment?.let { integrityViolation(attachment = it) }
    }

    /**
     * An embedded payload matches the size and SHA-1 hash its attachment states.
     */
    private fun integrityViolation(attachment: JsonObject): ExchangeGraphError? {
        val data = attachment.string(name = "data")?.let { decoded(base64 = it) } ?: return null
        val size = (attachment.members["size"] as? JsonNumber)?.lexeme?.toBigDecimalOrNull()
        if (size != null && size.compareTo(BigDecimal(data.size)) != 0) {
            return violation(
                rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_EMBEDDED_INTEGRITY,
                location = "DocumentReference.content[0].attachment.size",
            )
        }
        val hash = attachment.string(name = "hash") ?: return null
        val expected = MessageDigest.getInstance("SHA-1").digest(data)
        return if (decoded(base64 = hash)?.contentEquals(expected) != true) {
            violation(
                rule = ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_EMBEDDED_INTEGRITY,
                location = "DocumentReference.content[0].attachment.hash",
            )
        } else {
            null
        }
    }

    private fun decoded(base64: String): ByteArray? = runCatching { Base64.getDecoder().decode(base64) }.getOrNull()

    private fun provenanceViolation(profiles: List<String>): ExchangeGraphError? {
        val claimed = ProfileClaims.adapterProvenanceTargetProfiles.keys.filter { it in profiles }
        return when {
            claimed.isNotEmpty() && (claimed.size != 1 || profiles != claimed) ->
                ExchangeGraphError.InvalidEntries(reason = "An adapter conversion Provenance claims more than its adapter's profile.")
            profiles.size != 1 || profiles[0] !in ProfileClaims.activeProvenanceProfiles ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_PROFILE)
            else -> null
        }
    }

    /**
     * A Health Connect conversion names the writing application as its one data-origin agent.
     */
    private fun dataOriginViolation(index: Int): ExchangeGraphError? {
        if (Profile.HealthConnect.HEALTH_CONNECT_CONVERSION_PROVENANCE !in facts.profiles(index = index)) return null
        val location = "Provenance.entity[0].agent"
        val entity = document.resource(index = index)?.array(name = "entity")?.firstOrNull() as? JsonObject
        val agent = entity?.array(name = "agent")?.singleOrNull() as? JsonObject
            ?: return violation(rule = ExchangeGraphRule.HEALTH_CONNECT_PROVENANCE_DATA_ORIGIN_AGENT, location = location)
        val codes = agent.obj(name = "type")?.array(name = "coding").orEmpty().filterIsInstance<JsonObject>()
            .filter { it.string(name = "system") == Hl7Url.PROVENANCE_PARTICIPANT_TYPE }
            .map { it.string(name = "code") }
        val who = agent.obj(name = "who")
        val isLogicalDevice = who != null && !who.members.containsKey("reference") && who.string(name = "type") == ResourceType.DEVICE.value
        if (codes != listOf("enterer") || !isLogicalDevice) {
            return violation(rule = ExchangeGraphRule.HEALTH_CONNECT_PROVENANCE_DATA_ORIGIN_AGENT, location = "$location[0].who")
        }
        val identifier = who.obj(name = "identifier")
        val isPackageName = identifier?.string(name = "system") == HealthConnectContract.PACKAGE_NAME_SYSTEM
        return if (!isPackageName || identifier?.string(name = "value").isNullOrBlank()) {
            violation(rule = ExchangeGraphRule.HEALTH_CONNECT_PROVENANCE_DATA_ORIGIN_AGENT, location = "$location[0].who.identifier")
        } else {
            null
        }
    }

    private fun claim(profiles: List<String>, modes: List<DirectProfileClaim>, rule: ExchangeGraphRule): Result<DirectProfileClaim> {
        val direct = profiles.toSet()
        val match = modes.filter { profiles.size == direct.size && it.profiles.toSet() == direct }.singleOrNull()
        return match?.let { Result.success(it) } ?: Result.failure(violation(rule = rule))
    }

    /**
     * The typed identifiers of entry [index] carry each required role once, each optional role at most once, and no other.
     */
    private fun identifierRoleViolation(
        index: Int,
        claim: DirectProfileClaim,
        rule: ExchangeGraphRule,
        additionallyAllowed: Set<GroveIdentifierRole> = emptySet(),
    ): ExchangeGraphError? {
        val identifiers = document.typedIdentifiers(index = index)
        val roles = identifiers.map { it.role }
        val required = claim.requiredIdentifierRoles.mapNotNull { GroveIdentifierRole.fromCode(code = it) }.toSet()
        val optional = claim.optionalIdentifierRoles.mapNotNull { GroveIdentifierRole.fromCode(code = it) }.toSet() + additionallyAllowed
        val isValid = identifiers.toSet().size == identifiers.size &&
            required.all { role -> roles.count { it == role } == 1 } &&
            roles.all { it in required || it in optional } &&
            optional.all { role -> roles.count { it == role } <= 1 }
        return if (isValid) null else violation(rule = rule)
    }
}
