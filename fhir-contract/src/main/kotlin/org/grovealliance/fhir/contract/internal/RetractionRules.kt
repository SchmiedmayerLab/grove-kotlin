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
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.ProfileClaims
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RetractionRecord
import org.grovealliance.fhir.contract.RoledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.obj
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roleCodings
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The rules of a retraction graph: only retraction Provenances and their Devices, each Provenance
 * retracting one source record through distinct opaque logical targets in a closed role, and nothing
 * referencing a Provenance.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:TooManyFunctions")
internal class RetractionRules(
    private val document: GraphDocument,
    private val facts: GraphFacts,
) {

    fun violation(): ExchangeGraphError? {
        document.entries.indices.forEach { index -> entryViolation(index = index)?.let { return it } }
        provenanceViolation()?.let { return it }
        val sources = mutableSetOf<RoledIdentifier>()
        facts.provenances().forEach { index ->
            val provenance = requireNotNull(document.resource(index = index))
            val source = facts.exactSourceEntity(provenance = provenance).getOrElse { return it as ExchangeGraphError }
            val targets = provenance.array(name = "target").orEmpty()
            if (targets.isEmpty()) {
                return violation(
                    rule = ExchangeGraphRule.MOBILE_RETRACTION_TARGET_REQUIRED,
                    location = "Provenance.target",
                )
            }
            targetsViolation(targets = targets, source = source)?.let { return it }
            if (!sources.add(source)) {
                return violation(
                    rule = ExchangeGraphRule.MOBILE_RETRACTION_PROVENANCE,
                    location = "Bundle.entry[$index].resource.entity",
                )
            }
        }
        val provenanceUrls = facts.provenances().map { facts.fullUrl(index = it) }.toSet()
        return facts.crossingReferenceViolation { _, reference -> reference in provenanceUrls }
            ?: facts.connectivityViolation(supportingTypes = setOf(ResourceType.DEVICE))
    }

    private fun entryViolation(index: Int): ExchangeGraphError? = when (document.type(index = index)) {
        ResourceType.PROVENANCE -> if (facts.profiles(index = index) != ProfileClaims.retractionProvenanceProfiles) {
            violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_PROFILE)
        } else {
            null
        }
        ResourceType.DEVICE -> deviceViolation(index = index)
        else -> violation(rule = ExchangeGraphRule.MOBILE_RETRACTION_NO_CLINICAL_COPY)
    }

    private fun deviceViolation(index: Int): ExchangeGraphError? {
        val profiles = facts.profiles(index = index)
        val direct = profiles.toSet()
        val claim = ProfileClaims.deviceProfileModes.singleOrNull { profiles.size == direct.size && it.profiles.toSet() == direct }
            ?: return violation(rule = ExchangeGraphRule.MOBILE_SUPPORT_DEVICE_PROFILE)
        val identifiers = document.typedIdentifiers(index = index)
        val roles = identifiers.map { it.role.code }
        val dual = violation(rule = ExchangeGraphRule.MOBILE_DEVICE_RECORDING_DEVICE_DUAL_IDENTITY)
        val rolesAreValid = identifiers.toSet().size == identifiers.size &&
            claim.requiredIdentifierRoles.all { role -> roles.count { it == role } == 1 } &&
            roles.all { it in claim.requiredIdentifierRoles || it in claim.optionalIdentifierRoles } &&
            claim.optionalIdentifierRoles.all { role -> roles.count { it == role } <= 1 }
        if (!rolesAreValid) return dual
        val key = document.entryKey(index = index) ?: return dual
        return facts.deviceIdentityViolation(index = index, key = key, identifiers = identifiers)
    }

    private fun provenanceViolation(): ExchangeGraphError? {
        val provenances = facts.provenances()
        val lifecycles = provenances.map { facts.lifecycle(provenance = requireNotNull(document.resource(index = it))) }
        if (GraphFacts.Lifecycle.INVALID in lifecycles) return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_LIFECYCLE_CODING)
        if (provenances.isEmpty() || lifecycles.any { it != GraphFacts.Lifecycle.RETRACTION }) {
            return violation(rule = ExchangeGraphRule.MOBILE_RETRACTION_PROVENANCE)
        }
        facts.recordTimesViolation(indices = provenances)?.let { return it }
        val isAssembled = provenances.all { index ->
            Profile.Mobile.GROVE_MOBILE_RETRACTION_PROVENANCE in facts.profiles(index = index) &&
                facts.assemblerResolves(provenance = requireNotNull(document.resource(index = index)))
        }
        return if (isAssembled) null else violation(rule = ExchangeGraphRule.MOBILE_RETRACTION_NO_CLINICAL_COPY)
    }

    private fun targetsViolation(targets: List<*>, source: RoledIdentifier): ExchangeGraphError? {
        val logical = mutableSetOf<BusinessIdentifier>()
        targets.forEachIndexed { index, value ->
            val identifier = target(
                target = value as? JsonObject,
                index = index,
                count = targets.size,
                source = source,
            ).getOrElse { return it as ExchangeGraphError }
            if (!logical.add(identifier.identifier)) {
                return violation(
                    rule = ExchangeGraphRule.MOBILE_RETRACTION_DISTINCT_TARGET,
                    location = "Provenance.target[$index].identifier",
                )
            }
        }
        return null
    }

    @Suppress("detekt:CyclomaticComplexMethod")
    private fun target(target: JsonObject?, index: Int, count: Int, source: RoledIdentifier): Result<RoledIdentifier> {
        val identifier = target?.takeUnless { it.members.containsKey("reference") }?.obj(name = "identifier")?.roledIdentifier()
            ?: return failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_LOGICAL_TARGET)
        if (!OpaqueIdentityScope.isCanonicalOpaqueValue(value = identifier.identifier.value)) {
            return failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_OPAQUE_TARGET)
        }
        val extensions = target.array(name = "extension").orEmpty().filterIsInstance<JsonObject>()
        val role = extensions.singleOrNull { it.string(name = "url") == ExchangeContract.RETRACTION_TARGET_ROLE_EXTENSION }
            ?.string(name = "valueCode")
            ?: return failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_TARGET_ROLE)
        val native = nativeRecordIdentifier(extensions = extensions).getOrElse { return Result.failure(it) }
        if (role == RetractionRecord.Target.Role.SOURCE_RECORD.code) {
            val location = "Provenance.target[$index]"
            return when {
                identifier.role != GroveIdentifierRole.SOURCE_RECORD -> failure(
                    rule = ExchangeGraphRule.MOBILE_RETRACTION_SOURCE_RECORD_TARGET,
                    location = "$location.identifier",
                )
                target.members.containsKey("type") -> failure(
                    rule = ExchangeGraphRule.MOBILE_RETRACTION_SOURCE_RECORD_TARGET,
                    location = "$location.type",
                )
                count != 1 -> failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_SOURCE_RECORD_TARGET, location = "Provenance.target")
                identifier != source -> failure(
                    rule = ExchangeGraphRule.MOBILE_RETRACTION_SOURCE_RECORD_TARGET,
                    location = "$location.identifier",
                )
                else -> Result.success(identifier)
            }
        }
        val targetRole = RetractionRecord.Target.Role.entries.firstOrNull { it.code == role }
            ?: return failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_TARGET_ROLE)
        val resourceType = target.string(name = "type")?.takeIf { RESOURCE_TYPE.matches(it) }
            ?: return failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_LOGICAL_TARGET)
        return RetractionRecord.Target.output(
            identifier = identifier,
            resourceType = resourceType,
            role = targetRole,
            nativeRecordIdentifier = native,
        ).fold(
            onSuccess = { Result.success(identifier) },
            onFailure = { failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_ROLE_TARGET_TYPE) },
        )
    }

    /**
     * The at most one native record identifier a target discloses, never in a Grove role.
     */
    private fun nativeRecordIdentifier(extensions: List<JsonObject>): Result<BusinessIdentifier?> {
        val natives = extensions.filter { it.string(name = "url") == ExchangeContract.RETRACTION_TARGET_NATIVE_IDENTIFIER_EXTENSION }
        val native = natives.firstOrNull() ?: return Result.success(null)
        val disclosed = native.obj(name = "valueIdentifier")
        val hasRoleSystem = disclosed?.obj(name = "type")?.array(name = "coding").orEmpty()
            .any { (it as? JsonObject)?.string(name = "system") == ExchangeContract.IDENTIFIER_ROLE_CODE_SYSTEM }
        val system = disclosed?.string(name = "system")?.let { IdentifierSystem.create(rawValue = it).getOrNull() }
        val identifier = system?.let { disclosed.string(name = "value")?.let { value -> BusinessIdentifier.create(
            system = it,
            value = value,
        ).getOrNull() } }
        val isPlain = !hasRoleSystem && disclosed?.roleCodings().isNullOrEmpty()
        return if (natives.size > 1 || !isPlain || identifier == null) {
            failure(rule = ExchangeGraphRule.MOBILE_RETRACTION_NATIVE_RECORD_IDENTIFIER)
        } else {
            Result.success(identifier)
        }
    }

    private fun <T> failure(rule: ExchangeGraphRule, location: String? = null): Result<T> =
        Result.failure(if (location == null) violation(rule = rule) else violation(rule = rule, location = location))

    private companion object {
        val RESOURCE_TYPE = Regex("[A-Z][A-Za-z]+")
    }
}
