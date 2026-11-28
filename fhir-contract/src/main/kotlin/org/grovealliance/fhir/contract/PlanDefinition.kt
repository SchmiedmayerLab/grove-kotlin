//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `PlanDefinition` naming one exact protocol revision by its canonical [url] and [version].
 */
data class PlanDefinition(
    val identifier: List<Identifier> = emptyList(),
    val url: String,
    val version: String,
    val status: String = "active",
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.PLAN_DEFINITION

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .string(name = "url", value = url)
        .elements(name = "identifier", values = identifier)
        .string(name = "version", value = version)
        .string(name = "status", value = status)
        .build()
}
