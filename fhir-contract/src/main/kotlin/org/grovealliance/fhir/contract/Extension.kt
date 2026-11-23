//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Extension` at [url], with a [value] or nested [extension]s.
 */
data class Extension(
    val url: String,
    val value: Value? = null,
    val extension: List<Extension> = emptyList(),
) : FhirElement {

    /**
     * The `value[x]` of an extension, each choice written under its own member name.
     */
    sealed interface Value {

        /**
         * `valueString`.
         */
        data class StringValue(val value: String) : Value

        /**
         * `valueBoolean`.
         */
        data class BooleanValue(val value: Boolean) : Value

        /**
         * `valueInteger`, as its exact lexeme.
         */
        data class IntegerValue(val value: Long) : Value

        /**
         * `valueDecimal`.
         */
        data class DecimalValue(val value: FhirDecimal) : Value

        /**
         * `valueCode`.
         */
        data class CodeValue(val value: String) : Value

        /**
         * `valueUri`.
         */
        data class UriValue(val value: String) : Value

        /**
         * `valueCanonical`.
         */
        data class CanonicalValue(val value: String) : Value

        /**
         * `valueDateTime`, as a `dateTime` lexeme.
         */
        data class DateTimeValue(val value: String) : Value

        /**
         * `valueCoding`.
         */
        data class CodingValue(val value: Coding) : Value

        /**
         * `valueCodeableConcept`.
         */
        data class CodeableConceptValue(val value: CodeableConcept) : Value

        /**
         * `valueIdentifier`.
         */
        data class IdentifierValue(val value: Identifier) : Value

        /**
         * `valueReference`.
         */
        data class ReferenceValue(val value: Reference) : Value

        /**
         * `valueQuantity`.
         */
        data class QuantityValue(val value: Quantity) : Value

        /**
         * `valuePeriod`.
         */
        data class PeriodValue(val value: Period) : Value
    }

    @InternalGroveFhirApi
    @Suppress("detekt:CyclomaticComplexMethod")
    override fun toJson(): LosslessJsonValue {
        val builder = JsonObjectBuilder()
            .string(name = "url", value = url)
            .elements(name = "extension", values = extension)
        when (value) {
            is Value.StringValue -> builder.string(name = "valueString", value = value.value)
            is Value.BooleanValue -> builder.boolean(name = "valueBoolean", value = value.value)
            is Value.IntegerValue -> builder.number(name = "valueInteger", lexeme = value.value.toString())
            is Value.DecimalValue -> builder.number(name = "valueDecimal", lexeme = value.value.lexeme)
            is Value.CodeValue -> builder.string(name = "valueCode", value = value.value)
            is Value.UriValue -> builder.string(name = "valueUri", value = value.value)
            is Value.CanonicalValue -> builder.string(name = "valueCanonical", value = value.value)
            is Value.DateTimeValue -> builder.string(name = "valueDateTime", value = value.value)
            is Value.CodingValue -> builder.element(name = "valueCoding", value = value.value)
            is Value.CodeableConceptValue -> builder.element(name = "valueCodeableConcept", value = value.value)
            is Value.IdentifierValue -> builder.element(name = "valueIdentifier", value = value.value)
            is Value.ReferenceValue -> builder.element(name = "valueReference", value = value.value)
            is Value.QuantityValue -> builder.element(name = "valueQuantity", value = value.value)
            is Value.PeriodValue -> builder.element(name = "valuePeriod", value = value.value)
            null -> Unit
        }
        return builder.build()
    }
}
