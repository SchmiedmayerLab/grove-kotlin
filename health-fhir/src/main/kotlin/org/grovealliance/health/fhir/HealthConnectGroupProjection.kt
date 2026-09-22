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
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Velocity
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Observation
import java.time.Instant

/** Every source type that fans out into one Observation per sample, read back from its sorted samples. */
internal fun OutputGroup.series(): Record? = when (type) {
    HealthConnectSourceType.HEART_RATE -> sampled("heart-rate") { span, samples ->
        HeartRateRecord(
            startTime = span.start,
            startZoneOffset = null,
            endTime = span.endInclusive,
            endZoneOffset = null,
            samples = samples.map { (time, value) -> HeartRateRecord.Sample(time, value.toLong()) },
            metadata = metadata,
        )
    }
    HealthConnectSourceType.CYCLING_PEDALING_CADENCE -> sampled("cycling-cadence") { span, samples ->
        CyclingPedalingCadenceRecord(
            startTime = span.start,
            startZoneOffset = null,
            endTime = span.endInclusive,
            endZoneOffset = null,
            samples = samples.map { (time, value) -> CyclingPedalingCadenceRecord.Sample(time, value) },
            metadata = metadata,
        )
    }
    HealthConnectSourceType.POWER -> sampled("power") { span, samples ->
        PowerRecord(
            startTime = span.start,
            startZoneOffset = null,
            endTime = span.endInclusive,
            endZoneOffset = null,
            samples = samples.map { (time, value) -> PowerRecord.Sample(time, Power.watts(value)) },
            metadata = metadata,
        )
    }
    HealthConnectSourceType.SPEED -> sampled("speed") { span, samples ->
        SpeedRecord(
            startTime = span.start,
            startZoneOffset = null,
            endTime = span.endInclusive,
            endZoneOffset = null,
            samples = samples.map { (time, value) -> SpeedRecord.Sample(time, Velocity.metersPerSecond(value)) },
            metadata = metadata,
        )
    }
    HealthConnectSourceType.STEPS_CADENCE -> sampled("step-cadence") { span, samples ->
        StepsCadenceRecord(
            startTime = span.start,
            startZoneOffset = null,
            endTime = span.endInclusive,
            endZoneOffset = null,
            samples = samples.map { (time, value) -> StepsCadenceRecord.Sample(time, value) },
            metadata = metadata,
        )
    }
    HealthConnectSourceType.SKIN_TEMPERATURE -> sampled("skin-temperature") { span, samples -> skinTemperature(span, samples) }
    else -> null
}

/** The outputs as time-sorted samples of one measurement, and the span they cover. */
private fun <T : Record> OutputGroup.sampled(
    measurement: String,
    build: OutputGroup.(ClosedRange<Instant>, List<Pair<Instant, Double>>) -> T,
): T {
    val samples = (listOf(primary) + children).map { it.moment().time to quantity(it, measurement) }.sortedBy { it.first }
    return build(samples.first().first..samples.last().first, samples)
}

private fun OutputGroup.skinTemperature(span: ClosedRange<Instant>, values: List<Pair<Instant, Double>>): SkinTemperatureRecord {
    // The graph carries absolute temperatures; the first sample becomes the baseline the deltas rest on.
    val baseline = values.first().second
    return SkinTemperatureRecord(
        startTime = span.start,
        startZoneOffset = null,
        endTime = span.endInclusive,
        endZoneOffset = null,
        metadata = metadata,
        deltas = values.map { (time, value) -> SkinTemperatureRecord.Delta(time, TemperatureDelta.celsius(value - baseline)) },
        baseline = Temperature.celsius(baseline),
        measurementLocation = HealthConnectSourceTokens.skinTemperatureMeasurementLocation.externalValue(
            HealthConnectContextMappings.skinTemperatureMeasurementLocation,
            primary.bodySite?.codingFirstRep?.takeIf { it.hasCode() },
            OutputGroup.BODY_SITE_PATH,
        ),
    )
}

