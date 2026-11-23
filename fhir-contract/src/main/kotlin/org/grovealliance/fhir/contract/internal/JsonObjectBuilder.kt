//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.FhirElement
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue

/**
 * Collects the members of a FHIR element's JSON, leaving out every absent or empty one, as FHIR
 * forbids stating an element empty.
 */
@OptIn(InternalGroveFhirApi::class)
internal class JsonObjectBuilder {
    private val members = LinkedHashMap<String, LosslessJsonValue>()

    fun string(name: String, value: String?) = apply {
        if (value != null) members[name] = LosslessJsonValue.JsonString(value = value)
    }

    fun number(name: String, lexeme: String?) = apply {
        if (lexeme != null) members[name] = LosslessJsonValue.JsonNumber(lexeme = lexeme)
    }

    fun boolean(name: String, value: Boolean?) = apply {
        if (value != null) members[name] = LosslessJsonValue.JsonBoolean(value = value)
    }

    fun element(name: String, value: FhirElement?) = apply {
        value?.toJson()?.takeUnless { it.isEmpty() }?.let { members[name] = it }
    }

    fun elements(name: String, values: List<FhirElement>) = apply {
        val json = values.map { it.toJson() }.filterNot { it.isEmpty() }
        if (json.isNotEmpty()) members[name] = LosslessJsonValue.JsonArray(elements = json)
    }

    fun strings(name: String, values: List<String>) = apply {
        if (values.isNotEmpty()) {
            members[name] = LosslessJsonValue.JsonArray(elements = values.map { LosslessJsonValue.JsonString(value = it) })
        }
    }

    fun json(name: String, value: LosslessJsonValue?) = apply {
        if (value != null) members[name] = value
    }

    fun build(): LosslessJsonValue.JsonObject = LosslessJsonValue.JsonObject(members = members)

    private fun LosslessJsonValue.isEmpty() = (this as? LosslessJsonValue.JsonObject)?.members?.isEmpty() == true
}
