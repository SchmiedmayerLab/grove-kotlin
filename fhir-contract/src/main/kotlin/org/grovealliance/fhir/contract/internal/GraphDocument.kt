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
import org.grovealliance.fhir.contract.LosslessJsonValue
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonArray
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonString
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.RoledIdentifier

/**
 * A Bundle under validation, read losslessly: its members and entries, and readers every rule shares.
 */
@OptIn(InternalGroveFhirApi::class)
internal class GraphDocument(val bundle: JsonObject) {

    /**
     * The Bundle's entries.
     */
    val entries: List<JsonObject> = (bundle.members["entry"] as? JsonArray)?.elements.orEmpty().filterIsInstance<JsonObject>()

    fun resource(index: Int): JsonObject? = entries[index].members["resource"] as? JsonObject

    fun resourceType(index: Int): String? = resource(index = index)?.string(name = "resourceType")

    fun type(index: Int): ResourceType? = ResourceType.fromValue(value = resourceType(index = index))

    fun fullUrl(index: Int): String? = entries[index].string(name = "fullUrl")

    /**
     * The Grove-typed identifier the entry-key extension of entry [index] states, when there is exactly one.
     */
    fun entryKey(index: Int): RoledIdentifier? {
        val keys = (entries[index].members["extension"] as? JsonArray)?.elements.orEmpty()
            .filterIsInstance<JsonObject>()
            .filter { it.string(name = "url") == ExchangeContract.ENTRY_KEY_EXTENSION }
        return keys.singleOrNull()?.let { (it.members["valueIdentifier"] as? JsonObject)?.roledIdentifier() }
    }

    /**
     * The Grove-typed identifiers of the resource of entry [index], each read or `null` when malformed.
     */
    fun typedIdentifiers(index: Int): List<RoledIdentifier> =
        resource(index = index)?.array(name = "identifier").orEmpty().filterIsInstance<JsonObject>().mapNotNull { it.roledIdentifier() }

    /**
     * The Grove-typed `valueIdentifier` of every top-level extension at [url] of the resource of entry [index].
     */
    fun extensionIdentifiers(index: Int, url: String): List<RoledIdentifier?> =
        resource(index = index)?.array(name = "extension").orEmpty().filterIsInstance<JsonObject>()
            .filter { it.string(name = "url") == url }
            .map { (it.members["valueIdentifier"] as? JsonObject)?.roledIdentifier() }

    /**
     * Every JSON object of [value], parents before children.
     */
    fun objects(value: LosslessJsonValue): List<JsonObject> = when (value) {
        is JsonObject -> listOf(value) + value.members.values.flatMap { objects(value = it) }
        is JsonArray -> value.elements.flatMap { objects(value = it) }
        else -> emptyList()
    }

