//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The test vectors of the grove-fhir exchange protocol catalog the module is pinned to.
 */
class ExchangeProtocolCatalog {
    private val testVectors: JsonObject = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/grove-fhir/exchange-protocol.json")).readText(),
    ).jsonObject.getValue("testVectors").jsonObject

    val keyId: String = string(name = "keyId")

    val epoch: String = string(name = "epoch")

    val keyHex: String = string(name = "keyHex")

    fun vectors(name: String): List<JsonObject> = testVectors.getValue(name).jsonArray.map { it.jsonObject }

    private fun string(name: String) = testVectors.getValue(name).jsonPrimitive.content
}

/**
 * The string member [name] of a vector.
 */
fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

/**
 * The string array member [name] of a vector.
 */
fun JsonObject.texts(
    name: String,
): List<String> = getValue(name).jsonArray.map(JsonElement::jsonPrimitive).map { it.content }
