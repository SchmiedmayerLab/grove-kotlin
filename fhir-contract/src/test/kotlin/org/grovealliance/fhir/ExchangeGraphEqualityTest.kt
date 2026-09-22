//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.grovealliance.fhir.ConformanceFixtures.objectValue
import org.grovealliance.fhir.ConformanceFixtures.string
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Binds token-level equality to the receiver-lifecycle `reformatted-replay` and `lexeme-replay` sequences. */
class ExchangeGraphEqualityTest {
    @Test
    fun `the vendored equality sequences decide the exact retry over tokens not bytes`() {
        val index = ConformanceFixtures.resourceJson("/receiver-lifecycle/equality.json").jsonObject
        val events = index.objectValue("events")
        val graphs = events.keys.associateWith { alias ->
            val path = events.objectValue(alias).string("path")
            val text = ConformanceFixtures.resourceText("/receiver-lifecycle/$path")
            assertWithMessage(alias).that(ExchangeProtocol.sha256Hex(text)).isEqualTo(events.objectValue(alias).string("sha256"))
            parse(text)
        }
        assertThat(index.getValue("sequences").jsonArray.map { it.jsonObject.string("id") })
            .containsExactly("reformatted-replay", "lexeme-replay").inOrder()

        val original = graphs.getValue("original")
        val reformatted = graphs.getValue("reformatted-retry")
        val lexeme = graphs.getValue("lexeme-retry")

        assertThat(reformatted.sha256).isNotEqualTo(original.sha256)
        assertThat(original.semanticallyEquals(reformatted)).isTrue()
        assertThat(reformatted.semanticallyEquals(original)).isTrue()
        assertThat(original.semanticallyEquals(lexeme)).isFalse()
        assertThat(lexeme.semanticallyEquals(original)).isFalse()
        assertThat(original.eventIdentifier).isEqualTo(lexeme.eventIdentifier)
    }

    @Test
    fun `the configured receiver corpus agrees with the vendored fixtures`() {
        val directory = ConformanceFixtures.configuredDirectory(ConformanceFixtures.RECEIVER_CORPUS_PROPERTY)
        assumeTrue("The ${ConformanceFixtures.RECEIVER_CORPUS_PROPERTY} lane is not configured.", directory != null)
        val corpus = requireNotNull(directory)
        val events = Json.parseToJsonElement(corpus.resolve("events.json").readText()).jsonObject.objectValue("events")
        val original = parse(corpus.resolve(events.objectValue("original").string("path")).readText())
        val reformatted = parse(corpus.resolve(events.objectValue("reformatted-retry").string("path")).readText())
        val lexeme = parse(corpus.resolve(events.objectValue("lexeme-retry").string("path")).readText())
        val altered = parse(corpus.resolve(events.objectValue("altered-retry").string("path")).readText())

        assertThat(original.semanticallyEquals(reformatted)).isTrue()
        assertThat(original.semanticallyEquals(lexeme)).isFalse()
        assertThat(original.semanticallyEquals(altered)).isFalse()
        listOf("original", "corrected", "unordered-a", "unordered-b", "pending", "target").forEach { alias ->
            val file = File(corpus, events.objectValue(alias).string("path"))
            assertWithMessage(alias).that(ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, file.readText()))
                .isInstanceOf(ExchangeGraphParseResult.Valid::class.java)
        }
        val retraction = corpus.resolve(events.objectValue("retraction").string("path")).readText()
        assertThat(ExchangeGraph.parse(ExchangeGraphKind.RETRACTION, retraction))
            .isInstanceOf(ExchangeGraphParseResult.Valid::class.java)
    }

    @Test
    fun `token trees compare members without order and strings after unescaping`() {
        assertThat(JsonToken.parse("""{"a":1,"b":[true,null,"x"]}"""))
            .isEqualTo(JsonToken.parse(""" { "b" : [ true , null , "x" ] , "a" : 1 } """))
        assertThat(JsonToken.parse("""{"v":72}""")).isNotEqualTo(JsonToken.parse("""{"v":72.0}"""))
        assertThat(JsonToken.parse("""{"v":1e2}""")).isNotEqualTo(JsonToken.parse("""{"v":100}"""))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { JsonToken.parse("""{"a":1,"a":2}""") }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { JsonToken.parse("""{"a":01}""") }
    }

    private fun parse(json: String): ExchangeGraph = when (val result = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, json)) {
        is ExchangeGraphParseResult.Valid -> result.graph
        is ExchangeGraphParseResult.Invalid -> throw AssertionError(result.error.toString())
    }
}
