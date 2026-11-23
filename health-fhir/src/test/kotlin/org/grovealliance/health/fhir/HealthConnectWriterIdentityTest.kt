//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.IdentifierSystem
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectWriterIdentityTest {
    private val start = Instant.parse("2026-01-01T08:00:00Z")
    private val instant = Instant.parse("2026-01-01T12:30:00Z")
    private val otherSystem = IdentifierSystem.create(rawValue = "https://other.example.org/fhir/NamingSystem/android-package").getOrThrow()

    @Test
    fun `names the writer identically on every conversion under one system`() {
        // given
        val record = weight(metadata = MetadataFixtures.create(clientRecordId = "scale-entry-7"))

        // when
        val first = writerIdentity(json = export(record = record, system = ExchangeProducerFixtures.writerApplicationSystem))
        val second = writerIdentity(json = export(record = record, system = ExchangeProducerFixtures.writerApplicationSystem))

        // then
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `names the writer differently under another deployment system`() {
        // given
        val record = weight(metadata = MetadataFixtures.create(clientRecordId = "scale-entry-7"))

        // when
        val deployment = writerIdentity(json = export(record = record, system = ExchangeProducerFixtures.writerApplicationSystem))
        val other = writerIdentity(json = export(record = record, system = otherSystem))

        // then
        assertThat(other).isNotEqualTo(deployment)
    }

    @Test
    fun `carries a client record version of zero`() {
        // when
        val json = export(
            record = weight(metadata = MetadataFixtures.create(clientRecordId = "scale-entry-7", clientRecordVersion = 0)),
            system = ExchangeProducerFixtures.writerApplicationSystem,
        )

        // then
        assertThat(json).contains("grove-writer-record-version\",\"valueString\":\"0\"")
    }

    @Test
    fun `names no writer without a client record id`() {
        // when
        val json = export(record = weight(metadata = MetadataFixtures.create()), system = ExchangeProducerFixtures.writerApplicationSystem)

        // then
        assertThat(json).doesNotContain("\"code\":\"writer-record\"")
    }

    @Test
    fun `refuses a blank client record id`() {
        // when
        val export = exporter(system = ExchangeProducerFixtures.writerApplicationSystem)
            .export(records = listOf(weight(metadata = MetadataFixtures.create(clientRecordId = " "))), instant = instant)
            .single()

        // then
        assertThat(export.outcome).isInstanceOf(HealthConnectExport.Outcome.Refused::class.java)
    }

    @Test
    fun `refuses the shared data-origin system as the writer application system`() {
        // given
        val shared = IdentifierSystem.create(rawValue = SHARED_PACKAGE_NAME_SYSTEM).getOrThrow()

        // when
        val result = create(system = shared)

        // then
        assertThat(result.exceptionOrNull())
            .isEqualTo(HealthConnectFhirExporter.ConfigurationError.ReservedWriterApplicationSystem(system = shared))
    }

    @Test
    fun `refuses a graph identity system as the writer application system`() {
        // given
        val reserved = ExchangeProducerFixtures.create().identityScope.systems.sourceRecord

        // when
        val result = create(system = reserved)

        // then
        assertThat(result.exceptionOrNull())
            .isEqualTo(HealthConnectFhirExporter.ConfigurationError.ReservedWriterApplicationSystem(system = reserved))
    }

    private fun weight(metadata: Metadata) = WeightRecord(
        time = start,
        zoneOffset = ZoneOffset.UTC,
        weight = Mass.kilograms(72.5),
        metadata = metadata,
    )

    private fun create(system: IdentifierSystem) = HealthConnectFhirExporter.create(
        producer = ExchangeProducerFixtures.create(),
        repositoryScope = ExchangeProducerFixtures.repositoryScope,
        options = HealthConnectFhirExporter.Options(
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
            writerApplicationSystem = system,
        ),
    )

    private fun exporter(system: IdentifierSystem) = create(system = system).getOrThrow()

    private fun export(record: WeightRecord, system: IdentifierSystem): String = String(
        requireNotNull(exporter(system = system).export(records = listOf(record), instant = instant).single().record).json,
        Charsets.UTF_8,
    )

    /**
     * The value of the identifier carrying the writer-record role in [json].
     */
    private fun writerIdentity(json: String): String {
        val role = json.indexOf("\"code\":\"writer-record\"")
        check(role >= 0) { "no writer-record identifier" }
        return Regex("\"value\":\"([^\"]+)\"").find(json, startIndex = role)!!.groupValues[1]
    }

    private companion object {
        const val SHARED_PACKAGE_NAME_SYSTEM = "https://grovealliance.org/fhir/health-connect/NamingSystem/android-package-name"
    }
}
