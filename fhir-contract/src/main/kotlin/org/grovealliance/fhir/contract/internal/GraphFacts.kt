//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.ExchangeContract
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.GroveIdentifierRole
import org.grovealliance.fhir.contract.HealthKitContract
import org.grovealliance.fhir.contract.Hl7Url
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonArray
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonString
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RoledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.obj
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roleCodings
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roledIdentifier
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * What the lifecycle rules read from a graph: its outputs and subject, its lifecycle Provenances, the
 * literal references between its entries, and the records those references bind together.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:TooManyFunctions")
internal class GraphFacts(private val document: GraphDocument) {
    private val fullUrls = document.entries.indices.map { document.fullUrl(index = it).orEmpty() }
    private val patients = document.entries.indices
        .filter { document.type(index = it) == ResourceType.PATIENT }
        .associate { fullUrls[it] to requireNotNull(document.resource(index = it)) }
    private val references: Map<String, Set<String>> = fullUrls.indices.associate { index ->
        fullUrls[index] to literalReferences(index = index).intersect(fullUrls.toSet())
    }
    private val studiesByResearchSubject = document.entries.indices
        .filter { document.type(index = it) == ResourceType.RESEARCH_SUBJECT && document.fullUrl(index = it) != null }
        .mapNotNull { index ->
            document.resource(index = index)?.obj(name = "study")?.string(name = "reference")?.let { fullUrls[index] to it }
        }
        .toMap()

    /**
     * How a Provenance states its lifecycle event.
     */
    enum class Lifecycle { ACTIVE, RETRACTION, NONE, INVALID }

    fun fullUrl(index: Int): String = fullUrls[index]

    fun profiles(index: Int): List<String> =
        document.resource(index = index)?.obj(name = "meta")?.array(name = "profile").orEmpty().mapNotNull { (it as? JsonString)?.value }

    fun isActiveOutput(index: Int): Boolean = document.type(index = index) in ExchangeContract.ACTIVE_OUTPUT_RESOURCE_TYPES

    fun provenances(): List<Int> = document.entries.indices.filter { document.type(index = it) == ResourceType.PROVENANCE }

    fun literalReferences(index: Int): Set<String> = document.resource(index = index)
        ?.let { resource -> document.objects(value = resource).mapNotNull { it.string(name = "reference") }.toSet() }
        .orEmpty()

    /**
     * Every subject the outputs of the graph state.
     */
    fun subjectStatements(): Set<BusinessIdentifier> = document.entries.indices
        .filter { isActiveOutput(index = it) }
        .mapNotNull { index ->
            val resource = document.resource(index = index)
            val subject = resource?.obj(name = subjectField(resource = resource))
            subject?.let { subjectStatement(reference = it) }
        }
        .toSet()

    /**
     * Whether an active output states a subject that names no one: a value that is no reference, or a
     * reference resolving to no subject pseudonym.
     */
    fun hasUnresolvedSubject(): Boolean = document.entries.indices
        .filter { isActiveOutput(index = it) }
        .any { index ->
            val resource = document.resource(index = index) ?: return@any false
            val subject = resource.members[subjectField(resource = resource)] ?: return@any false
            subject !is JsonObject || subjectStatement(reference = subject) == null
        }

    fun bundleSubject(): BusinessIdentifier? = subjectStatements().singleOrNull()

    fun subjectStatement(reference: JsonObject): BusinessIdentifier? {
        val literal = reference.string(name = "reference")
            ?: return reference.obj(name = "identifier")?.let { businessIdentifier(identifier = it) }
        return patients[literal]?.let { patientPseudonym(patient = it) }
    }

    /**
     * The one identifier of [patient] that carries no Grove role.
     */
    fun patientPseudonym(patient: JsonObject): BusinessIdentifier? = patient.array(name = "identifier").orEmpty()
        .filterIsInstance<JsonObject>()
        .filter { it.roleCodings().isEmpty() }
        .singleOrNull()
        ?.let { businessIdentifier(identifier = it) }

    fun lifecycle(provenance: JsonObject): Lifecycle {
        val codings = provenance.obj(name = "activity")?.array(name = "coding").orEmpty().filterIsInstance<JsonObject>()
        val iso = codings.filter { it.string(name = "system") == ExchangeContract.ACTIVE_ACTIVITY_SYSTEM }
        val grove = codings.filter { it.string(name = "system") == ExchangeContract.RETRACTION_ACTIVITY_SYSTEM }
        return when {
            iso.isEmpty() && grove.isEmpty() -> Lifecycle.NONE
            iso.size + grove.size != 1 -> Lifecycle.INVALID
            iso.isNotEmpty() -> iso[0].lifecycle(code = ExchangeContract.ACTIVE_ACTIVITY_CODE, kind = Lifecycle.ACTIVE)
            else -> grove[0].lifecycle(code = ExchangeContract.RETRACTION_ACTIVITY_CODE, kind = Lifecycle.RETRACTION)
        }
    }

