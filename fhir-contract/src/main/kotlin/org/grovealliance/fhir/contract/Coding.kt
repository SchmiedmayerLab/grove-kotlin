//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Coding`: [code] in [system], with an optional [display] and code system [version].
 */
data class Coding(
    val system: String? = null,
    val code: String? = null,
    val display: String? = null,
    val version: String? = null,
) : FhirElement {

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "system", value = system)
        .string(name = "version", value = version)
        .string(name = "code", value = code)
        .string(name = "display", value = display)
        .build()
}
