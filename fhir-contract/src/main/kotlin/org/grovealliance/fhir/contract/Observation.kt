//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Observation`, the resource a measurement travels as.
 */
@Suppress("detekt:LongParameterList")
data class Observation(
    val meta: Meta? = null,
    val extension: List<Extension> = emptyList(),
    val identifier: List<Identifier> = emptyList(),
    val status: String = "final",
    val category: List<CodeableConcept> = emptyList(),
    val code: CodeableConcept,
    val subject: Reference? = null,
    val effective: Effective? = null,
    val issued: String? = null,
    val value: Value? = null,
    val dataAbsentReason: CodeableConcept? = null,
    val interpretation: List<CodeableConcept> = emptyList(),
    val note: List<Annotation> = emptyList(),
    val bodySite: CodeableConcept? = null,
    val method: CodeableConcept? = null,
    val specimen: Reference? = null,
    val device: Reference? = null,
    val hasMember: List<Reference> = emptyList(),
    val derivedFrom: List<Reference> = emptyList(),
    val component: List<Component> = emptyList(),
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.OBSERVATION

    /**
     * `effective[x]`: the instant or the period the measurement applies to.
     */
    sealed interface Effective {

        /**
         * `effectiveDateTime`, as a `dateTime` lexeme.
         */
        data class DateTime(val value: String) : Effective

        /**
         * `effectivePeriod`.
         */
        data class PeriodValue(val value: Period) : Effective
    }

    /**
     * `value[x]` of an Observation or of one of its components.
     */
    sealed interface Value {

        /**
         * `valueQuantity`.
         */
        data class QuantityValue(val value: Quantity) : Value

        /**
         * `valueCodeableConcept`.
         */
        data class CodeableConceptValue(val value: CodeableConcept) : Value

        /**
         * `valueString`.
         */
        data class StringValue(val value: String) : Value

        /**
         * `valueBoolean`.
         */
        data class BooleanValue(val value: Boolean) : Value

        /**
         * `valueInteger`.
         */
        data class IntegerValue(val value: Long) : Value

        /**
         * `valuePeriod`.
         */
        data class PeriodValue(val value: Period) : Value
    }

    /**
     * One component of a panel, such as the systolic pressure of a blood-pressure panel.
     */
    data class Component(
        val extension: List<Extension> = emptyList(),
        val code: CodeableConcept,
        val value: Value? = null,
        val dataAbsentReason: CodeableConcept? = null,
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .elements(name = "extension", values = extension)
            .element(name = "code", value = code)
            .value(value = value)
            .element(name = "dataAbsentReason", value = dataAbsentReason)
            .build()
    }

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue {
        val builder = JsonObjectBuilder()
            .string(name = "resourceType", value = resourceType.value)
            .element(name = "meta", value = meta)
            .elements(name = "extension", values = extension)
            .elements(name = "identifier", values = identifier)
            .string(name = "status", value = status)
            .elements(name = "category", values = category)
            .element(name = "code", value = code)
            .element(name = "subject", value = subject)
        when (effective) {
            is Effective.DateTime -> builder.string(name = "effectiveDateTime", value = effective.value)
            is Effective.PeriodValue -> builder.element(name = "effectivePeriod", value = effective.value)
            null -> Unit
        }
        return builder.string(name = "issued", value = issued)
            .value(value = value)
            .element(name = "dataAbsentReason", value = dataAbsentReason)
            .elements(name = "interpretation", values = interpretation)
            .elements(name = "note", values = note)
            .element(name = "bodySite", value = bodySite)
            .element(name = "method", value = method)
            .element(name = "specimen", value = specimen)
            .element(name = "device", value = device)
            .elements(name = "hasMember", values = hasMember)
            .elements(name = "derivedFrom", values = derivedFrom)
            .elements(name = "component", values = component)
            .build()
    }

    private companion object {

        fun JsonObjectBuilder.value(value: Value?): JsonObjectBuilder = when (value) {
            is Value.QuantityValue -> element(name = "valueQuantity", value = value.value)
            is Value.CodeableConceptValue -> element(name = "valueCodeableConcept", value = value.value)
            is Value.StringValue -> string(name = "valueString", value = value.value)
            is Value.BooleanValue -> boolean(name = "valueBoolean", value = value.value)
            is Value.IntegerValue -> number(name = "valueInteger", lexeme = value.value.toString())
            is Value.PeriodValue -> element(name = "valuePeriod", value = value.value)
            null -> this
        }
    }
}
