//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Quantity`: [value] in the UCUM [code] of [system], with the human-readable [unit] and an
 * optional [comparator].
 */
data class Quantity(
    val value: FhirDecimal? = null,
    val unit: String? = null,
    val system: String? = null,
    val code: String? = null,
    val comparator: String? = null,
) : FhirElement {

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .number(name = "value", lexeme = value?.lexeme)
        .string(name = "comparator", value = comparator)
        .string(name = "unit", value = unit)
        .string(name = "system", value = system)
        .string(name = "code", value = code)
        .build()
}
