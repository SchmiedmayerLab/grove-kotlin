//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Provenance`: the conversion or retraction of one source record.
 */
data class Provenance(
    val meta: Meta? = null,
    val extension: List<Extension> = emptyList(),
    val target: List<Reference> = emptyList(),
    val occurredDateTime: String? = null,
    val occurredPeriod: Period? = null,
    val recorded: String,
    val activity: CodeableConcept? = null,
    val agent: List<Agent> = emptyList(),
    val entity: List<Entity> = emptyList(),
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.PROVENANCE

    /**
     * One participant: [who] acted, as [type], possibly on behalf of [onBehalfOf].
     */
    data class Agent(
        val extension: List<Extension> = emptyList(),
        val type: CodeableConcept? = null,
        val role: List<CodeableConcept> = emptyList(),
        val who: Reference,
        val onBehalfOf: Reference? = null,
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .elements(name = "extension", values = extension)
            .element(name = "type", value = type)
            .elements(name = "role", values = role)
            .element(name = "who", value = who)
            .element(name = "onBehalfOf", value = onBehalfOf)
            .build()
    }

    /**
     * One entity the activity used, in [role], such as the source record it converted.
     */
    data class Entity(
        val extension: List<Extension> = emptyList(),
        val role: String,
        val what: Reference,
        val agent: List<Agent> = emptyList(),
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .elements(name = "extension", values = extension)
            .string(name = "role", value = role)
            .element(name = "what", value = what)
            .elements(name = "agent", values = agent)
            .build()
    }

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .element(name = "meta", value = meta)
        .elements(name = "extension", values = extension)
        .elements(name = "target", values = target)
        .string(name = "occurredDateTime", value = occurredDateTime)
        .element(name = "occurredPeriod", value = occurredPeriod)
        .string(name = "recorded", value = recorded)
        .element(name = "activity", value = activity)
        .elements(name = "agent", values = agent)
        .elements(name = "entity", values = entity)
        .build()
}
