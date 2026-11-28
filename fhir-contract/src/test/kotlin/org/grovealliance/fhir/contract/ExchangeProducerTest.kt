//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.internal.ExchangeTestScope
import org.junit.Test

class ExchangeProducerTest {
    private val application = ApplicationDevice.create(name = "App", packageName = "org.example.app", version = "1").getOrThrow()
    private val host = HostDevice.create(operatingSystemVersion = "16").getOrThrow()

    @Test
    fun `refuses a subject numbered in a graph identity system`() {
        // given
        val subject = Subject.Logical(
            identifier = BusinessIdentifier.create(
                system = ExchangeTestScope.identityScope.systems.sourceRecord,
                value = "participant",
            ).getOrThrow(),
        )

        // when
        val result = ExchangeProducer.create(
            identityScope = ExchangeTestScope.identityScope,
            subject = subject,
            application = application,
            host = host,
        )

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(
            ExchangeProducer.ConfigurationError.ReservedSubjectIdentifierSystem(
                system = ExchangeTestScope.identityScope.systems.sourceRecord,
            ),
        )
    }

    @Test
    fun `refuses a bundled patient that states a second identifier`() {
        // given
        val patient = Patient(identifier = listOf(Identifier(system = "https://hospital.example.org/mrn", value = "123")))

        // when
        val result = ExchangeProducer.create(
            identityScope = ExchangeTestScope.identityScope,
            subject = Subject.Bundled(identifier = ExchangeTestScope.subject, patient = patient),
            application = application,
            host = host,
        )

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(ExchangeProducer.ConfigurationError.BundledPatientIdentifiers)
    }

    @Test
    fun `refuses blank and malformed application facts`() {
        // when
        val results = listOf(
            ApplicationDevice.create(name = " ", packageName = "org.example", version = "1"),
            ApplicationDevice.create(name = "App", packageName = "org..example", version = "1"),
            ApplicationDevice.create(name = "App", packageName = "org.example", version = "​"),
        )

        // then
        assertThat(results.map { it.exceptionOrNull() }).containsExactly(
            ApplicationDevice.ValidationError.BlankName,
            ApplicationDevice.ValidationError.InvalidPackageName(packageName = "org..example"),
            ApplicationDevice.ValidationError.BlankVersion,
        ).inOrder()
    }
}
