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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ExchangeGraphValidationTest {
    private val base = Json.parseToJsonElement(resource(name = "exchange-bundle.json")).jsonObject

    @Test
    fun `accepts the base bundle`() {
        // when
        val result = validate(bundle = base)

        // then
        assertThat(result.exceptionOrNull()).isNull()
    }

    @Test
    fun `refuses a root resource other than a Bundle`() {
        // given
        val bundle = JsonObject(base + ("resourceType" to JsonPrimitive("Patient")))

        // when
        val result = validate(bundle = bundle)

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(ExchangeGraphError.InvalidEntries::class.java)
    }

    @Test
    fun `refuses a null entry`() {
        // when
        val result = validate(bundle = withEntry(entry = JsonNull))

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(ExchangeGraphError.InvalidEntries::class.java)
    }

    @Test
    fun `refuses a number entry`() {
        // when
        val result = validate(bundle = withEntry(entry = JsonPrimitive(1)))

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(ExchangeGraphError.InvalidEntries::class.java)
    }

    @Test
    fun `refuses a string entry`() {
        // when
        val result = validate(bundle = withEntry(entry = JsonPrimitive("bad")))

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(ExchangeGraphError.InvalidEntries::class.java)
    }

    @Test
    fun `refuses an entry whose resource is no object`() {
        // when
        val result = validate(bundle = withEntry(entry = JsonObject(mapOf("resource" to JsonPrimitive(1)))))

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(ExchangeGraphError.InvalidEntries::class.java)
    }

    @Test
    fun `refuses an active bundle without a subject`() {
        // given
        val bundle = withResources { resource -> if (resource.type == "Patient") null else JsonObject(resource - "subject") }

        // when
        val result = validate(bundle = bundle)

        // then
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `refuses an active bundle whose subject is no reference`() {
        // given
        val bundle = withResources { resource ->
            when {
                resource.type == "Patient" -> null
                "subject" in resource -> JsonObject(resource + ("subject" to JsonPrimitive(1)))
                else -> resource
            }
        }

        // when
        val result = validate(bundle = bundle)

        // then
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `accepts a retraction bundle without a subject`() {
        // given
        val retraction = resource(name = "retraction-bundle.json").toByteArray()

        // when
        val result = ExchangeGraph.validating(json = retraction, kind = ExchangeRecord.Kind.RETRACTION)

        // then
        assertThat(result.exceptionOrNull()).isNull()
    }

    @Test
    fun `keeps its bytes when the caller changes the validated array`() {
        // given
        val input = resource(name = "exchange-bundle.json").toByteArray()
        val graph = ExchangeGraph.validating(json = input, kind = ExchangeRecord.Kind.ACTIVE).getOrThrow()
        val validated = input.copyOf()

        // when
        input.fill(element = 'X'.code.toByte())

        // then
        assertThat(graph.json).isEqualTo(validated)
        assertThat(graph.records().isSuccess).isTrue()
    }

    @Test
    fun `keeps its bytes when the caller changes the array it read`() {
        // given
        val input = resource(name = "exchange-bundle.json").toByteArray()
        val graph = ExchangeGraph.validating(json = input, kind = ExchangeRecord.Kind.ACTIVE).getOrThrow()
        val validated = graph.json.copyOf()

        // when
        graph.json.fill(element = 'X'.code.toByte())

        // then
        assertThat(graph.json).isEqualTo(validated)
    }

    private fun validate(bundle: JsonObject): Result<ExchangeGraph> = ExchangeGraph.validating(
        json = Json.encodeToString(JsonElement.serializer(), bundle).toByteArray(),
        kind = ExchangeRecord.Kind.ACTIVE,
    )

    private fun withEntry(entry: JsonElement): JsonObject =
        JsonObject(base + ("entry" to JsonArray(base.getValue("entry").jsonArray + entry)))

    /**
     * The base bundle with each entry's resource replaced by [transform]'s result, dropping the entries it maps to `null`.
     */
    private fun withResources(transform: (JsonObject) -> JsonObject?): JsonObject {
        val entries = base.getValue("entry").jsonArray.mapNotNull { entry ->
            val members = entry.jsonObject
            transform(members.getValue("resource").jsonObject)?.let { JsonObject(members + ("resource" to it)) }
        }
        return JsonObject(base + ("entry" to JsonArray(entries)))
    }

    private val JsonObject.type: String get() = getValue("resourceType").jsonPrimitive.content

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/grove-fhir/corpora/mobile-exchange/$name")).readText()
}