/** Sleep, exercise and mindfulness sessions, read back from their summary and member outputs. */
internal fun OutputGroup.session(): Record? = when (type) {
    HealthConnectSourceType.SLEEP_SESSION -> sleepSession()
    HealthConnectSourceType.EXERCISE_SESSION -> exerciseSession()
    HealthConnectSourceType.MINDFULNESS_SESSION -> requireSingle().let { o ->
        val span = o.interval()
        val method = o.methodCode(HealthConnectContextMappings.mindfulnessSessionType.codeSystem)
        MindfulnessSessionRecord(
            startTime = span.start.time,
            startZoneOffset = span.start.offset,
            endTime = span.end.time,
            endZoneOffset = span.end.offset,
            metadata = metadata,
            mindfulnessSessionType = HealthConnectSourceTokens.mindfulnessSessionType.sourceValue(method, OutputGroup.METHOD_PATH),
            title = o.sessionTitle(),
            notes = o.sessionNotes(),
        )
    }
    else -> null
}

private fun OutputGroup.sleepSession(): SleepSessionRecord {
    val span = summary("sleep-duration").interval()
    return SleepSessionRecord(
        startTime = span.start.time,
        startZoneOffset = span.start.offset,
        endTime = span.end.time,
        endZoneOffset = span.end.offset,
        metadata = metadata,
        title = primary.sessionTitle(),
        notes = primary.sessionNotes(),
        stages = children.map { stage ->
            val stageSpan = stage.interval()
            val token = stage.sourceToken(HealthConnectContextMappings.sleepStage.sourceSystem)
            val value = HealthConnectSourceTokens.sleepStage.sourceValue(token, OutputGroup.VALUE_PATH)
            SleepSessionRecord.Stage(stageSpan.start.time, stageSpan.end.time, value)
        },
    )
}

private fun OutputGroup.exerciseSession(): ExerciseSessionRecord {
    val summary = summary("workout")
    val span = summary.interval()
    val activityToken = summary.sourceToken(HealthConnectContextMappings.exerciseType.sourceSystem)
    val exerciseType = HealthConnectWorkoutVocabulary.activityToken(activityToken)
        ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedCode(OutputGroup.VALUE_PATH))
    val segments = mutableListOf<ExerciseSegment>()
    val laps = mutableListOf<ExerciseLap>()
    val segmentSpec = spec("workout-segment")
    children.forEach { child ->
        val childSpan = child.interval()
        val token = child.sourceToken(HealthConnectContextMappings.exerciseSegmentType.sourceSystem)
        if (token == HealthConnectWorkoutVocabulary.LAP_TOKEN) {
            val length = child.componentValue(segmentSpec.component("lap-length"))?.let(Length::meters)
            laps += ExerciseLap(childSpan.start.time, childSpan.end.time, length)
        } else {
            val segmentType = HealthConnectWorkoutVocabulary.segmentToken(token)
                ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedCode(OutputGroup.VALUE_PATH))
            val repetitions = child.componentValue(segmentSpec.component("repetitions"))?.toInt() ?: 0
            segments += ExerciseSegment(childSpan.start.time, childSpan.end.time, segmentType, repetitions)
        }
    }
    return ExerciseSessionRecord(
        startTime = span.start.time,
        startZoneOffset = span.start.offset,
        endTime = span.end.time,
        endZoneOffset = span.end.offset,
        metadata = metadata,
        exerciseType = exerciseType,
        title = summary.sessionTitle(),
        notes = summary.sessionNotes(),
        segments = segments,
        laps = laps,
    )
}

/** Blood glucose with its Specimen companion, and nutrition folded back into one record. */
internal fun OutputGroup.special(): Record? = when (type) {
    HealthConnectSourceType.BLOOD_GLUCOSE -> requireSingle().let(::bloodGlucose)
    HealthConnectSourceType.NUTRITION -> nutrition()
    else -> null
}

private fun OutputGroup.bloodGlucose(observation: Observation): BloodGlucoseRecord {
    val measurement = primaryMeasurement ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.meta.profile"))
    val specimenType = companions.firstOrNull()?.type?.codingFirstRep?.takeIf { it.hasCode() }
    val candidates = HealthConnectContextMappings.bloodGlucoseSpecimen.filter { it.measurement == measurement }
    val specimenSource = candidates.singleOrNull { specimenType == null || it.specimenType?.code == specimenType.code }
        ?: candidates.singleOrNull()
        ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.specimen"))
    val mealContext = observation.getExtensionByUrl(HealthConnectContract.HEALTH_CONNECT_GLUCOSE_MEAL_CONTEXT)
    val relation = (mealContext?.getExtensionByUrl("relationToMeal")?.value as? Coding)?.code
    val meal = (mealContext?.getExtensionByUrl("mealType")?.value as? Coding)?.code
    return BloodGlucoseRecord(
        time = observation.moment().time,
        zoneOffset = observation.moment().offset,
        metadata = metadata,
        level = BloodGlucose.milligramsPerDeciliter(quantity(observation, measurement)),
        specimenSource = HealthConnectSourceTokens.specimenSource.sourceValue(specimenSource.source, "Specimen.type"),
        mealType = meal?.let { HealthConnectSourceTokens.mealType.sourceValue(it, MEAL_CONTEXT_PATH) } ?: UNKNOWN_SOURCE_VALUE,
        relationToMeal = relation?.let { HealthConnectSourceTokens.relationToMeal.sourceValue(it, MEAL_CONTEXT_PATH) }
            ?: UNKNOWN_SOURCE_VALUE,
    )
}

