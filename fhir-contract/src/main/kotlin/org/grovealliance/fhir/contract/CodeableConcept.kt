//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `CodeableConcept`: its [coding] in order and an optional [text].
 */
data class CodeableConcept(
    val coding: List<Coding> = emptyList(),
    val text: String? = null,
) : FhirElement {

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .elements(name = "coding", values = coding)
        .string(name = "text", value = text)
        .build()
}
