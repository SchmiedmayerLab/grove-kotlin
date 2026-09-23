//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import java.time.Instant
import java.time.ZoneOffset

/** One semantic vector of the mobile-semantics corpus and the record that projects onto it. */
internal data class SemanticVectorRecord(val id: String, val profile: String, val record: Record)

/** The deterministic source records the conformance lane converts. */
internal object HealthConnectFixtureRecords {
    private val fixtures = HealthConnectTestFixtures
    val pacific: ZoneOffset = ZoneOffset.ofHours(-7)
    val instant: Instant = Instant.parse("2026-08-19T16:00:00Z")
    val end: Instant = instant.plusSeconds(3_600)
    val sessionStart: Instant = Instant.parse("2026-08-19T08:00:00Z")

    fun steps(id: String = "fixture-step", count: Long = 1): StepsRecord = StepsRecord(
        startTime = instant,
        startZoneOffset = ZoneOffset.UTC,
        endTime = end,
        endZoneOffset = ZoneOffset.UTC,
        count = count,
        metadata = fixtures.metadata(id),
    )

    fun weight(id: String = "fixture-weight"): WeightRecord = WeightRecord(
        time = instant,
        zoneOffset = ZoneOffset.UTC,
        weight = Mass.kilograms(72.5),
        metadata = fixtures.metadata(id),
    )

    fun twoHeartRateSamples(): List<HeartRateRecord.Sample> = listOf(
        HeartRateRecord.Sample(Instant.parse("2026-08-19T16:00:00Z"), 72),
        HeartRateRecord.Sample(Instant.parse("2026-08-19T16:00:30Z"), 74),
    )

    fun heartRate(
        id: String = "heart-record",
        samples: List<HeartRateRecord.Sample> = twoHeartRateSamples(),
        lastModified: Instant = fixtures.lastModified,
    ): HeartRateRecord = HeartRateRecord(
        startTime = instant,
        startZoneOffset = pacific,
        endTime = instant.plusSeconds(60),
        endZoneOffset = pacific,
        samples = samples,
        metadata = fixtures.metadata(id, lastModified),
    )

    fun bloodGlucose(id: String, specimenSource: Int): BloodGlucoseRecord = BloodGlucoseRecord(
        time = instant,
        zoneOffset = ZoneOffset.UTC,
        metadata = fixtures.metadata(id),
        level = BloodGlucose.milligramsPerDeciliter(95.5),
        specimenSource = specimenSource,
        mealType = MealType.MEAL_TYPE_BREAKFAST,
        relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL,
    )

    fun bloodPressure(id: String = "fixture-blood-pressure"): BloodPressureRecord = BloodPressureRecord(
        time = instant,
        zoneOffset = ZoneOffset.UTC,
        metadata = fixtures.metadata(id),
        systolic = Pressure.millimetersOfMercury(120.0),
        diastolic = Pressure.millimetersOfMercury(80.0),
        bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
        measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
    )

    fun sleep(id: String = "fixture-sleep", stages: List<SleepSessionRecord.Stage> = everyStage()): SleepSessionRecord =
        SleepSessionRecord(
            startTime = sessionStart,
            startZoneOffset = pacific,
            endTime = sessionStart.plusSeconds(8 * 3_600L),
            endZoneOffset = pacific,
            metadata = fixtures.metadata(id),
            title = "Night sleep",
            notes = "Participant-reported note",
            stages = stages,
        )

    fun everyStage(): List<SleepSessionRecord.Stage> = listOf(
        SleepSessionRecord.STAGE_TYPE_UNKNOWN,
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_SLEEPING,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        SleepSessionRecord.STAGE_TYPE_LIGHT,
        SleepSessionRecord.STAGE_TYPE_DEEP,
        SleepSessionRecord.STAGE_TYPE_REM,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    ).mapIndexed { index, stage ->
        SleepSessionRecord.Stage(sessionStart.plusSeconds(index * 3_600L), sessionStart.plusSeconds((index + 1) * 3_600L), stage)
    }

    fun exercise(
        id: String = "fixture-exercise",
        segments: List<ExerciseSegment> = emptyList(),
        laps: List<ExerciseLap> = emptyList(),
        metadata: Metadata = fixtures.metadata(id),
    ): ExerciseSessionRecord = ExerciseSessionRecord(
        startTime = sessionStart,
        startZoneOffset = ZoneOffset.UTC,
        endTime = sessionStart.plusSeconds(3_600),
        endZoneOffset = ZoneOffset.UTC,
        metadata = metadata,
        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        title = "Morning run",
        notes = null,
        segments = segments,
        laps = laps,
    )

    fun nutrition(id: String = "fixture-nutrition"): NutritionRecord = NutritionRecord(
        startTime = instant,
        startZoneOffset = ZoneOffset.UTC,
        endTime = end,
        endZoneOffset = ZoneOffset.UTC,
        metadata = fixtures.metadata(id),
        energy = Energy.kilocalories(520.0),
        protein = Mass.grams(31.5),
        sodium = Mass.milligrams(640.0),
        name = "Lunch",
        mealType = MealType.MEAL_TYPE_LUNCH,
    )

