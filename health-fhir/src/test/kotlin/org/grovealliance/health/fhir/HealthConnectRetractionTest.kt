//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.PlannedExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.ExchangeBundlePacker
import org.grovealliance.fhir.contract.ExchangeRecord
import org.junit.Test
import java.time.Instant
import kotlin.reflect.KClass

class HealthConnectRetractionTest {
    private val detectedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val instant = Instant.parse("2026-01-01T12:30:00Z")
    private val exporter = HealthConnectFhirExporter.create(
        producer = ExchangeProducerFixtures.create(),
        repositoryScope = ExchangeProducerFixtures.repositoryScope,
        options = HealthConnectFhirExporter.Options(
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
            writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
        ),
    ).getOrThrow()

    @Test
    fun `retracts a deleted record's whole source record`() {
        // given
        val deletion = deletion(recordType = StepsRecord::class)

        // when
        val retraction = exporter.retract(deletions = listOf(deletion), instant = instant).single()

        // then
        val record = requireNotNull(retraction.record)
        val json = String(record.json, Charsets.UTF_8)
        assertThat(record.kind).isEqualTo(ExchangeRecord.Kind.RETRACTION)
        assertThat(json).contains("\"valueCode\":\"source-record\"")
        assertThat(json).contains("\"occurredPeriod\":{\"end\":\"2026-01-01T12:00:00Z\",\"start\":\"2026-01-01T11:00:00Z\"}")
    }

    @Test
    fun `retracts the same deletion with the same key`() {
        // when
        val first = exporter.retract(deletions = listOf(deletion(recordType = StepsRecord::class)), instant = instant).single()
        val second = exporter.retract(deletions = listOf(deletion(recordType = StepsRecord::class)), instant = instant).single()

        // then
        assertThat(requireNotNull(first.record).version).isEqualTo(requireNotNull(second.record).version)
    }

    @Test
    fun `packs retractions into one validated retraction bundle`() {
        // given
        val deletions = listOf(deletion(recordType = StepsRecord::class), deletion(recordType = StepsRecord::class, recordId = "record-2"))
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.RETRACTION)

        // when
        exporter.retract(
            deletions = deletions,
            instant = instant,
        ).forEach { packer.insert(record = requireNotNull(it.record)).getOrThrow() }

        // then
        assertThat(packer.bundle(packedAt = instant).isSuccess).isTrue()
    }

    @Test
    fun `retracts nothing for a record type the exporter never converts`() {
        // when
        val retraction = exporter.retract(
            deletions = listOf(deletion(recordType = PlannedExerciseSessionRecord::class)),
            instant = instant,
        ).single()

        // then
        assertThat(retraction.outcome).isEqualTo(HealthConnectRetraction.Outcome.NothingToRetract)
    }

    private fun deletion(recordType: KClass<out Record>, recordId: String = "record-1") = HealthConnectDeletion(
        recordId = recordId,
        recordType = recordType,
        deletedAfter = Instant.parse("2026-01-01T11:00:00Z"),
        detectedAt = detectedAt,
    )
}
