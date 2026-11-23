//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Specimen`, such as the blood a glucose result was measured in.
 */
data class Specimen(
    val meta: Meta? = null,
    val extension: List<Extension> = emptyList(),
    val identifier: List<Identifier> = emptyList(),
    val type: CodeableConcept? = null,
    val subject: Reference? = null,
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.SPECIMEN

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .element(name = "meta", value = meta)
        .elements(name = "extension", values = extension)
        .elements(name = "identifier", values = identifier)
        .element(name = "type", value = type)
        .element(name = "subject", value = subject)
        .build()
}
