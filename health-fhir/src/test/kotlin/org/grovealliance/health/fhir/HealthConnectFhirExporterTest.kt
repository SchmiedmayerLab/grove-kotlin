//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMindfulnessSessionApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.ExchangeBundlePacker
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

@OptIn(InternalGroveFhirApi::class)
class HealthConnectFhirExporterTest {
    private val start = Instant.parse("2026-01-01T08:00:00Z")
    private val instant = Instant.parse("2026-01-01T12:30:00Z")
    private val offset = ZoneOffset.ofHours(-8)
    private val exporter = HealthConnectFhirExporter.create(
        producer = ExchangeProducerFixtures.create(),
        repositoryScope = ExchangeProducerFixtures.repositoryScope,
        options = HealthConnectFhirExporter.Options(
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
            writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
        ),
    ).getOrThrow()

    @Test
    fun `converts a steps record into one validated record`() {
        // given
        val record = steps()

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(export.source).isEqualTo(HealthConnectExport.Source(recordId = "record-1", recordType = "StepsRecord"))
        assertThat(json).contains(
            "\"profile\":[\"https://grovealliance.org/fhir/mobile/StructureDefinition/grove-mobile-step-count\"," +
                "\"https://grovealliance.org/fhir/health-connect/StructureDefinition/health-connect-observation\"]",
        )
        assertThat(json).contains("\"effectivePeriod\":{\"end\":\"2026-01-01T01:00:00-08:00\",\"start\":\"2026-01-01T00:00:00-08:00\"}")
        assertThat(json).contains("\"issued\":\"2026-01-01T12:00:00Z\"")
        assertThat(json).contains(
            "\"valueQuantity\":{\"code\":\"{steps}\",\"system\":\"http://unitsofmeasure.org\",\"unit\":\"steps\",\"value\":1200}",
        )
        assertThat(json).contains(
            "{\"url\":\"https://grovealliance.org/fhir/health-connect/StructureDefinition/health-connect-record-type\"," +
                "\"valueCode\":\"StepsRecord\"}",
        )
        assertThat(export.warnings).isEmpty()
    }