    private fun JsonObject.lifecycle(code: String, kind: Lifecycle) = if (string(name = "code") == code) kind else Lifecycle.INVALID

    /**
     * Every Provenance of [indices] states when its record occurred and was recorded.
     */
    fun recordTimesViolation(indices: List<Int>): ExchangeGraphError? = indices.firstOrNull { index ->
        val provenance = document.resource(index = index) ?: return@firstOrNull true
        val period = provenance.obj(name = "occurredPeriod")
        val occurred = provenance.string(name = "occurredDateTime") != null ||
            (period != null && (period.string(name = "start") != null || period.string(name = "end") != null))
        provenance.string(name = "recorded") == null || !occurred
    }?.let { violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_RECORD_TIMES, location = "Bundle.entry[$it].resource") }

    /**
     * Whether [provenance] names exactly one assembler, which is an application Device of the graph.
     */
    fun assemblerResolves(provenance: JsonObject): Boolean {
        val applications = document.entries.indices
            .filter { index ->
                document.type(index = index) == ResourceType.DEVICE &&
                    profiles(index = index).any { it in APPLICATION_PROFILES }
            }
            .mapNotNull { document.fullUrl(index = it) }
            .toSet()
        val assemblers = provenance.array(name = "agent").orEmpty().filterIsInstance<JsonObject>().filter { agent ->
            val codes = agent.obj(name = "type")?.array(name = "coding").orEmpty().filterIsInstance<JsonObject>()
                .filter { it.string(name = "system") == Hl7Url.PROVENANCE_PARTICIPANT_TYPE }
            codes.size == 1 && codes[0].string(name = "code") == "assembler"
        }
        val who = assemblers.singleOrNull()?.obj(name = "who") ?: return false
        return who.obj(name = "identifier") == null && who.string(name = "reference") in applications
    }

    /**
     * The one logical source-record entity of [provenance].
     */
    fun exactSourceEntity(provenance: JsonObject): Result<RoledIdentifier> {
        val entity = provenance.array(name = "entity")?.singleOrNull() as? JsonObject
            ?: return Result.failure(violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_SINGLE_SOURCE_ENTITY))
        val what = entity.obj(name = "what")
        val source = what?.takeIf { entity.string(name = "role") == "source" && !it.members.containsKey("reference") }
            ?.obj(name = "identifier")?.roledIdentifier()
            ?.takeIf { it.role == GroveIdentifierRole.SOURCE_RECORD }
            ?.takeIf { OpaqueIdentityScope.isCanonicalOpaqueValue(value = it.identifier.value) }
        return source?.let { Result.success(it) }
            ?: Result.failure(violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_SOURCE_ENTITY))
    }

    /**
     * The fullUrls reached from [starts] through literal references, with every ResearchSubject of a reached study.
     */
    fun unit(starts: List<String>): Set<String> {
        val unit = mutableSetOf<String>()
        var pending = starts.filter { references.containsKey(it) }.toMutableList()
        while (true) {
            while (pending.isNotEmpty()) {
                val current = pending.removeAt(pending.lastIndex)
                if (unit.add(current)) pending += references[current].orEmpty().filterNot { it in unit }
            }
            pending = studiesByResearchSubject.filter { it.key !in unit && it.value in unit }.keys.toMutableList()
            if (pending.isEmpty()) return unit
        }
    }

    /**
     * Every supporting entry is reached from a lifecycle Provenance or its targets.
     */
    fun connectivityViolation(supportingTypes: Set<ResourceType>): ExchangeGraphError? {
        val starts = provenances().flatMap { index ->
            val targets = document.resource(index = index)?.array(name = "target").orEmpty().filterIsInstance<JsonObject>()
            listOf(fullUrls[index]) + targets.mapNotNull { it.string(name = "reference") }
        }
        val connected = unit(starts = starts)
        val isDisconnected = document.entries.indices.any { index ->
            val isSupporting = document.type(index = index) in supportingTypes
            document.fullUrl(index = index) != null && isSupporting && fullUrls[index] !in connected
        }
        return if (isDisconnected) violation(rule = ExchangeGraphRule.MOBILE_SUPPORT_CONNECTED) else null
    }

    /**
     * The first literal reference that [crosses] a record boundary, located at its element.
     */
    fun crossingReferenceViolation(crosses: (referrer: String, reference: String) -> Boolean): ExchangeGraphError? {
        document.entries.indices.forEach { index ->
            val resource = document.resource(index = index) ?: return@forEach
            document.objects(value = resource).mapNotNull { it.string(name = "reference") }.forEach { reference ->
                if (crosses(fullUrls[index], reference)) {
                    val path = elementPath(reference = reference, value = resource, path = "").orEmpty()
                    return violation(
                        rule = ExchangeGraphRule.MOBILE_EXCHANGE_CROSS_RECORD_REFERENCE,
                        location = "Bundle.entry[$index].resource.$path.reference",
                    )
                }
            }
        }
        return null
    }

    /**
     * A Device names one profile, is keyed by its one device snapshot, and carries the identities its profile admits.
     */
    fun deviceIdentityViolation(
        index: Int,
        key: RoledIdentifier,
        identifiers: List<RoledIdentifier>,
    ): ExchangeGraphError? {
        val device = document.resource(index = index) ?: return null
        val claimed = DEVICE_PROFILES.filter { it in profiles(index = index) }
        val snapshots = identifiers.filter { it.role == GroveIdentifierRole.DEVICE_SNAPSHOT }
        val all = device.array(name = "identifier").orEmpty().filterIsInstance<JsonObject>()
        val isKeyed = snapshots == listOf(key) && key.role == GroveIdentifierRole.DEVICE_SNAPSHOT &&
            OpaqueIdentityScope.isCanonicalOpaqueValue(value = key.identifier.value)
        val isValid = claimed.size == 1 && isKeyed && when (claimed.single()) {
            HealthKitContract.APPLICATION_DEVICE_PROFILE -> isHealthKitApplication(all = all, identifiers = identifiers)
            Profile.Mobile.GROVE_RECORDING_DEVICE -> identifiers.count { it.role == GroveIdentifierRole.RECORDING_DEVICE } <= 1 &&
                identifiers.all { it.role == GroveIdentifierRole.DEVICE_SNAPSHOT || it.role == GroveIdentifierRole.RECORDING_DEVICE }
            Profile.Mobile.GROVE_HOST_DEVICE -> all.size == 1 && identifiers.size == 1
            else -> true
        }
        return if (isValid) null else violation(rule = ExchangeGraphRule.MOBILE_DEVICE_RECORDING_DEVICE_DUAL_IDENTITY)
    }

    /**
     * A HealthKit application Device carries its snapshot identity and one Apple bundle identifier.
     */
    private fun isHealthKitApplication(all: List<JsonObject>, identifiers: List<RoledIdentifier>): Boolean {
        val bundleIdentifier = all.filter { identifier ->
            val codings = identifier.obj(name = "type")?.array(name = "coding").orEmpty().filterIsInstance<JsonObject>()
                .filter { it.string(name = "system") == HealthKitContract.APPLE_BUNDLE_IDENTIFIER_TYPE_SYSTEM }
            codings.size == 1 && codings[0].string(name = "code") == HealthKitContract.APPLE_BUNDLE_IDENTIFIER_TYPE_CODE
        }.singleOrNull()
        return all.size == 2 && identifiers.size == 1 && bundleIdentifier != null &&
            bundleIdentifier.string(name = "system") == HealthKitContract.APPLE_BUNDLE_IDENTIFIER_SYSTEM &&
            !bundleIdentifier.string(name = "value").isNullOrEmpty()
    }

    private fun elementPath(reference: String, value: LosslessJsonValue, path: String): String? = when (value) {
        is JsonObject -> if (value.string(name = "reference") == reference) {
            path
        } else {
            value.members.keys.sorted().firstNotNullOfOrNull { key ->
                elementPath(reference = reference, value = value.members.getValue(key), path = if (path.isEmpty()) key else "$path.$key")
            }
        }
        is JsonArray -> value.elements.withIndex().firstNotNullOfOrNull { (index, child) ->
            elementPath(reference = reference, value = child, path = "$path[$index]")
        }
        else -> null
    }

    private fun subjectField(resource: JsonObject): String {
        val isPrescription = ResourceType.fromValue(value = resource.string(name = "resourceType")) == ResourceType.VISION_PRESCRIPTION
        return if (isPrescription) "patient" else "subject"
    }

    private fun businessIdentifier(identifier: JsonObject): BusinessIdentifier? {
        val system = identifier.string(name = "system")?.let { IdentifierSystem.create(rawValue = it).getOrNull() } ?: return null
        return identifier.string(name = "value")?.let { BusinessIdentifier.create(system = system, value = it).getOrNull() }
    }

    private companion object {
        val APPLICATION_PROFILES = setOf(Profile.Mobile.GROVE_APPLICATION_DEVICE, HealthKitContract.APPLICATION_DEVICE_PROFILE)
        val DEVICE_PROFILES = listOf(
            Profile.Mobile.GROVE_APPLICATION_DEVICE,
            HealthKitContract.APPLICATION_DEVICE_PROFILE,
            Profile.Mobile.GROVE_HOST_DEVICE,
            Profile.Mobile.GROVE_RECORDING_DEVICE,
        )
    }
}
