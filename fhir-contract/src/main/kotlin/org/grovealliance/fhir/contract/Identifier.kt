//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Identifier`: [value] in [system], with an optional [type] and [use].
 */
data class Identifier(
    val system: String? = null,
    val value: String? = null,
    val type: CodeableConcept? = null,
    val use: String? = null,
) : FhirElement {

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "use", value = use)
        .element(name = "type", value = type)
        .string(name = "system", value = system)
        .string(name = "value", value = value)
        .build()
}