    /** The records the lane exports as complete graphs, in export order. */
    fun conformanceRecords(): List<Pair<String, Record>> = listOf(
        "active-energy" to ActiveCaloriesBurnedRecord(
            startTime = instant,
            startZoneOffset = pacific,
            endTime = end,
            endZoneOffset = pacific,
            energy = Energy.kilocalories(412.5),
            metadata = fixtures.metadata("fixture-active-energy"),
        ),
        "basal-body-temperature" to BasalBodyTemperatureRecord(
            time = instant,
            zoneOffset = ZoneOffset.UTC,
            metadata = fixtures.metadata("fixture-basal-body-temperature"),
            temperature = Temperature.celsius(36.4),
            measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH,
        ),
    ) + glucoseRecords() + otherConformanceRecords()

    private fun glucoseRecords(): List<Pair<String, Record>> = listOf(
        "glucose-whole-blood" to BloodGlucoseRecord.SPECIMEN_SOURCE_WHOLE_BLOOD,
        "glucose-capillary" to BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
        "glucose-plasma" to BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA,
        "glucose-serum" to BloodGlucoseRecord.SPECIMEN_SOURCE_SERUM,
        "glucose-interstitial" to BloodGlucoseRecord.SPECIMEN_SOURCE_INTERSTITIAL_FLUID,
    ).map { (name, specimen) -> name to bloodGlucose("fixture-$name", specimen) }

    private fun otherConformanceRecords(): List<Pair<String, Record>> {
        return listOf(
            "blood-pressure" to bloodPressure(),
            "body-temperature" to BodyTemperatureRecord(
                time = instant,
                zoneOffset = ZoneOffset.UTC,
                metadata = fixtures.metadata("fixture-body-temperature"),
                temperature = Temperature.celsius(37.1),
                measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR,
            ),
            "distance" to DistanceRecord(
                startTime = instant,
                startZoneOffset = ZoneOffset.UTC,
                endTime = end,
                endZoneOffset = ZoneOffset.UTC,
                distance = Length.kilometers(3.25),
                metadata = fixtures.metadata("fixture-distance"),
            ),
            "heart-rate" to heartRate(),
            "height" to HeightRecord(
                time = instant,
                zoneOffset = ZoneOffset.UTC,
                height = Length.meters(1.82),
                metadata = fixtures.metadata("fixture-height"),
            ),
            "oxygen-saturation" to OxygenSaturationRecord(
                time = instant,
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(98.2),
                metadata = fixtures.metadata("fixture-oxygen-saturation"),
            ),
            "respiratory-rate" to RespiratoryRateRecord(
                time = instant,
                zoneOffset = ZoneOffset.UTC,
                rate = 14.5,
                metadata = fixtures.metadata("fixture-respiratory-rate"),
            ),
            "sleep" to sleep(),
            "steps" to steps(),
            "weight" to weight(),
        )
    }

    /** The thirteen records whose primary outputs project exactly onto the mobile-semantics vectors. */
    fun semanticVectorRecords(): List<SemanticVectorRecord> =
        semanticSpans() + semanticBodyMeasurements() + semanticVitals() + semanticSleep()

    private fun semanticMetadata(id: String): Metadata = fixtures.metadata("semantic-$id", Instant.parse("2026-08-20T17:30:01Z"))

    private fun semanticSpans(): List<SemanticVectorRecord> = listOf(
        SemanticVectorRecord(
            "active-energy",
            HealthConnectContract.MOBILE_ACTIVE_ENERGY_PROFILE,
            ActiveCaloriesBurnedRecord(
                startTime = Instant.parse("2026-08-20T15:00:00Z"),
                startZoneOffset = pacific,
                endTime = Instant.parse("2026-08-20T16:00:00Z"),
                endZoneOffset = pacific,
                energy = Energy.kilocalories(312.5),
                metadata = semanticMetadata("active-energy"),
            ),
        ),
        SemanticVectorRecord(
            "distance",
            HealthConnectContract.MOBILE_DISTANCE_PROFILE,
            DistanceRecord(
                startTime = Instant.parse("2026-08-20T14:00:00Z"),
                startZoneOffset = pacific,
                endTime = Instant.parse("2026-08-20T14:30:00Z"),
                endZoneOffset = pacific,
                distance = Length.meters(4820.5),
                metadata = semanticMetadata("distance"),
            ),
        ),
        SemanticVectorRecord(
            "step-count",
            HealthConnectContract.MOBILE_STEP_COUNT_PROFILE,
            StepsRecord(
                startTime = Instant.parse("2026-08-20T15:00:00Z"),
                startZoneOffset = pacific,
                endTime = Instant.parse("2026-08-20T16:00:00Z"),
                endZoneOffset = pacific,
                count = 1042,
                metadata = semanticMetadata("step-count"),
            ),
        ),
    )

