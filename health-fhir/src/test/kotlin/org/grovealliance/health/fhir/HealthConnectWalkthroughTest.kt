//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.ExchangeGraph
import org.grovealliance.fhir.ExchangeGraphKind
import org.grovealliance.fhir.ExchangeGraphParseResult
import org.grovealliance.fhir.ProducerDiagnostic
import org.junit.Test

/** Runs the README's conversion and retraction blocks; the installation and context blocks need a device and only compile here. */
class HealthConnectWalkthroughTest {
    @Test
    fun `the walkthrough uploads the graph bytes of a converted record and logs a refusal`() {
        val uploads = mutableListOf<ByteArray>()
        val diagnostics = mutableListOf<ProducerDiagnostic>()
        val context = HealthConnectTestFixtures.context()

        HealthConnectWalkthrough.convert(HealthConnectFixtureRecords.steps(), context, uploads::add, diagnostics::add)
        HealthConnectWalkthrough.convert(HealthConnectFixtureRecords.steps(""), context, uploads::add, diagnostics::add)

        val expected = HealthConnectTestFixtures.converted(HealthConnectFixtureRecords.steps()).graph.json
        assertThat(uploads.single().toString(Charsets.UTF_8)).isEqualTo(expected)
        assertThat(diagnostics.single().code).isEqualTo("mobile-input.native-identifier-invalid")
    }

    @Test
    fun `the walkthrough retracts a deleted record under its export context`() {
        val context = HealthConnectTestFixtures.context()
        val conversion = HealthConnectTestFixtures.converted(HealthConnectFixtureRecords.steps(), context)

        val retraction = HealthConnectWalkthrough.retract(
            id = conversion.source.id,
            context = context,
            nextContext = HealthConnectTestFixtures.context(sequence = 2),
            sourceRecord = conversion.identifiers.sourceRecord,
        )

        assertThat(retraction.targets.map { it.identifier }).containsExactly(conversion.identifiers.primaryOutput)
        assertThat(ExchangeGraph.parse(ExchangeGraphKind.RETRACTION, retraction.graph.json))
            .isInstanceOf(ExchangeGraphParseResult.Valid::class.java)
    }
}