    companion object {

        /**
         * Where the conformance corpus fixes the diagnostic of each rule a check reports without a finding of its own.
         */
        private val defaultLocations = mapOf(
            ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_KEY to "Bundle.entry[0]",
            ExchangeGraphRule.MOBILE_EXCHANGE_RECORD_TIMES to "Bundle.timestamp",
            ExchangeGraphRule.MOBILE_EXCHANGE_DETERMINISTIC_FULL_URL to "Bundle.entry[1].fullUrl",
            ExchangeGraphRule.MOBILE_EXCHANGE_RESOLVED_REFERENCE to "Bundle.entry[2].resource.subject.reference",
            ExchangeGraphRule.MOBILE_OUTPUT_FIXED_QUANTITY_UNIT to "Bundle.entry[2].resource.valueQuantity.code",
            ExchangeGraphRule.MOBILE_OUTPUT_QUANTITY_VALUE_DOMAIN to "Bundle.entry[2].resource.valueQuantity.value",
            ExchangeGraphRule.MOBILE_OUTPUT_SOURCE_OUTPUT_REQUIRED to "Bundle.entry[2].resource.identifier",
            ExchangeGraphRule.MOBILE_EXCHANGE_TRANSFORM_PROVENANCE to "Bundle.entry",
            ExchangeGraphRule.MOBILE_SUPPORT_CONNECTED to "Bundle.entry",
            ExchangeGraphRule.MOBILE_EXCHANGE_COLLECTION_ENTRY_OPERATION to "Bundle.entry",
            ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_REQUIRED to "Bundle.entry",
            ExchangeGraphRule.MOBILE_EXCHANGE_OUTPUT_REQUIRED to "Bundle.entry",
            ExchangeGraphRule.MOBILE_RETRACTION_PROVENANCE to "Bundle.entry",
            ExchangeGraphRule.MOBILE_SUPPORT_STUDY_CONTEXT to "Bundle.entry",
            ExchangeGraphRule.MOBILE_RETRACTION_LOGICAL_TARGET to "Provenance.target[0]",
            ExchangeGraphRule.MOBILE_RETRACTION_TARGET_ROLE to "Provenance.target[0].extension",
            ExchangeGraphRule.MOBILE_RETRACTION_NATIVE_RECORD_IDENTIFIER to "Provenance.target[0].extension.valueIdentifier.type",
            ExchangeGraphRule.MOBILE_RETRACTION_OPAQUE_TARGET to "Provenance.target[0].identifier.value",
            ExchangeGraphRule.MOBILE_RETRACTION_NO_CLINICAL_COPY to "Bundle.entry[0].resource",
            ExchangeGraphRule.MOBILE_EXCHANGE_LIFECYCLE_CODING to "Provenance.activity.coding",
            ExchangeGraphRule.MOBILE_OUTPUT_SEMANTIC_PROFILE to "Observation.meta.profile",
            ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_TARGET_TYPE to "Observation.subject.reference",
            ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_DECLARED_TYPE to "Observation.subject.type",
            ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_SOURCE_ENTITY to "Provenance.entity[0].what",
            ExchangeGraphRule.MOBILE_RETRACTION_ROLE_TARGET_TYPE to "Provenance.target[0].type",
            ExchangeGraphRule.MOBILE_EXCHANGE_SINGLE_SOURCE_ENTITY to "Provenance.entity",
            ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_SHAPE to "Observation.subject",
            ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_PATIENT_REFERENCE to "Observation.subject",
            ExchangeGraphRule.MOBILE_EXCHANGE_ENTRY_RESOURCE_TYPE to "Bundle.entry[0].resource.resourceType",
            ExchangeGraphRule.MOBILE_OUTPUT_ADAPTER_ONLY_PROFILE to "Specimen.meta.profile",
            ExchangeGraphRule.MOBILE_EXCHANGE_CONTAINED_RESOURCE_PROHIBITED to "Bundle.entry[2].resource.contained",
            ExchangeGraphRule.MOBILE_OUTPUT_DOCUMENT_PROFILE to "DocumentReference.meta.profile",
            ExchangeGraphRule.HEALTHKIT_CLINICAL_FHIR_REPRESENTATION to "DocumentReference.content[0]",
            ExchangeGraphRule.MOBILE_SUPPORT_DEVICE_PROFILE to "Device.meta.profile",
            ExchangeGraphRule.MOBILE_EXCHANGE_PROVENANCE_PROFILE to "Provenance.meta.profile",
            ExchangeGraphRule.MOBILE_SUPPORT_QUESTIONNAIRE_RESPONSE_PROFILE to "QuestionnaireResponse.meta.profile",
            ExchangeGraphRule.MOBILE_DEVICE_RECORDING_DEVICE_DUAL_IDENTITY to "Device.identifier",
            ExchangeGraphRule.SENSOR_RECORDING_DOCUMENT_IDENTITY_AND_CONTENT to "DocumentReference.identifier",
            ExchangeGraphRule.MOBILE_EXCHANGE_OPAQUE_RESOURCE_IDENTITY to "Bundle.entry[0].resource.identifier",
            ExchangeGraphRule.MOBILE_OMISSION_SOURCE_OFFSET to "Observation.effective",
            ExchangeGraphRule.MOBILE_OMISSION_UNMODELED_METADATA to "Observation.extension",
            ExchangeGraphRule.MOBILE_OMISSION_RECORDING_METHOD to "Observation.extension",
        )

        fun violation(rule: ExchangeGraphRule, location: String = defaultLocations[rule] ?: "Bundle"): ExchangeGraphError.RuleViolation =
            ExchangeGraphError.RuleViolation(rule = rule, location = location)

        fun JsonObject.string(name: String): String? = (members[name] as? JsonString)?.value

        fun JsonObject.array(name: String): List<LosslessJsonValue>? = (members[name] as? JsonArray)?.elements

        fun JsonObject.obj(name: String): JsonObject? = members[name] as? JsonObject

        /**
         * The Grove role codings of an Identifier object.
         */
        fun JsonObject.roleCodings(): List<JsonObject> = obj(name = "type")?.array(name = "coding").orEmpty()
            .filterIsInstance<JsonObject>()
            .filter { it.string(name = "system") == GroveIdentifierRole.CODE_SYSTEM }

        /**
         * The Grove-typed Identifier this object states: one role coding of a closed role, a valid system and a value.
         */
        fun JsonObject.roledIdentifier(): RoledIdentifier? {
            val role = roleCodings().singleOrNull()?.string(name = "code")?.let { GroveIdentifierRole.fromCode(code = it) } ?: return null
            val system = string(name = "system")?.let { IdentifierSystem.create(rawValue = it).getOrNull() } ?: return null
            val identifier = string(name = "value")?.let { BusinessIdentifier.create(
                system = system,
                value = it,
            ).getOrNull() } ?: return null
            return RoledIdentifier(
                identifier = identifier,
                role = role,
            )
        }
    }
}