    private fun semanticBodyMeasurements(): List<SemanticVectorRecord> = listOf(
        SemanticVectorRecord(
            "basal-body-temperature",
            HealthConnectContract.MOBILE_BASAL_BODY_TEMPERATURE_PROFILE,
            BasalBodyTemperatureRecord(
                time = Instant.parse("2026-08-20T13:45:00Z"),
                zoneOffset = pacific,
                metadata = semanticMetadata("basal-body-temperature"),
                temperature = Temperature.celsius(36.52),
                measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_MOUTH,
            ),
        ),
        SemanticVectorRecord(
            "body-height",
            HealthConnectContract.MOBILE_BODY_HEIGHT_PROFILE,
            HeightRecord(
                time = Instant.parse("2026-08-20T15:15:00Z"),
                zoneOffset = pacific,
                height = Length.meters(1.712),
                metadata = semanticMetadata("body-height"),
            ),
        ),
        SemanticVectorRecord(
            "body-temperature",
            HealthConnectContract.MOBILE_BODY_TEMPERATURE_PROFILE,
            BodyTemperatureRecord(
                time = Instant.parse("2026-08-20T15:20:00Z"),
                zoneOffset = pacific,
                metadata = semanticMetadata("body-temperature"),
                temperature = Temperature.celsius(37.1),
                measurementLocation = BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_EAR,
            ),
        ),
        SemanticVectorRecord(
            "body-weight",
            HealthConnectContract.MOBILE_BODY_WEIGHT_PROFILE,
            WeightRecord(
                time = Instant.parse("2026-08-20T15:25:00Z"),
                zoneOffset = pacific,
                weight = Mass.kilograms(68.4),
                metadata = semanticMetadata("body-weight"),
            ),
        ),
    )

    private fun semanticVitals(): List<SemanticVectorRecord> = listOf(
        SemanticVectorRecord(
            "blood-pressure",
            HealthConnectContract.MOBILE_BLOOD_PRESSURE_PROFILE,
            BloodPressureRecord(
                time = Instant.parse("2026-08-20T15:10:00Z"),
                zoneOffset = pacific,
                metadata = semanticMetadata("blood-pressure"),
                systolic = Pressure.millimetersOfMercury(118.0),
                diastolic = Pressure.millimetersOfMercury(76.0),
                bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
            ),
        ),
        SemanticVectorRecord(
            "heart-rate",
            HealthConnectContract.MOBILE_HEART_RATE_PROFILE,
            HeartRateRecord(
                startTime = Instant.parse("2026-08-20T15:29:00Z"),
                startZoneOffset = pacific,
                endTime = Instant.parse("2026-08-20T15:31:00Z"),
                endZoneOffset = pacific,
                samples = listOf(HeartRateRecord.Sample(Instant.parse("2026-08-20T15:30:00.251Z"), 72)),
                metadata = semanticMetadata("heart-rate"),
            ),
        ),
        SemanticVectorRecord(
            "oxygen-saturation",
            HealthConnectContract.MOBILE_OXYGEN_SATURATION_PROFILE,
            OxygenSaturationRecord(
                time = Instant.parse("2026-08-20T15:35:00Z"),
                zoneOffset = pacific,
                percentage = Percentage(98.0),
                metadata = semanticMetadata("oxygen-saturation"),
            ),
        ),
        SemanticVectorRecord(
            "respiratory-rate",
            HealthConnectContract.MOBILE_RESPIRATORY_RATE_PROFILE,
            RespiratoryRateRecord(
                time = Instant.parse("2026-08-20T15:40:00Z"),
                zoneOffset = pacific,
                rate = 15.0,
                metadata = semanticMetadata("respiratory-rate"),
            ),
        ),
    )

    private fun semanticSleep(): List<SemanticVectorRecord> {
        val sleep = SleepSessionRecord(
            startTime = Instant.parse("2026-08-20T06:00:00Z"),
            startZoneOffset = pacific,
            endTime = Instant.parse("2026-08-20T13:30:00Z"),
            endZoneOffset = pacific,
            metadata = semanticMetadata("sleep"),
            title = null,
            notes = null,
            stages = listOf(
                SleepSessionRecord.Stage(
                    Instant.parse("2026-08-20T07:10:00Z"),
                    Instant.parse("2026-08-20T07:42:00Z"),
                    SleepSessionRecord.STAGE_TYPE_LIGHT,
                ),
            ),
        )
        return listOf(
            SemanticVectorRecord("sleep-duration", HealthConnectContract.MOBILE_SLEEP_DURATION_PROFILE, sleep),
            SemanticVectorRecord("sleep-stage", HealthConnectContract.MOBILE_SLEEP_STAGE_PROFILE, sleep),
        )
    }
}
