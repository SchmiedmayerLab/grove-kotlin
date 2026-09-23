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
import org.grovealliance.fhir.ConformanceFixtures.applyPatch
import org.grovealliance.fhir.ConformanceFixtures.string
import org.hl7.fhir.r4.model.ResourceType
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Checks the graph boundary against the shared positive bases and one-mutation negative corpus. */
class ExchangeGraphCorpusTest {
    private val directory: File? = ConformanceFixtures.configuredDirectory(ConformanceFixtures.EXCHANGE_CORPUS_PROPERTY)

    @Test
    fun `both shared bases parse as valid graphs and derive their retraction targets`() {
        assumeTrue("The ${ConformanceFixtures.EXCHANGE_CORPUS_PROPERTY} lane is not configured.", directory != null)
        val corpus = requireNotNull(directory)
        val manifest = Json.parseToJsonElement(corpus.resolve("corpus.json").readText()).jsonObject
        assertThat(manifest.string("version")).isEqualTo(ExchangeContract.PACKAGE_VERSION)

        val active = parse(ExchangeGraphKind.ACTIVE, corpus.resolve("exchange-bundle.json").readText())
        val retraction = parse(ExchangeGraphKind.RETRACTION, corpus.resolve("retraction-bundle.json").readText())

        assertThat(active.eventIdentifier.sequence).isEqualTo(EventSequence.of(42))
        assertThat(retraction.eventIdentifier.sequence).isEqualTo(EventSequence.of(43))
        val derived = active.retractionTargets()
        assertThat(derived.map { it.role }).containsExactly(
            RetractionTargetRole.DEVICE_SNAPSHOT,
            RetractionTargetRole.PRIMARY_OUTPUT,
        )
        val declared = retraction.retractionTargets().single()
        assertThat(declared.role).isEqualTo(RetractionTargetRole.PRIMARY_OUTPUT)
        assertThat(declared.resourceType).isEqualTo(ResourceType.Observation)
        assertThat(declared.identifier).isEqualTo(derived.single { it.role == RetractionTargetRole.PRIMARY_OUTPUT }.identifier)
        assertThat(requireNotNull(declared.nativeRecordIdentifier).value).isEqualTo("record-heart-001")
        assertThat(active.semanticallyEquals(parse(ExchangeGraphKind.ACTIVE, active.json))).isTrue()
    }

    @Test
    fun `every exact structured mutation yields exactly its registered rule code`() {
        assumeTrue("The ${ConformanceFixtures.EXCHANGE_CORPUS_PROPERTY} lane is not configured.", directory != null)
        val corpus = requireNotNull(directory)
        val manifest = Json.parseToJsonElement(corpus.resolve("corpus.json").readText()).jsonObject
        val bases = manifest.getValue("bases").jsonArray.associate { base ->
            base.jsonObject.string("id") to base.jsonObject.string("path")
        }
        val cases = manifest.getValue("cases").jsonArray.map { it.jsonObject }
        assertThat(cases).isNotEmpty()

        val mismatches = cases.mapNotNull { case ->
            val baseId = case.string("base")
            val kind = if (baseId == "mobile-retraction") ExchangeGraphKind.RETRACTION else ExchangeGraphKind.ACTIVE
            val base = Json.parseToJsonElement(corpus.resolve(bases.getValue(baseId)).readText())
            val mutated = case.getValue("patch").jsonArray.fold(base) { document, operation ->
                document.applyPatch(operation.jsonObject)
            }
            val expected = case.getValue("expectedRule").jsonObject
            val rule = requireNotNull(ExchangeGraphRule.of(expected.string("code"))) { case.string("id") }
            val error = (ExchangeGraph.parse(kind, mutated.toString()) as? ExchangeGraphParseResult.Invalid)?.error
            val diagnostic = error?.diagnostic
            val matches = diagnostic?.code == rule.code &&
                diagnostic.reason == expected.string("reason") &&
                diagnostic.severity.code == expected.string("severity")
            if (matches) null else "${case.string("id")}: expected ${rule.code}, got $error"
        }
        assertWithMessage("corpus mismatches").that(mismatches).isEmpty()
    }

    @Test
    fun `a base read under the wrong kind is refused with the bundle profile rule`() {
        assumeTrue("The ${ConformanceFixtures.EXCHANGE_CORPUS_PROPERTY} lane is not configured.", directory != null)
        val corpus = requireNotNull(directory)
        val result = ExchangeGraph.parse(ExchangeGraphKind.RETRACTION, corpus.resolve("exchange-bundle.json").readText())
        val error = (result as ExchangeGraphParseResult.Invalid).error
        assertThat(error.diagnostic.code).isEqualTo("mobile-exchange.bundle-profile")
    }

    @Test
    fun `bytes that are not a Bundle never throw`() {
        val notJson = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, "not json")
        assertThat((notJson as ExchangeGraphParseResult.Invalid).error).isInstanceOf(ExchangeGraphError.Malformed::class.java)
        val patient = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, """{"resourceType":"Patient"}""")
        assertThat((patient as ExchangeGraphParseResult.Invalid).error.diagnostic.code).isEqualTo("mobile-exchange.unclassified")
    }

    private fun parse(kind: ExchangeGraphKind, json: String): ExchangeGraph =
        when (val result = ExchangeGraph.parse(kind, json)) {
            is ExchangeGraphParseResult.Valid -> result.graph
            is ExchangeGraphParseResult.Invalid -> throw AssertionError(result.error.toString())
        }
}
