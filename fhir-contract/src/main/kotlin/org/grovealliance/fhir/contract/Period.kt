//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Period` from [start] to [end], each a `dateTime` lexeme.
 */
data class Period(
    val start: String? = null,
    val end: String? = null,
) : FhirElement {

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "start", value = start)
        .string(name = "end", value = end)
        .build()
}
