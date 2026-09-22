//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.fhir.ConformanceFixtures.string
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The generated rule enum is the registry: every code, reason and severity, and nothing else. */
class ExchangeGraphRuleTest {
    @Test
    fun `the rule enum carries every registered code once with its reason and severity`() {
        assertThat(ExchangeGraphRule.entries).hasSize(REGISTRY_SIZE)
        assertThat(ExchangeGraphRule.entries.map { it.code }).containsNoDuplicates()
        val warnings = ExchangeGraphRule.entries.filter { it.severity == ExchangeGraphDiagnostic.Severity.WARNING }
        assertThat(warnings.map { it.code }).containsExactly(
            "mobile-omission.recording-device",
            "mobile-omission.source-offset",
            "mobile-omission.unmodeled-metadata",
        )
        assertThat(ExchangeGraphRule.of("mobile-input.unclassified")).isEqualTo(ExchangeGraphRule.MOBILE_INPUT_UNCLASSIFIED)
        assertThat(ExchangeGraphRule.of("mobile-input.invented")).isNull()
        val diagnostic = ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_TYPE.at("Record")
        assertThat(diagnostic.reason).isEqualTo(ExchangeGraphRule.MOBILE_INPUT_UNSUPPORTED_SOURCE_TYPE.reason)
        assertThat(diagnostic.severity).isEqualTo(ExchangeGraphDiagnostic.Severity.ERROR)
    }

    @Test
    fun `the rule enum exactly matches the configured registry`() {
        val catalog = ConformanceFixtures.configuredDirectory(ConformanceFixtures.CATALOG_PROPERTY)
        assumeTrue("The ${ConformanceFixtures.CATALOG_PROPERTY} lane is not configured.", catalog != null)
        val registry = Json.parseToJsonElement(requireNotNull(catalog).readText()).jsonObject
            .getValue("producerDiagnostics").jsonArray
            .map { it.jsonObject }
            .associate { row ->
                row.string("code") to (row.string("reason") to (row["severity"]?.jsonPrimitive?.content ?: "error"))
            }

        assertThat(ExchangeGraphRule.entries.associate { it.code to (it.reason to it.severity.code) }).isEqualTo(registry)
    }

    @Test
    fun `identity errors report registered input rules`() {
        assertThat(ExchangeIdentityError.NonScalarText("source-record.native-record-id").diagnostic.code)
            .isEqualTo("mobile-input.text-not-unicode-scalar")
        assertThat(ExchangeIdentityError.EmptyComponent("source-record.native-record-id").diagnostic.code)
            .isEqualTo("mobile-input.required-metadata-missing")
        assertThat(ExchangeIdentityError.MalformedIdentifier("Bundle.identifier.value").diagnostic.code)
            .isEqualTo("mobile-exchange.event-identity")
    }

    private companion object {
        const val REGISTRY_SIZE = 86
    }
}
