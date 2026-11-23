//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMindfulnessSessionApi::class)
@file:Suppress("detekt:LargeClass", "detekt:LongMethod", "detekt:CyclomaticComplexMethod", "detekt:TooManyFunctions", "detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseLap
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.LosslessJsonValue
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonArray
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonNumber
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonObject
import org.grovealliance.fhir.contract.LosslessJsonValue.JsonString
import org.grovealliance.health.fhir.ExchangeProducerFixtures
import org.grovealliance.health.fhir.HealthConnectFhirExporter
import org.grovealliance.health.fhir.MetadataFixtures
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Runs the grove-fhir mobile-semantics corpus: for every vector that admits Health Connect, the
 * Record a writer would store for it converts to an Observation whose profile, code, effective time
 * and result equal the vector's. Each converted record's Bundle is written to the directory the
 * `grove.conformance.export` property names, for the official FHIR Validator.
 */
class SemanticVectorTest {
    private val exporter = HealthConnectFhirExporter.create(
        producer = ExchangeProducerFixtures.create(),
        repositoryScope = ExchangeProducerFixtures.repositoryScope,
        options = HealthConnectFhirExporter.Options(
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
            writerApplicationSystem = ExchangeProducerFixtures.writerApplicationSystem,
        ),
    ).getOrThrow()
    private val vectors = (jsonObject(value = parse(name = "corpus.json")).members.getValue("vectors") as JsonArray).elements
        .map { it as JsonObject }
        .filter { (it.members["admittedSourceContext"] as? JsonObject)?.members?.containsKey("health-connect") == true }

    @Test
    fun `converts every vector that admits Health Connect to its projection`() {
        // given
        val converted = vectors.filter { text(node = it, name = "id") !in NOT_EMITTED }

        // when
        val mismatches = converted.mapNotNull { vector -> mismatch(vector = vector) }

        // then
        assertThat(converted).isNotEmpty()
        assertThat(mismatches).isEmpty()
    }

    @Test
    fun `emits no sleep-duration summary`() {
        // when
        val vectorIds = vectors.map { text(node = it, name = "id") }

        // then
        assertThat(vectorIds).containsAtLeastElementsIn(NOT_EMITTED)
    }

    private fun mismatch(vector: JsonObject): String? {
        val id = text(node = vector, name = "id")
        val record = record(vector = vector) ?: return "$id: no Record builds it"
        val export = exporter.export(records = listOf(record), instant = Instant.parse("2027-01-01T00:00:00Z")).single()
        val json = export.record?.json ?: return "$id: refused, ${export.outcome}"
        System.getProperty(EXPORT_PROPERTY)?.let { File(it).apply { mkdirs() }.resolve("$id.json").writeBytes(json) }
        val profile = text(node = vector, name = "profile")
        val observation = resources(json = json).firstOrNull { profile in profiles(resource = it) }
            ?: return "$id: no Observation claims $profile"
        val expected = listOf(
            code(node = vector.members["code"] as JsonObject),
            effective(node = vector.members["effective"] as JsonObject),
            result(node = vector.members["result"] as JsonObject),
        )
        val actual = listOf(
            observedCode(observation = observation),
            observedEffective(observation = observation),
            observedResult(observation = observation),
        )
        return if (expected == actual) null else "$id: expected $expected, got $actual"
    }

    private fun record(vector: JsonObject): Record? {
        val id = text(node = vector, name = "id")
        val effective = vector.members["effective"] as JsonObject
        val result = vector.members["result"] as JsonObject
        val value = (result.members["value"] as? JsonNumber)?.lexeme?.let { BigDecimal(it) }
        val metadata = MetadataFixtures.create()
        if (text(node = effective, name = "type") == "dateTime") {
            val time = OffsetDateTime.parse(text(node = effective, name = "value"))
            return pointRecord(id = id, time = time.toInstant(), offset = time.offset, value = value, result = result)
        }
        val period = Window(
            start = OffsetDateTime.parse(text(node = effective, name = "start")).toInstant(),
            end = OffsetDateTime.parse(text(node = effective, name = "end")).toInstant(),
            offset = OffsetDateTime.parse(text(node = effective, name = "start")).offset,
        )
        return intervalRecord(id = id, period = period, value = value, result = result, metadata = metadata)
    }

    private data class Window(val start: Instant, val end: Instant, val offset: ZoneOffset)

    private fun pointRecord(id: String, time: Instant, offset: ZoneOffset, value: BigDecimal?, result: JsonObject): Record? {
        val metadata = MetadataFixtures.create()
        val number = value?.toDouble() ?: 0.0
        val sampleEnd = time.plusSeconds(1)
        return when (id) {
            "basal-body-temperature" -> BasalBodyTemperatureRecord(
                time = time,
                zoneOffset = offset,
                temperature = Temperature.celsius(number),
                metadata = metadata,
            )
            "body-temperature" -> BodyTemperatureRecord(
                time = time,
                zoneOffset = offset,
                temperature = Temperature.celsius(number),
                metadata = metadata,
            )
            "body-fat-percentage" -> BodyFatRecord(time = time, zoneOffset = offset, percentage = Percentage(number), metadata = metadata)
            "body-height" -> HeightRecord(
                time = time,
                zoneOffset = offset,
                height = Length.meters(value!!.movePointLeft(2).toDouble()),
                metadata = metadata,
            )
            "body-water-mass" -> BodyWaterMassRecord(time = time, zoneOffset = offset, mass = Mass.kilograms(number), metadata = metadata)
            "body-weight" -> WeightRecord(time = time, zoneOffset = offset, weight = Mass.kilograms(number), metadata = metadata)
            "bone-mass" -> BoneMassRecord(time = time, zoneOffset = offset, mass = Mass.kilograms(number), metadata = metadata)
            "lean-body-mass" -> LeanBodyMassRecord(time = time, zoneOffset = offset, mass = Mass.kilograms(number), metadata = metadata)
            "heart-rate-variability-rmssd" -> HeartRateVariabilityRmssdRecord(
                time = time,
                zoneOffset = offset,
                heartRateVariabilityMillis = number,
                metadata = metadata,
            )
            "oxygen-saturation" -> OxygenSaturationRecord(
                time = time,
                zoneOffset = offset,
                percentage = Percentage(number),
                metadata = metadata,
            )
            "respiratory-rate" -> RespiratoryRateRecord(time = time, zoneOffset = offset, rate = number, metadata = metadata)
            "resting-heart-rate" -> RestingHeartRateRecord(
                time = time,
                zoneOffset = offset,
                beatsPerMinute = number.toLong(),
                metadata = metadata,
            )
            "vo2-max" -> Vo2MaxRecord(
                time = time,
                zoneOffset = offset,
                vo2MillilitersPerMinuteKilogram = number,
                measurementMethod = Vo2MaxRecord.MEASUREMENT_METHOD_OTHER,
                metadata = metadata,
            )
            "skin-temperature" -> SkinTemperatureRecord(
                startTime = time,
                startZoneOffset = offset,
                endTime = sampleEnd,
                endZoneOffset = offset,
                baseline = Temperature.celsius(number),
                deltas = listOf(SkinTemperatureRecord.Delta(time = time, delta = TemperatureDelta.celsius(0.0))),
                metadata = metadata,
            )
            "heart-rate" -> HeartRateRecord(
                startTime = time,
                startZoneOffset = offset,
                endTime = sampleEnd,
                endZoneOffset = offset,
                samples = listOf(HeartRateRecord.Sample(time = time, beatsPerMinute = number.toLong())),
                metadata = metadata,
            )
            "cycling-cadence" -> CyclingPedalingCadenceRecord(
                startTime = time,
                startZoneOffset = offset,
                endTime = sampleEnd,
                endZoneOffset = offset,
                samples = listOf(CyclingPedalingCadenceRecord.Sample(time = time, revolutionsPerMinute = number)),
                metadata = metadata,
            )
            "power" -> PowerRecord(
                startTime = time,
                startZoneOffset = offset,
                endTime = sampleEnd,
                endZoneOffset = offset,
                samples = listOf(PowerRecord.Sample(time = time, power = Power.watts(number))),
                metadata = metadata,
            )
            "speed" -> SpeedRecord(
                startTime = time,
                startZoneOffset = offset,
                endTime = sampleEnd,
                endZoneOffset = offset,
                samples = listOf(SpeedRecord.Sample(time = time, speed = Velocity.metersPerSecond(number))),
                metadata = metadata,
            )
            "blood-pressure" -> {
                val components = (result.members["components"] as JsonArray).elements.map { it as JsonObject }
                    .associate { text(node = it, name = "id") to BigDecimal((it.members["value"] as JsonNumber).lexeme).toDouble() }
                BloodPressureRecord(
                    time = time,
                    zoneOffset = offset,
                    systolic = Pressure.millimetersOfMercury(components.getValue("systolic")),
                    diastolic = Pressure.millimetersOfMercury(components.getValue("diastolic")),
                    metadata = metadata,
                )
            }
            "cervical-mucus-quality" -> CervicalMucusRecord(
                time = time,
                zoneOffset = offset,
                appearance = source(table = HealthConnectCatalog.cervicalMucusAppearances, result = result),
                metadata = metadata,
            )
            "menstruation-flow" -> MenstruationFlowRecord(
                time = time,
                zoneOffset = offset,
                flow = source(table = HealthConnectCatalog.menstruationFlows, result = result),
                metadata = metadata,
            )
            "ovulation-test-result" -> OvulationTestRecord(
                time = time,
                zoneOffset = offset,
                result = source(table = HealthConnectCatalog.ovulationTestResults, result = result),
                metadata = metadata,
            )
            "sexual-activity" -> SexualActivityRecord(
                time = time,
                zoneOffset = offset,
                protectionUsed = source(table = HealthConnectCatalog.sexualActivityProtections, result = result),
                metadata = metadata,
            )
            "intermenstrual-bleeding" -> IntermenstrualBleedingRecord(time = time, zoneOffset = offset, metadata = metadata)
            else -> null
        }
    }

    private fun intervalRecord(
        id: String,
        period: Window,
        value: BigDecimal?,
        result: JsonObject,
        metadata: Metadata,
    ): Record? {
        val number = value?.toDouble() ?: 0.0
        val unit = text(node = result, name = "code")
        val amount: Any = when (unit) {
            "kcal" -> Energy.kilocalories(number)
            "g" -> Mass.grams(number)
            "mg" -> Mass.milligrams(number)
            "ug" -> Mass.micrograms(number)
            else -> number
        }
        return when (id) {
            "active-energy" -> ActiveCaloriesBurnedRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                energy = Energy.kilocalories(number),
                metadata = metadata,
            )
            "distance" -> DistanceRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                distance = Length.meters(number),
                metadata = metadata,
            )
            "flights-climbed" -> FloorsClimbedRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                floors = number,
                metadata = metadata,
            )
            "fluid-intake" -> HydrationRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                volume = Volume.milliliters(number),
                metadata = metadata,
            )
            "step-count" -> StepsRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                count = number.toLong(),
                metadata = metadata,
            )
            "wheelchair-push-count" -> WheelchairPushesRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                count = number.toLong(),
                metadata = metadata,
            )
            "mindfulness-session" -> MindfulnessSessionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                metadata = metadata,
            )
            "sleep-stage" -> SleepSessionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                stages = listOf(
                    SleepSessionRecord.Stage(
                        startTime = period.start,
                        endTime = period.end,
                        stage = HealthConnectCatalog.sleepStages.entries.first { it.value.shared == text(
                            node = result,
                            name = "code",
                        ) }.key,
                    ),
                ),
                metadata = metadata,
            )
            "workout" -> ExerciseSessionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                exerciseType = HealthConnectCatalog.exerciseTypes.keys.first { ExerciseProjection().mapActivity(type = it).code == text(
                    node = result,
                    name = "code",
                ) },
                metadata = metadata,
            )
            "workout-segment" -> ExerciseSessionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
                laps = listOf(ExerciseLap(startTime = period.start, endTime = period.end)),
                metadata = metadata,
            )
            "dietary-biotin" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                biotin = amount as Mass,
                metadata = metadata,
            )
            "dietary-caffeine" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                caffeine = amount as Mass,
                metadata = metadata,
            )
            "dietary-calcium" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                calcium = amount as Mass,
                metadata = metadata,
            )
            "dietary-carbohydrates" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                totalCarbohydrate = amount as Mass,
                metadata = metadata,
            )
            "dietary-chloride" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                chloride = amount as Mass,
                metadata = metadata,
            )
            "dietary-cholesterol" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                cholesterol = amount as Mass,
                metadata = metadata,
            )
            "dietary-chromium" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                chromium = amount as Mass,
                metadata = metadata,
            )
            "dietary-copper" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                copper = amount as Mass,
                metadata = metadata,
            )
            "dietary-energy" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                energy = amount as Energy,
                metadata = metadata,
            )
            "dietary-fat-monounsaturated" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                monounsaturatedFat = amount as Mass,
                metadata = metadata,
            )
            "dietary-fat-polyunsaturated" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                polyunsaturatedFat = amount as Mass,
                metadata = metadata,
            )
            "dietary-fat-saturated" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                saturatedFat = amount as Mass,
                metadata = metadata,
            )
            "dietary-fat-total" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                totalFat = amount as Mass,
                metadata = metadata,
            )
            "dietary-fiber" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                dietaryFiber = amount as Mass,
                metadata = metadata,
            )
            "dietary-folate" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                folate = amount as Mass,
                metadata = metadata,
            )
            "dietary-iodine" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                iodine = amount as Mass,
                metadata = metadata,
            )
            "dietary-iron" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                iron = amount as Mass,
                metadata = metadata,
            )
            "dietary-magnesium" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                magnesium = amount as Mass,
                metadata = metadata,
            )
            "dietary-manganese" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                manganese = amount as Mass,
                metadata = metadata,
            )
            "dietary-molybdenum" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                molybdenum = amount as Mass,
                metadata = metadata,
            )
            "dietary-niacin" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                niacin = amount as Mass,
                metadata = metadata,
            )
            "dietary-pantothenic-acid" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                pantothenicAcid = amount as Mass,
                metadata = metadata,
            )
            "dietary-phosphorus" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                phosphorus = amount as Mass,
                metadata = metadata,
            )
            "dietary-potassium" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                potassium = amount as Mass,
                metadata = metadata,
            )
            "dietary-protein" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                protein = amount as Mass,
                metadata = metadata,
            )
            "dietary-riboflavin" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                riboflavin = amount as Mass,
                metadata = metadata,
            )
            "dietary-selenium" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                selenium = amount as Mass,
                metadata = metadata,
            )
            "dietary-sodium" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                sodium = amount as Mass,
                metadata = metadata,
            )
            "dietary-sugar" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                sugar = amount as Mass,
                metadata = metadata,
            )
            "dietary-thiamin" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                thiamin = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-a" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminA = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-b12" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminB12 = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-b6" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminB6 = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-c" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminC = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-d" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminD = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-e" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminE = amount as Mass,
                metadata = metadata,
            )
            "dietary-vitamin-k" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                vitaminK = amount as Mass,
                metadata = metadata,
            )
            "dietary-zinc" -> NutritionRecord(
                startTime = period.start,
                startZoneOffset = period.offset,
                endTime = period.end,
                endZoneOffset = period.offset,
                zinc = amount as Mass,
                metadata = metadata,
            )
            else -> null
        }
    }

    /**
     * The source constant of [table] whose shared code is the vector's result.
     */
    private fun source(table: Map<Int, HealthConnectCodedValue>, result: JsonObject): Int =
        table.entries.first { it.value.shared == text(node = result, name = "code") }.key

    private fun code(node: JsonObject) = text(node = node, name = "system") to text(node = node, name = "code")

    private fun observedCode(observation: JsonObject): Pair<String, String> {
        val coding = ((observation.members["code"] as JsonObject).members["coding"] as JsonArray).elements.first() as JsonObject
        return code(node = coding)
    }

    private fun effective(node: JsonObject): List<String> = if (text(node = node, name = "type") == "dateTime") {
        listOf(instant(lexeme = text(node = node, name = "value")))
    } else {
        listOf(instant(lexeme = text(node = node, name = "start")), instant(lexeme = text(node = node, name = "end")))
    }

    private fun observedEffective(observation: JsonObject): List<String> {
        val period = observation.members["effectivePeriod"] as? JsonObject
        return if (period == null) {
            listOf(instant(lexeme = text(node = observation, name = "effectiveDateTime")))
        } else {
            listOf(instant(lexeme = text(node = period, name = "start")), instant(lexeme = text(node = period, name = "end")))
        }
    }

    /**
     * The instant and offset [lexeme] states, so equal instants written differently compare equal.
     */
    private fun instant(lexeme: String): String = OffsetDateTime.parse(lexeme).let { "${it.toInstant()}${it.offset}" }

    private fun result(node: JsonObject): List<String> = when (text(node = node, name = "type")) {
        "Quantity" -> listOf(decimal(node = node), text(node = node, name = "system"), text(node = node, name = "code"))
        "CodeableConcept" -> listOf(text(node = node, name = "system"), text(node = node, name = "code"))
        else -> (node.members["components"] as JsonArray).elements.map { it as JsonObject }.flatMap { component ->
            listOf(text(node = component, name = "code"), decimal(node = component), text(node = component, name = "quantityCode"))
        }
    }

    private fun observedResult(observation: JsonObject): List<String> {
        (observation.members["valueQuantity"] as? JsonObject)?.let {
            return listOf(decimal(node = it), text(node = it, name = "system"), text(node = it, name = "code"))
        }
        (observation.members["valueCodeableConcept"] as? JsonObject)?.let {
            return code(node = (it.members["coding"] as JsonArray).elements.first() as JsonObject).toList()
        }
        return (observation.members["component"] as JsonArray).elements.map { it as JsonObject }.flatMap { component ->
            val coding = ((component.members["code"] as JsonObject).members["coding"] as JsonArray).elements.first() as JsonObject
            val quantity = component.members["valueQuantity"] as JsonObject
            listOf(text(node = coding, name = "code"), decimal(node = quantity), text(node = quantity, name = "code"))
        }
    }

    private fun decimal(node: JsonObject): String =
        BigDecimal((node.members["value"] as JsonNumber).lexeme).stripTrailingZeros().toPlainString()

    private fun profiles(resource: JsonObject): List<String> =
        ((resource.members["meta"] as? JsonObject)?.members?.get("profile") as? JsonArray)?.elements.orEmpty()
            .map { (it as JsonString).value }

    private fun resources(json: ByteArray): List<JsonObject> =
        ((jsonObject(value = LosslessJsonValue.parse(bytes = json).getOrThrow()).members["entry"] as JsonArray).elements)
            .map { ((it as JsonObject).members["resource"] as JsonObject) }

    private fun parse(name: String): LosslessJsonValue = LosslessJsonValue.parse(
        bytes = requireNotNull(javaClass.getResource("/grove-fhir/corpora/mobile-semantics/$name")).readBytes(),
    ).getOrThrow()

    private fun jsonObject(value: LosslessJsonValue) = value as JsonObject

    private fun text(node: JsonObject, name: String) = (node.members.getValue(name) as JsonString).value

    private companion object {
        val NOT_EMITTED = setOf("sleep-duration")
        const val EXPORT_PROPERTY = "grove.conformance.export"
    }
}
