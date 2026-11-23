//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.fhir.contract.internal.ExchangeTestScope
import org.junit.Test

/**
 * Runs the grove-fhir mobile-exchange corpus: every base Bundle validates, and every case, a base with
 * one JSON patch applied, fails with exactly its expected rule and location.
 */
class ExchangeGraphCorpusTest {
    private val corpus = Json.parseToJsonElement(resource(name = "corpus.json")).jsonObject
    private val bases = corpus.getValue("bases").jsonArray.associate {
        val base = it.jsonObject
        base.text(name = "id") to resource(name = base.text(name = "path"))
    }

    @Test
    fun `validates every base bundle`() {
        // when
        val failures = bases.mapNotNull { (id, json) ->
            val error = ExchangeGraph.validating(json = json.toByteArray(), kind = kind(base = id)).exceptionOrNull()
            error?.let { "$id: ${(it as? ExchangeGraphError)?.diagnostic ?: it}" }
        }

        // then
        assertThat(failures).isEmpty()
    }

    @Test
    fun `refuses every case with its expected rule and location`() {
        // given
        val cases = corpus.getValue("cases").jsonArray.map { it.jsonObject }

        // when
        val mismatches = cases.mapNotNull { case ->
            val base = case.text(name = "base")
            val patched = patch(document = Json.parseToJsonElement(bases.getValue(base)), operations = case.getValue("patch").jsonArray)
            val json = sorted(element = patched).toString().let {
                if (case.text(name = "id") == DECIMAL_LEXEME_CASE) it.replace("\"value\":72}", "\"value\":72.0}") else it
            }
            val graph = ExchangeGraph.validating(json = json.toByteArray(), kind = kind(base = base))
            val error = graph.exceptionOrNull() ?: graph.getOrNull()?.derivedKeyViolation(scope = ExchangeTestScope.identityScope)
            val diagnostic = (error as? ExchangeGraphError)?.diagnostic
            val expected = case.getValue("expectedRule").jsonObject
            val actual = diagnostic?.let { "${it.code} at ${it.location}" }
            val wanted = "${expected.text(name = "code")} at ${expected.text(name = "location")}"
            "${case.text(name = "id")}: expected $wanted, got $actual".takeIf { actual != wanted }
        }

        // then
        assertThat(cases).isNotEmpty()
        assertThat(mismatches).isEmpty()
    }

    private companion object {
        const val DECIMAL_LEXEME_CASE = "decimal-lexeme-changes-record-version"
    }

    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { sorted(element = it.value) })
        is JsonArray -> JsonArray(element.map { sorted(element = it) })
        else -> element
    }

    private fun kind(base: String) = if (base.contains("retraction")) ExchangeRecord.Kind.RETRACTION else ExchangeRecord.Kind.ACTIVE

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/grove-fhir/corpora/mobile-exchange/$name")).readText()

    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

    /**
     * [document] with the RFC 6902 [operations] applied; `add`, `replace` and `remove` are the ones the corpus uses.
     */
    private fun patch(
        document: JsonElement,
        operations: JsonArray,
    ): JsonElement = operations.fold(document) { current, operation ->
        val members = operation.jsonObject
        val path = members.text(name = "path").removePrefix("/").split("/").map { it.replace("~1", "/").replace("~0", "~") }
        apply(node = current, path = path, op = members.text(name = "op"), value = members["value"])
    }

    private fun apply(node: JsonElement, path: List<String>, op: String, value: JsonElement?): JsonElement {
        val key = path.first()
        if (path.size > 1) {
            val child = { current: JsonElement -> apply(node = current, path = path.drop(1), op = op, value = value) }
            return when (node) {
                is JsonObject -> JsonObject(node + (key to child(node.getValue(key))))
                is JsonArray -> JsonArray(node.toMutableList().also { it[key.toInt()] = child(it[key.toInt()]) })
                else -> error("Cannot descend into $node")
            }
        }
        return leaf(node = node, key = key, op = op, value = value)
    }

    private fun leaf(node: JsonElement, key: String, op: String, value: JsonElement?): JsonElement {
        return when (node) {
            is JsonObject -> when (op) {
                "remove" -> JsonObject(node - key)
                else -> JsonObject(node + (key to requireNotNull(value)))
            }
            is JsonArray -> JsonArray(
                node.toMutableList().also { elements ->
                    val index = if (key == "-") elements.size else key.toInt()
                    when (op) {
                        "remove" -> elements.removeAt(index)
                        "replace" -> elements[index] = requireNotNull(value)
                        else -> elements.add(index, requireNotNull(value))
                    }
                },
            )
            else -> error("Cannot patch $node")
        }
    }
}
