//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * A FHIR element the exchange states, which renders itself as the exact JSON it travels as.
 */
interface FhirElement {

    /**
     * The element's JSON; absent and empty members are left out.
     */
    @InternalGroveFhirApi
    fun toJson(): LosslessJsonValue
}

/**
 * A FHIR resource the exchange states.
 */
interface FhirResource : FhirElement {

    /**
     * The resource's `resourceType`.
     */
    val resourceType: ResourceType
}
