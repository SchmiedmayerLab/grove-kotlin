//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `ResearchSubject`: the enrollment linking the [individual] to a [study].
 */
data class ResearchSubject(
    val identifier: List<Identifier> = emptyList(),
    val status: String = "on-study",
    val study: Reference,
    val individual: Reference,
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.RESEARCH_SUBJECT

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .elements(name = "identifier", values = identifier)
        .string(name = "status", value = status)
        .element(name = "study", value = study)
        .element(name = "individual", value = individual)
        .build()
}