    @Test
    fun `names the entering application and the recording watch`() {
        // when
        val export = exporter.export(records = listOf(steps()), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains(
            "\"agent\":[{\"type\":{\"coding\":[{\"code\":\"enterer\",\"display\":\"Enterer\"," +
                "\"system\":\"http://terminology.hl7.org/CodeSystem/provenance-participant-type\"}]}," +
                "\"who\":{\"identifier\":{\"system\":\"https://grovealliance.org/fhir/health-connect/NamingSystem/android-package-name\"," +
                "\"value\":\"com.example.wearable\"},\"type\":\"Device\"}}]",
        )
        assertThat(json).contains("\"manufacturer\":\"Example Devices\",\"meta\"")
        assertThat(json).contains("\"modelNumber\":\"Study Watch 2\"")
        assertThat(json).contains("\"type\":{\"text\":\"Watch\"}")
        assertThat(json).contains("\"valueCoding\":{\"code\":\"automatically-recorded\"")
    }

    @Test
    fun `converts every heart rate sample, numbering samples at the same instant`() {
        // given
        val record = HeartRateRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(60),
            endZoneOffset = offset,
            samples = listOf(
                HeartRateRecord.Sample(time = start, beatsPerMinute = 72),
                HeartRateRecord.Sample(time = start, beatsPerMinute = 74),
                HeartRateRecord.Sample(time = start.plusSeconds(30), beatsPerMinute = 76),
            ),
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val exchangeRecord = requireNotNull(export.record)
        val json = json(record = exchangeRecord)
        assertThat(exchangeRecord.outputFullUrls).hasSize(3)
        assertThat(json).contains("\"effectiveDateTime\":\"2026-01-01T00:00:30-08:00\"")
        assertThat(json).contains("\"code\":\"8867-4\",\"display\":\"Heart rate\"")
    }

    @Test
    fun `states a height in exact centimeters`() {
        // given
        val record = HeightRecord(
            time = start,
            zoneOffset = offset,
            height = Length.meters(1.75),
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record)))
            .contains("\"code\":\"cm\",\"system\":\"http://unitsofmeasure.org\",\"unit\":\"cm\",\"value\":175}")
    }

    @Test
    fun `carries the writer's own record identity and version on the output`() {
        // given
        val record = weight(metadata = MetadataFixtures.create(clientRecordId = "scale-entry-7", clientRecordVersion = 3))

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains("\"code\":\"writer-record\"")
        assertThat(json).contains(
            "{\"url\":\"https://grovealliance.org/fhir/mobile/StructureDefinition/grove-writer-record-version\"," +
                "\"valueString\":\"3\"}",
        )
    }

    @Test
    fun `warns when the record states no offset`() {
        // given
        val record = WeightRecord(
            time = start,
            zoneOffset = null,
            weight = Mass.kilograms(72.5),
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record))).contains("\"effectiveDateTime\":\"2026-01-01T08:00:00Z\"")
        assertThat(export.warnings.map { it.code to it.location })
            .containsExactly("mobile-omission.source-offset" to "Observation.effectiveDateTime")
    }

    @Test
    fun `converts the same record into the same bytes`() {
        // when
        val first = exporter.export(records = listOf(steps()), instant = instant).single()
        val second = exporter.export(records = listOf(steps()), instant = instant).single()

        // then
        assertThat(requireNotNull(first.record).json).isEqualTo(requireNotNull(second.record).json)
    }

    @Test
    fun `packs records of several types into one validated bundle`() {
        // given
        val records = listOf(steps(), weight(metadata = MetadataFixtures.create(id = "record-2")))
        val packer = ExchangeBundlePacker(kind = ExchangeRecord.Kind.ACTIVE)

        // when
        exporter.export(records = records, instant = instant).forEach { packer.insert(record = requireNotNull(it.record)).getOrThrow() }

        // then
        assertThat(packer.bundle(packedAt = instant).isSuccess).isTrue()
    }

    @Test
    fun `refuses a record Health Connect never stored`() {
        // given
        val record = steps(metadata = Metadata.autoRecorded(device = MetadataFixtures.watch))

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val diagnostic = (export.outcome as HealthConnectExport.Outcome.Refused).error.diagnostic
        assertThat(diagnostic.code to diagnostic.location).isEqualTo("mobile-input.required-metadata-missing" to "Record.metadata.id")
    }

    @Test
    fun `refuses a conversion before the record's last modification`() {
        // when
        val export = exporter.export(records = listOf(steps()), instant = Instant.parse("2026-01-01T11:00:00Z")).single()

        // then
        val diagnostic = (export.outcome as HealthConnectExport.Outcome.Refused).error.diagnostic
        assertThat(diagnostic.code).isEqualTo("mobile-input.conversion-instant-precedes-source-version")
    }

    @Test
    fun `refuses a sleep session without stages`() {
        // given
        val record = SleepSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(28_800),
            endZoneOffset = offset,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val diagnostic = (export.outcome as HealthConnectExport.Outcome.Refused).error.diagnostic
        assertThat(diagnostic.code).isEqualTo("mobile-input.not-yet-convertible")
    }

    @Test
    fun `converts a blood pressure record into its two components with position and site`() {
        // given
        val record = BloodPressureRecord(
            time = start,
            zoneOffset = offset,
            systolic = Pressure.millimetersOfMercury(118.0),
            diastolic = Pressure.millimetersOfMercury(78.0),
            bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
            measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains("\"code\":\"8480-6\"")
        assertThat(json).contains("\"code\":\"8462-4\"")
        assertThat(json).contains("\"value\":118}")
        assertThat(json).contains("\"url\":\"http://hl7.org/fhir/StructureDefinition/observation-bodyPosition\"")
        assertThat(json).contains("\"bodySite\":{\"coding\":[{")
    }

    @Test
    fun `converts a capillary glucose record into its observation and specimen`() {
        // given
        val record = glucose(specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD)

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val exchangeRecord = requireNotNull(export.record)
        val json = json(record = exchangeRecord)
        assertThat(exchangeRecord.outputFullUrls).hasSize(2)
        assertThat(json).contains(
            "\"profile\":[\"https://grovealliance.org/fhir/health-connect/StructureDefinition/health-connect-capillary-blood-glucose\"]",
        )
        assertThat(json).contains("\"specimen\":{\"reference\":\"${exchangeRecord.outputFullUrls[1]}\"}")
        assertThat(json).contains("\"type\":{\"coding\":[{\"code\":\"122554006\",\"display\":\"Capillary blood specimen\"")
        assertThat(json).contains("\"url\":\"relationToMeal\",\"valueCoding\":{\"code\":\"RELATION_TO_MEAL_FASTING\"")
        assertThat(json).contains("\"value\":96}")
    }

    @Test
    fun `refuses a glucose record whose specimen the contract rejects`() {
        // given
        val record = glucose(specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_TEARS)

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val diagnostic = (export.outcome as HealthConnectExport.Outcome.Refused).error.diagnostic
        assertThat(diagnostic.code to diagnostic.location)
            .isEqualTo("mobile-input.unsupported-source-value" to "BloodGlucoseRecord.specimenSource")
    }

    @Test
    fun `states where a body temperature was measured`() {
        // given
        val record = BodyTemperatureRecord(
            time = start,
            zoneOffset = offset,
            temperature = Temperature.celsius(36.8),
            measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_ARMPIT,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains("\"bodySite\":{\"coding\":[{\"code\":\"422543003\"")
        assertThat(json).contains("\"value\":36.8}")
    }

    @Test
    fun `states how a VO2 max was measured`() {
        // given
        val record = Vo2MaxRecord(
            time = start,
            zoneOffset = offset,
            vo2MillilitersPerMinuteKilogram = 42.5,
            measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_COOPER_TEST,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record)))
            .contains("\"method\":{\"coding\":[{\"code\":\"MEASUREMENT_METHOD_COOPER_TEST\"")
    }

    @Test
    fun `converts a mindfulness session with its type, title and note when text is retained`() {
        // given
        val retaining = HealthConnectFhirExporter.create(
            producer = ExchangeProducerFixtures.create(),
            repositoryScope = ExchangeProducerFixtures.repositoryScope,
            options = HealthConnectFhirExporter.Options(
                userText = HealthConnectFhirExporter.Options.UserText.RETAIN,
                writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
            ),
        ).getOrThrow()

        // when
        val export = retaining.export(records = listOf(mindfulness()), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains("\"code\":\"min\",\"system\":\"http://unitsofmeasure.org\",\"unit\":\"min\",\"value\":12}")
        assertThat(json).contains("\"method\":{\"coding\":[{\"code\":\"MINDFULNESS_SESSION_TYPE_MEDITATION\"")
        assertThat(json).contains("\"valueString\":\"Morning practice\"")
        assertThat(json).contains("\"note\":[{\"text\":\"Guided attention exercise.\"}]")
    }

    @Test
    fun `omits a mindfulness session's title and note unless text is retained`() {
        // when
        val export = exporter.export(records = listOf(mindfulness()), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).doesNotContain("Morning practice")
        assertThat(json).doesNotContain("Guided attention exercise.")
    }

    @Test
    fun `converts every sleep stage with its shared and exact stage`() {
        // given
        val record = SleepSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(7_200),
            endZoneOffset = offset,
            stages = listOf(
                SleepSessionRecord.Stage(
                    startTime = start,
                    endTime = start.plusSeconds(3_600),
                    stage = SleepSessionRecord.STAGE_TYPE_LIGHT,
                ),
                SleepSessionRecord.Stage(
                    startTime = start.plusSeconds(3_600),
                    endTime = start.plusSeconds(7_200),
                    stage = SleepSessionRecord.STAGE_TYPE_REM,
                ),
            ),
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val exchangeRecord = requireNotNull(export.record)
        val json = json(record = exchangeRecord)
        assertThat(exchangeRecord.outputFullUrls).hasSize(2)
        assertThat(json).contains(
            "{\"code\":\"light\",\"display\":\"Light sleep\"," +
                "\"system\":\"https://grovealliance.org/fhir/mobile/CodeSystem/grove-sleep-stage\"}," +
                "{\"code\":\"STAGE_TYPE_LIGHT\",\"display\":\"Light sleep\"",
        )
        assertThat(json).contains("\"code\":\"activity\"")
        assertThat(json).doesNotContain("93832-4")
    }

    @Test
    fun `converts an exercise session into its workout, segments and laps`() {
        // given
        val record = ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(3_600),
            endZoneOffset = offset,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            segments = listOf(
                ExerciseSegment(
                    startTime = start,
                    endTime = start.plusSeconds(600),
                    segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
                ),
                ExerciseSegment(
                    startTime = start.plusSeconds(600),
                    endTime = start.plusSeconds(900),
                    segmentType = ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST,
                ),
            ),
            laps = listOf(ExerciseLap(startTime = start.plusSeconds(900), endTime = start.plusSeconds(1_200))),
            metadata = MetadataFixtures.create(clientRecordId = "session-1"),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val exchangeRecord = requireNotNull(export.record)
        val json = json(record = exchangeRecord)
        assertThat(exchangeRecord.outputFullUrls).hasSize(4)
        assertThat(json).contains("\"code\":\"strength-training\",\"display\":\"Strength training\"")
        assertThat(json).contains("\"code\":\"EXERCISE_TYPE_STRENGTH_TRAINING\"")
        assertThat(json).contains("\"code\":\"EXERCISE_SEGMENT_TYPE_BENCH_PRESS\"")
        assertThat(json).contains("\"code\":\"rest\",\"display\":\"Rest\"")
        assertThat(json).contains("\"code\":\"EXERCISE_LAP\"")
        assertThat(json).contains("\"hasMember\":[{")
        assertThat(json.split("\"code\":\"writer-record\"").size - 1).isEqualTo(4)
    }

    @Test
    fun `states the activity category of a step count`() {
        // when
        val export = exporter.export(records = listOf(steps()), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record))).contains("\"category\":[{\"coding\":[{\"code\":\"activity\"")
    }

    @Test
    fun `converts every nutrient a nutrition record states in its own unit`() {
        // given
        val record = NutritionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(1_800),
            endZoneOffset = offset,
            sodium = Mass.grams(0.5),
            energy = Energy.kilocalories(420.0),
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val exchangeRecord = requireNotNull(export.record)
        val json = json(record = exchangeRecord)
        assertThat(exchangeRecord.outputFullUrls).hasSize(2)
        assertThat(json).contains("\"code\":\"mg\",\"system\":\"http://unitsofmeasure.org\",\"unit\":\"mg\",\"value\":500}")
        assertThat(json).contains("\"value\":420}")
    }

    @Test
    fun `converts every skin temperature delta from its baseline`() {
        // given
        val record = SkinTemperatureRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(3_600),
            endZoneOffset = offset,
            baseline = Temperature.celsius(33.5),
            deltas = listOf(
                SkinTemperatureRecord.Delta(time = start, delta = TemperatureDelta.celsius(0.25)),
                SkinTemperatureRecord.Delta(time = start.plusSeconds(1_800), delta = TemperatureDelta.celsius(-0.5)),
            ),
            measurementLocation = SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains("\"value\":33.75}")
        assertThat(json).contains("\"value\":33}")
        assertThat(json).contains("\"code\":\"8205005\"")
    }

    @Test
    fun `converts a cervical mucus record with its sensation`() {
        // given
        val record = CervicalMucusRecord(
            time = start,
            zoneOffset = offset,
            appearance = CervicalMucusRecord.APPEARANCE_EGG_WHITE,
            sensation = CervicalMucusRecord.SENSATION_MEDIUM,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        val json = json(record = requireNotNull(export.record))
        assertThat(json).contains("\"code\":\"egg-white\"")
        assertThat(json).contains("\"code\":\"APPEARANCE_EGG_WHITE\"")
        assertThat(json).contains("\"code\":\"cervical-mucus-sensation\"")
        assertThat(json).contains("\"code\":\"SENSATION_MEDIUM\"")
    }

    @Test
    fun `converts an ovulation test into its shared result`() {
        // given
        val record = OvulationTestRecord(
            time = start,
            zoneOffset = offset,
            result = OvulationTestRecord.RESULT_POSITIVE,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record))).contains("\"code\":\"luteinizing-hormone-surge\"")
    }

    @Test
    fun `converts a menstruation period over its interval`() {
        // given
        val record = MenstruationPeriodRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(345_600),
            endZoneOffset = offset,
            metadata = MetadataFixtures.create(),
        )

        // when
        val export = exporter.export(records = listOf(record), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record))).contains("\"code\":\"present\",\"display\":\"Present\"")
    }

    @Test
    fun `discloses the Health Connect id on the primary output when a system is named`() {
        // given
        val disclosing = HealthConnectFhirExporter.create(
            producer = ExchangeProducerFixtures.create(),
            repositoryScope = ExchangeProducerFixtures.repositoryScope,
            options = HealthConnectFhirExporter.Options(
                userText = HealthConnectFhirExporter.Options.UserText.OMIT,
                writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
                nativeIdentifierSystem = IdentifierSystem.create(rawValue = NATIVE_RECORD_SYSTEM).getOrThrow(),
            ),
        ).getOrThrow()

        // when
        val export = disclosing.export(records = listOf(steps()), instant = instant).single()

        // then
        assertThat(json(record = requireNotNull(export.record)))
            .contains("{\"system\":\"$NATIVE_RECORD_SYSTEM\",\"value\":\"record-1\"}")
    }

    @Test
    fun `refuses to disclose the Health Connect id under a graph identity system`() {
        // given
        val producer = ExchangeProducerFixtures.create()
        val reserved = producer.identityScope.systems.sourceRecord

        // when
        val result = HealthConnectFhirExporter.create(
            producer = producer,
            repositoryScope = ExchangeProducerFixtures.repositoryScope,
            options = HealthConnectFhirExporter.Options(
                userText = HealthConnectFhirExporter.Options.UserText.OMIT,
                writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
                nativeIdentifierSystem = reserved,
            ),
        )

        // then
        assertThat(result.exceptionOrNull())
            .isEqualTo(HealthConnectFhirExporter.ConfigurationError.ReservedNativeIdentifierSystem(system = reserved))
    }

    private fun steps(metadata: Metadata = MetadataFixtures.create()) = StepsRecord(
        startTime = start,
        startZoneOffset = offset,
        endTime = start.plusSeconds(3_600),
        endZoneOffset = offset,
        count = 1_200,
        metadata = metadata,
    )

    private fun weight(metadata: Metadata) = WeightRecord(
        time = start,
        zoneOffset = offset,
        weight = Mass.kilograms(72.5),
        metadata = metadata,
    )

    private fun glucose(specimenSource: Int) = BloodGlucoseRecord(
        time = start,
        zoneOffset = offset,
        level = BloodGlucose.milligramsPerDeciliter(96.0),
        specimenSource = specimenSource,
        mealType = MealType.MEAL_TYPE_BREAKFAST,
        relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING,
        metadata = MetadataFixtures.create(),
    )

    private fun mindfulness() = MindfulnessSessionRecord(
        startTime = start,
        startZoneOffset = offset,
        endTime = start.plusSeconds(720),
        endZoneOffset = offset,
        mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
        title = "Morning practice",
        notes = "Guided attention exercise.",
        metadata = MetadataFixtures.create(),
    )

    private companion object {
        const val NATIVE_RECORD_SYSTEM = "https://study.example.org/fhir/identifiers/health-connect-record"
    }

    private fun json(record: ExchangeRecord) = String(record.json, Charsets.UTF_8)
}
