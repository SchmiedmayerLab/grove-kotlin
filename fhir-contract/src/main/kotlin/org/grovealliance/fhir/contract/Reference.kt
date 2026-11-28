//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Reference`: a literal [reference] to an entry, or a logical one by [identifier], with the
 * [type] of the resource it names.
 */
data class Reference(
    val extension: List<Extension> = emptyList(),
    val reference: String? = null,
    val type: String? = null,
    val identifier: Identifier? = null,
    val display: String? = null,
) : FhirElement {

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .elements(name = "extension", values = extension)
        .string(name = "reference", value = reference)
        .string(name = "type", value = type)
        .element(name = "identifier", value = identifier)
        .string(name = "display", value = display)
        .build()
}
