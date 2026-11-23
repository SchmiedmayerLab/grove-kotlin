//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ExchangeContract
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonString
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.ReferencePolicy
import org.grovealliance.fhir.contract.ReferencePolicy.GovernedPath
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.array
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.obj
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.string
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The governed references of a graph: every reference the exchange constrains is either one literal
 * reference to an entry of an allowed type, or one logical reference that declares its type and names a
 * valid identifier, a logical Patient reference never by a Grove-reserved system or role.
 */
@OptIn(InternalGroveFhirApi::class)
internal class ReferenceRules(private val document: GraphDocument) {
    private val resourceTypes = document.entries.indices.associate { document.fullUrl(index = it) to document.type(index = it) }
    private val recordingDevices = document.entries.indices
        .filter { index ->
            document.type(index = index) == ResourceType.DEVICE &&
                document.resource(index = index)?.obj(name = "meta")?.array(name = "profile").orEmpty()
                    .any { (it as? JsonString)?.value == Profile.Mobile.GROVE_RECORDING_DEVICE }
        }
        .mapNotNull { document.fullUrl(index = it) }
        .toSet()

    fun violation(): ExchangeGraphError? {
        document.entries.indices.forEach { index ->
            val resource = document.resource(index = index) ?: return@forEach
            governedReferenceViolation(resource = resource)?.let { return it }
            governedExtensionViolation(resource = resource)?.let { return it }
        }
        return null
    }

    private fun governedReferenceViolation(resource: JsonObject): ExchangeGraphError? {
        val resourceType = ResourceType.fromValue(value = resource.string(name = "resourceType"))
        val fields = GOVERNED_PATHS[resourceType].orEmpty()
        fields.forEach { field ->
            val base = if (resourceType == ResourceType.QUESTIONNAIRE_RESPONSE) "${resourceType.value}.${field.path}" else null
            references(resource = resource, name = field.path).forEach { reference ->
                referenceViolation(reference = reference, expected = field.targetTypes, base = base)?.let { return it }
            }
            if (resourceType == ResourceType.OBSERVATION && field.path == "device") {
                val literal = resource.obj(name = "device")?.string(name = "reference")
                if (literal != null && literal !in recordingDevices) {
                    return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_TARGET_TYPE)
                }
            }
        }
        return null
    }

    private fun references(resource: JsonObject, name: String): List<JsonObject> =
        resource.obj(name = name)?.let { listOf(it) } ?: resource.array(name = name).orEmpty().filterIsInstance<JsonObject>()

    /**
     * Each governed extension carries one valid reference to its allowed type.
     */
    private fun governedExtensionViolation(resource: JsonObject): ExchangeGraphError? {
        document.objects(value = resource).forEach { node ->
            val expected = ReferencePolicy.extensionTargets[node.string(name = "url")] ?: return@forEach
            val reference = node.obj(name = "valueReference") ?: return violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_SHAPE)
            referenceViolation(reference = reference, expected = expected, base = null)?.let { return it }
        }
        return null
    }

    private fun referenceViolation(reference: JsonObject, expected: Set<ResourceType>, base: String?): ExchangeGraphError? {
        val literal = reference.string(name = "reference")
        val identifier = reference.obj(name = "identifier")
        return when {
            (literal != null) == (identifier != null) || (literal == null && reference.members.containsKey("reference")) ->
                located(rule = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_SHAPE, base = base)
            literal != null -> literalViolation(reference = reference, literal = literal, expected = expected, base = base)
            else -> logicalViolation(reference = reference, identifier = requireNotNull(identifier), expected = expected, base = base)
        }
    }

    private fun literalViolation(reference: JsonObject, literal: String, expected: Set<ResourceType>, base: String?): ExchangeGraphError? {
        val declared = reference.string(name = "type")
        return when {
            literal.startsWith("#") -> located(
                rule = ExchangeGraphRule.MOBILE_EXCHANGE_CONTAINED_RESOURCE_PROHIBITED,
                base = base,
                field = "reference",
            )
            !resourceTypes.containsKey(literal) -> located(
                rule = ExchangeGraphRule.MOBILE_EXCHANGE_RESOLVED_REFERENCE,
                base = base,
                field = "reference",
            )
            resourceTypes[literal] !in expected -> located(
                rule = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_TARGET_TYPE,
                base = base,
                field = "reference",
            )
            declared != null && declared != resourceTypes[literal]?.value ->
                located(rule = ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_DECLARED_TYPE, base = base, field = "type")
            else -> null
        }
    }

    private fun logicalViolation(
        reference: JsonObject,
        identifier: JsonObject,
        expected: Set<ResourceType>,
        base: String?,
    ): ExchangeGraphError? {
        val isPatient = expected == setOf(ResourceType.PATIENT)
        val system = identifier.string(name = "system")
        val isValid = ResourceType.fromValue(value = reference.string(name = "type")) in expected &&
            system != null && IdentifierSystem.create(rawValue = system).isSuccess &&
            !identifier.string(name = "value").isNullOrEmpty()
        val carriesGroveRole = identifier.obj(name = "type")?.array(name = "coding").orEmpty()
            .filterIsInstance<JsonObject>()
            .any { it.string(name = "system") == ExchangeContract.IDENTIFIER_ROLE_CODE_SYSTEM }
        return when {
            !isValid -> located(
                rule = if (isPatient) LOGICAL_PATIENT else ExchangeGraphRule.MOBILE_EXCHANGE_REFERENCE_SHAPE,
                base = base,
            )
            isPatient && carriesGroveRole -> located(
                rule = ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_PATIENT_REFERENCE,
                base = base,
                field = "identifier.type",
            )
            isPatient && system in ReferencePolicy.reservedPatientSystems ->
                located(rule = ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_PATIENT_REFERENCE, base = base, field = "identifier.system")
            else -> null
        }
    }

    private fun located(rule: ExchangeGraphRule, base: String?, field: String? = null): ExchangeGraphError =
        if (base == null) violation(rule = rule) else violation(rule = rule, location = field?.let { "$base.$it" } ?: base)

    private companion object {
        val LOGICAL_PATIENT = ExchangeGraphRule.MOBILE_EXCHANGE_LOGICAL_PATIENT_REFERENCE

        /**
         * The references governed beyond the catalog's paths.
         */
        val ADDITIONAL_PATHS = listOf(
            GovernedPath(
                resourceType = ResourceType.MEDICATION_ADMINISTRATION,
                path = "device",
                isRepeating = true,
                targetTypes = setOf(ResourceType.DEVICE),
            ),
            GovernedPath(
                resourceType = ResourceType.SPECIMEN,
                path = "parent",
                isRepeating = true,
                targetTypes = setOf(ResourceType.SPECIMEN),
            ),
            GovernedPath(
                resourceType = ResourceType.DEVICE,
                path = "patient",
                isRepeating = false,
                targetTypes = setOf(ResourceType.PATIENT),
            ),
        )
        val GOVERNED_PATHS = (ReferencePolicy.paths + ADDITIONAL_PATHS).groupBy { it.resourceType }
    }
}
