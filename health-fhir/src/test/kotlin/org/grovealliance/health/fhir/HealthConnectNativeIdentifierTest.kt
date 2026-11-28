//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.LosslessJsonValue
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonArray
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonString
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class HealthConnectNativeIdentifierTest {
    private val start = Instant.parse("2026-01-01T08:00:00Z")
    private val instant = Instant.parse("2026-01-01T12:30:00Z")
    private val offset = ZoneOffset.UTC
    private val exporter = HealthConnectFhirExporter.create(
        producer = ExchangeProducerFixtures.create(),
        repositoryScope = ExchangeProducerFixtures.repositoryScope,
        options = HealthConnectFhirExporter.Options(
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
            writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
            nativeIdentifierSystem = IdentifierSystem.create(rawValue = NATIVE_RECORD_SYSTEM).getOrThrow(),
        ),
    ).getOrThrow()

    @Test
    fun `discloses the id once on the output of a record converted exactly once`() {
        // when
        val record = export(
            record = StepsRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = start.plusSeconds(3_600),
                endZoneOffset = offset,
                count = 1_200,
                metadata = MetadataFixtures.create(),
            ),
        )

        // then
        assertThat(disclosures(record = record)).isEqualTo(1)
    }

    @Test
    fun `omits the id from a heart rate series of two samples`() {
        // when
        val record = export(record = heartRate(beats = listOf(65, 67)))

        // then
        assertThat(disclosures(record = record)).isEqualTo(0)
    }

    @Test
    fun `omits the id from a heart rate series of one sample`() {
        // when
        val record = export(record = heartRate(beats = listOf(65)))

        // then
        assertThat(disclosures(record = record)).isEqualTo(0)
    }

    @Test
    fun `omits the id from a nutrition record converted per present field`() {
        // when
        val record = export(
            record = NutritionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = start.plusSeconds(1_800),
                endZoneOffset = offset,
                energy = Energy.kilocalories(500.0),
                protein = Mass.grams(20.0),
                metadata = MetadataFixtures.create(),
            ),
        )

        // then
        assertThat(disclosures(record = record)).isEqualTo(0)
    }

    @Test
    fun `discloses the id on the glucose observation and not on its specimen`() {
        // when
        val record = export(
            record = BloodGlucoseRecord(
                time = start,
                zoneOffset = offset,
                level = BloodGlucose.milligramsPerDeciliter(96.0),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                mealType = 0,
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING,
                metadata = MetadataFixtures.create(),
            ),
        )

        // then
        assertThat(disclosures(record = record)).isEqualTo(1)
        assertThat(identifierSystems(record = record, resourceType = "Specimen")).doesNotContain(NATIVE_RECORD_SYSTEM)
    }

    private fun heartRate(beats: List<Long>) = HeartRateRecord(
        startTime = start,
        startZoneOffset = offset,
        endTime = start.plusSeconds(60L * beats.size),
        endZoneOffset = offset,
        samples = beats.mapIndexed { index, bpm ->
            HeartRateRecord.Sample(
                time = start.plusSeconds(60L * index),
                beatsPerMinute = bpm,
            )
        },
        metadata = MetadataFixtures.create(),
    )

    private fun export(record: Record): ExchangeRecord =
        requireNotNull(exporter.export(records = listOf(record), instant = instant).single().record)

    private fun disclosures(record: ExchangeRecord): Int =
        String(record.json, Charsets.UTF_8).split("{\"system\":\"$NATIVE_RECORD_SYSTEM\",\"value\":\"record-1\"}").size - 1

    /**
     * The identifier systems every entry resource of [resourceType] in [record] states.
     */
    private fun identifierSystems(record: ExchangeRecord, resourceType: String): List<String> {
        val bundle = LosslessJsonValue.parse(bytes = record.json).getOrThrow() as JsonObject
        return (bundle.members["entry"] as JsonArray).elements
            .map { (it as JsonObject).members["resource"] as JsonObject }
            .filter { (it.members["resourceType"] as? JsonString)?.value == resourceType }
            .flatMap { (it.members["identifier"] as? JsonArray)?.elements.orEmpty() }
            .mapNotNull { ((it as JsonObject).members["system"] as? JsonString)?.value }
    }

    private companion object {
        const val NATIVE_RECORD_SYSTEM = "https://study.example.org/fhir/identifiers/health-connect-record"
    }
}
