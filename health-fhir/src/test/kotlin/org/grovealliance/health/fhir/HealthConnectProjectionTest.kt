//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import com.google.common.truth.Truth.assertThat
import org.hl7.fhir.r4.model.Observation
import org.junit.Test

/** Every conversion projects back onto the AndroidX record it came from. */
class HealthConnectProjectionTest {
    private val fixtures = HealthConnectTestFixtures
    private val records = HealthConnectFixtureRecords

    @Test
    fun `a single output projects onto its exactly-one record`() {
        val source = records.steps(count = 1042)
        val observation = fixtures.converted(source).toBundle().entry.map { it.resource }.filterIsInstance<Observation>().single()
        val projected = observation.toHealthConnectRecord() as HealthConnectProjectionResult.Projected
        val record = projected.record as StepsRecord
        assertThat(record.count).isEqualTo(1042)
        assertThat(record.startTime).isEqualTo(source.startTime)
        assertThat(record.endTime).isEqualTo(source.endTime)
        assertThat(record.startZoneOffset).isEqualTo(source.startZoneOffset)
        assertThat(record.metadata.recordingMethod).isEqualTo(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED)
        assertThat(record.metadata.clientRecordId).isEqualTo(observation.sourceOutputIdentifier()?.identifier?.value)
        val named = observation.toHealthConnectRecord("client-7") as HealthConnectProjectionResult.Projected
        assertThat(named.record.metadata.clientRecordId).isEqualTo("client-7")
    }

    @Test
    fun `a graph projects every source record it carries`() {
        val conversion = fixtures.converted(records.bloodPressure())
        val batch = conversion.graph.toHealthConnectRecords()
        assertThat(batch.failures).isEmpty()
        val record = batch.conversions.single() as BloodPressureRecord
        assertThat(record.systolic.inMillimetersOfMercury).isEqualTo(120.0)
        assertThat(record.diastolic.inMillimetersOfMercury).isEqualTo(80.0)
        assertThat(record.bodyPosition).isEqualTo(BloodPressureRecord.BODY_POSITION_SITTING_DOWN)
        assertThat(record.measurementLocation).isEqualTo(BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM)
    }

    @Test
    fun `a series graph folds its members back into one sampled record`() {
        val source = records.heartRate()
        val record = fixtures.converted(source).graph.toHealthConnectRecords().conversions.single() as HeartRateRecord
        assertThat(record.samples.map { it.time to it.beatsPerMinute })
            .containsExactlyElementsIn(source.samples.map { it.time to it.beatsPerMinute })
        assertThat(record.startTime).isEqualTo(source.samples.first().time)
        assertThat(record.endTime).isEqualTo(source.samples.last().time)
    }

    @Test
    fun `sessions rebuild their stages segments and laps and a member alone is refused`() {
        val sleep = records.sleep()
        val sleepRecord = fixtures.converted(sleep).graph.toHealthConnectRecords().conversions.single() as SleepSessionRecord
        assertThat(sleepRecord.stages.map { it.stage }).containsExactlyElementsIn(sleep.stages.map { it.stage })
        assertThat(sleepRecord.title).isEqualTo("Night sleep")
        assertThat(sleepRecord.notes).isEqualTo("Participant-reported note")

        val start = records.sessionStart
        val exercise = records.exercise(
            segments = listOf(ExerciseSegment(start, start.plusSeconds(300), ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING, 3)),
            laps = listOf(ExerciseLap(start.plusSeconds(300), start.plusSeconds(600), Length.meters(400.0))),
        )
        val exerciseRecord = fixtures.converted(exercise).graph.toHealthConnectRecords().conversions.single() as ExerciseSessionRecord
        assertThat(exerciseRecord.exerciseType).isEqualTo(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING)
        assertThat(exerciseRecord.segments.single().repetitions).isEqualTo(3)
        assertThat(exerciseRecord.laps.single().length).isEqualTo(Length.meters(400.0))

        val stage = fixtures.converted(sleep).toBundle().entry.map { it.resource }.filterIsInstance<Observation>()
            .first { it.hasMember.isEmpty() }
        val refused = stage.toHealthConnectRecord() as HealthConnectProjectionResult.Refused
        assertThat(refused.refusal).isEqualTo(HealthConnectProjectionRefusal.ChildOutput("sleep-stage"))
    }

    @Test
    fun `glucose and nutrition rebuild from their companions and present fields`() {
        val glucose = records.bloodGlucose("fixture-glucose", BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA)
        val glucoseRecord = fixtures.converted(glucose).graph.toHealthConnectRecords().conversions.single() as BloodGlucoseRecord
        assertThat(glucoseRecord.level).isEqualTo(glucose.level)
        assertThat(glucoseRecord.specimenSource).isEqualTo(BloodGlucoseRecord.SPECIMEN_SOURCE_PLASMA)
        assertThat(glucoseRecord.mealType).isEqualTo(glucose.mealType)
        assertThat(glucoseRecord.relationToMeal).isEqualTo(glucose.relationToMeal)

        val nutrition = records.nutrition()
        val nutritionRecord = fixtures.converted(nutrition).graph.toHealthConnectRecords().conversions.single() as NutritionRecord
        assertThat(nutritionRecord.energy).isEqualTo(nutrition.energy)
        assertThat(nutritionRecord.protein).isEqualTo(nutrition.protein)
        assertThat(nutritionRecord.sodium).isEqualTo(nutrition.sodium)
        assertThat(nutritionRecord.name).isNull()
    }

    @Test
    fun `an Observation that is not a Health Connect output is refused with its location`() {
        val result = Observation().toHealthConnectRecord() as HealthConnectProjectionResult.Refused
        assertThat(result.refusal).isEqualTo(HealthConnectProjectionRefusal.NotHealthConnectOutput("Observation.extension"))
    }
}
