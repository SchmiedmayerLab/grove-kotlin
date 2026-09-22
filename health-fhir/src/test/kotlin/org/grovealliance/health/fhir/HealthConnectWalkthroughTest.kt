//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.ExchangeGraphDiagnostic
import org.junit.Test

/** Runs the README walkthrough's conversion block; the installation and context blocks need a device and only compile here. */
class HealthConnectWalkthroughTest {
    @Test
    fun `the walkthrough uploads the graph bytes of a converted record and logs a refusal`() {
        val uploads = mutableListOf<ByteArray>()
        val diagnostics = mutableListOf<ExchangeGraphDiagnostic>()
        val context = HealthConnectTestFixtures.context()

        HealthConnectWalkthrough.convert(HealthConnectFixtureRecords.steps(), context, uploads::add, diagnostics::add)
        HealthConnectWalkthrough.convert(HealthConnectFixtureRecords.steps(""), context, uploads::add, diagnostics::add)

        val expected = HealthConnectTestFixtures.converted(HealthConnectFixtureRecords.steps()).graph.json
        assertThat(uploads.single().toString(Charsets.UTF_8)).isEqualTo(expected)
        assertThat(diagnostics.single().code).isEqualTo("mobile-input.native-identifier-invalid")
    }
}