private fun OutputGroup.nutrition(): NutritionRecord {
    val span = primary.interval()
    val values = (listOf(primary) + children).associate { output ->
        val measurement = output.measurementId()
            ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.meta.profile"))
        measurement to quantity(output, measurement)
    }
    return nutritionRecord(span, metadata, values)
}

@Suppress("LongMethod") // One named argument per nutrient the AndroidX constructor admits.
private fun nutritionRecord(span: ProjectedInterval, metadata: Metadata, values: Map<String, Double>): NutritionRecord {
    fun grams(id: String) = values[id]?.let(Mass::grams)
    fun milligrams(id: String) = values[id]?.let(Mass::milligrams)
    fun micrograms(id: String) = values[id]?.let(Mass::micrograms)
    fun kilocalories(id: String) = values[id]?.let(Energy::kilocalories)
    return NutritionRecord(
        startTime = span.start.time,
        startZoneOffset = span.start.offset,
        endTime = span.end.time,
        endZoneOffset = span.end.offset,
        metadata = metadata,
        biotin = micrograms("dietary-biotin"),
        caffeine = milligrams("dietary-caffeine"),
        calcium = milligrams("dietary-calcium"),
        energy = kilocalories("dietary-energy"),
        energyFromFat = kilocalories("dietary-energy-from-fat"),
        chloride = milligrams("dietary-chloride"),
        cholesterol = milligrams("dietary-cholesterol"),
        chromium = micrograms("dietary-chromium"),
        copper = micrograms("dietary-copper"),
        dietaryFiber = grams("dietary-fiber"),
        folate = micrograms("dietary-folate"),
        folicAcid = micrograms("dietary-folic-acid"),
        iodine = micrograms("dietary-iodine"),
        iron = milligrams("dietary-iron"),
        magnesium = milligrams("dietary-magnesium"),
        manganese = milligrams("dietary-manganese"),
        molybdenum = micrograms("dietary-molybdenum"),
        monounsaturatedFat = grams("dietary-fat-monounsaturated"),
        niacin = milligrams("dietary-niacin"),
        pantothenicAcid = milligrams("dietary-pantothenic-acid"),
        phosphorus = milligrams("dietary-phosphorus"),
        polyunsaturatedFat = grams("dietary-fat-polyunsaturated"),
        potassium = milligrams("dietary-potassium"),
        protein = grams("dietary-protein"),
        riboflavin = milligrams("dietary-riboflavin"),
        saturatedFat = grams("dietary-fat-saturated"),
        selenium = micrograms("dietary-selenium"),
        sodium = milligrams("dietary-sodium"),
        sugar = grams("dietary-sugar"),
        thiamin = milligrams("dietary-thiamin"),
        totalCarbohydrate = grams("dietary-carbohydrates"),
        totalFat = grams("dietary-fat-total"),
        transFat = grams("dietary-fat-trans"),
        unsaturatedFat = grams("dietary-fat-unsaturated"),
        vitaminA = micrograms("dietary-vitamin-a"),
        vitaminB12 = micrograms("dietary-vitamin-b12"),
        vitaminB6 = milligrams("dietary-vitamin-b6"),
        vitaminC = milligrams("dietary-vitamin-c"),
        vitaminD = micrograms("dietary-vitamin-d"),
        vitaminE = milligrams("dietary-vitamin-e"),
        vitaminK = micrograms("dietary-vitamin-k"),
        zinc = milligrams("dietary-zinc"),
    )
}

private const val MEAL_CONTEXT_PATH = "Observation.extension[mealContext]"
