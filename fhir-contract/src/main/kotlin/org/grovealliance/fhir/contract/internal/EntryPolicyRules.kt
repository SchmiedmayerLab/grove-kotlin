//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ExchangeContract
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonArray
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonString
import org.grovealliance.fhir.contract.ProfileClaims
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.obj
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.roleCodings
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The rules a Bundle's entries meet as they are stated: the resource types a Bundle of [kind] admits,
 * no contained resources, an output in every active Bundle, valid identity systems, the Bundle header
 * and profile, and plain collection entries.
 */
@OptIn(InternalGroveFhirApi::class)
internal class EntryPolicyRules(
    private val document: GraphDocument,
    private val kind: ExchangeRecord.Kind,
) {

    fun violation(): ExchangeGraphError? = shapeViolation()
        ?: serializedEntryViolation()
        ?: identifierSystemViolation()
        ?: headerViolation()
        ?: entriesViolation()

    /**
     * The first way the serialized Bundle is not a Bundle of entry objects: another root resource type,
     * an `entry` that is no array, or an entry or entry resource that is no object.
     */
    private fun shapeViolation(): ExchangeGraphError? {
        val bundle = document.bundle
        if (bundle.string(name = "resourceType") != ResourceType.BUNDLE.value) {
            return ExchangeGraphError.InvalidEntries(reason = "The root resource is not a Bundle.")
        }
        val entries = bundle.members["entry"] ?: return null
        if (entries !is JsonArray) return ExchangeGraphError.InvalidEntries(reason = "Bundle.entry is not an array.")
        entries.elements.forEachIndexed { index, entry ->
            if (entry !is JsonObject) return ExchangeGraphError.InvalidEntries(reason = "Bundle.entry[$index] is not an object.")
            val resource = entry.members["resource"]
            if (resource != null && resource !is JsonObject) {
                return ExchangeGraphError.InvalidEntries(reason = "Bundle.entry[$index].resource is not an object.")
            }
        }
        return null
    }

    private fun serializedEntryViolation(): ExchangeGraphError? {
        document.entries.indices.forEach { index ->
            val resource = document.resource(index = index)
            val resourceType = resource?.string(name = "resourceType")
                ?: return ExchangeGraphError.InvalidEntries(reason = "A Bundle entry has no resourceType.")
            resourceTypeViolation(resource = resource, resourceType = resourceType)?.let { return it }
            val refersToContained = document.objects(value = resource).any { it.string(name = "reference")?.startsWith("#") == true }
            if (resource.members.containsKey("contained") || refersToContained) {
                return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTAINED_RESOURCE_PROHIBITED)
            }
        }
        val statesOutput = document.entries.indices.any {
            document.type(index = it) in ExchangeContract.ACTIVE_OUTPUT_RESOURCE_TYPES
        }
        return if (kind == ExchangeRecord.Kind.ACTIVE && document.entries.isNotEmpty() && !statesOutput) {
            violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_REQUIRED)
        } else {
            null
        }
    }

    private fun resourceTypeViolation(resource: JsonObject, resourceType: String): ExchangeGraphError? = when (kind) {
        ExchangeRecord.Kind.ACTIVE -> when {
            ResourceType.fromValue(value = resourceType) !in ACTIVE_TYPES ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_RESOURCE_TYPE)
            else -> adapterOnlyProfileViolation(resource = resource, resourceType = resourceType)
        }
        ExchangeRecord.Kind.RETRACTION -> {
            val hasLiteralTarget = resource.array(name = "target").orEmpty()
                .any { (it as? JsonObject)?.members?.containsKey("reference") == true }
            when {
                resourceType != ResourceType.PROVENANCE.value && resourceType != ResourceType.DEVICE.value ->
                    violation(rule = ExchangeGraphRule.MOBILE_RETRACTION_NO_CLINICAL_COPY)
                resourceType == ResourceType.PROVENANCE.value && hasLiteralTarget ->
                    violation(rule = ExchangeGraphRule.MOBILE_RETRACTION_LOGICAL_TARGET)
                else -> null
            }
        }
    }

    private fun adapterOnlyProfileViolation(resource: JsonObject, resourceType: String): ExchangeGraphError? {
        val expected = ProfileClaims.adapterOnlyOutputProfiles[resourceType] ?: return null
        val profiles = resource.obj(name = "meta")?.array(name = "profile")?.map { (it as? JsonString)?.value }
        return if (profiles == listOf(expected)) null else violation(rule = ExchangeGraphRule.MOBILE_OUTPUT_ADAPTER_ONLY_PROFILE)
    }

    /**
     * Every Grove-typed Identifier states a system that is an absolute ASCII URI, checked on the text as stated.
     */
    private fun identifierSystemViolation(): ExchangeGraphError? {
        val invalid = document.objects(value = document.bundle).any { identifier ->
            identifier.roleCodings().isNotEmpty() &&
                identifier.string(name = "system")?.let { IdentifierSystem.create(rawValue = it).isSuccess } != true
        }
        return if (invalid) violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_OPAQUE_RESOURCE_IDENTITY) else null
    }

    private fun headerViolation(): ExchangeGraphError? {
        val bundle = document.bundle
        return when {
            bundle.string(name = "type") != "collection" -> ExchangeGraphError.InvalidEntries(reason = "The Bundle is not a collection.")
            bundle.string(name = "timestamp") == null -> violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_RECORD_TIMES)
            bundle.obj(name = "identifier")?.members?.containsKey("type") == true ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_BUNDLE_IDENTIFIER, location = "Bundle.identifier")
            else -> null
        }
    }

    private fun entriesViolation(): ExchangeGraphError? {
        val profiles = document.bundle.obj(name = "meta")?.array(name = "profile").orEmpty()
            .mapNotNull { (it as? JsonString)?.value }
            .filter { it == ExchangeContract.ACTIVE_BUNDLE_PROFILE || it == ExchangeContract.RETRACTION_BUNDLE_PROFILE }
        return when {
            profiles != listOf(kind.bundleProfile) ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_BUNDLE_PROFILE, location = "Bundle.meta.profile")
            document.entries.isEmpty() -> violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_REQUIRED)
            document.entries.any { entry -> listOf("search", "request", "response").any { entry.members.containsKey(it) } } ->
                violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_COLLECTION_ENTRY_OPERATION)
            else -> null
        }
    }

    private companion object {
        val ACTIVE_TYPES = ExchangeContract.ACTIVE_OUTPUT_RESOURCE_TYPES +
            ExchangeContract.ACTIVE_SUPPORTING_RESOURCE_TYPES +
            ResourceType.PROVENANCE
    }
}
