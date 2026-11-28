//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `ResearchStudy`: the study a participant is enrolled in, naming its exact protocol revision.
 */
data class ResearchStudy(
    val identifier: List<Identifier> = emptyList(),
    val protocol: List<Reference> = emptyList(),
    val status: String = "active",
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.RESEARCH_STUDY

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .elements(name = "identifier", values = identifier)
        .elements(name = "protocol", values = protocol)
        .string(name = "status", value = status)
        .build()
}
