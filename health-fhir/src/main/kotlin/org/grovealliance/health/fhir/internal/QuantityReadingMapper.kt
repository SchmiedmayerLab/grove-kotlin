//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Observation
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/**
 * The quantity readings of a Record whose outputs are one quantity each: one for a point or interval
 * Record, one per sample for a series, each in its measurement's unit.
 */
@OptIn(InternalGroveFhirApi::class)
internal class QuantityReadingMapper(private val times: SourceTimeMapper) {

    /**
     * One reading: its [effective] time, its [value] or `null` when the source value is no finite
     * number, the [field] it was read from, its [sampleTime] when it is one sample of a series, and
     * whether the source stated an offset for every time it carries; then the [bodySite] and [method]
     * the Record states, and the [unsupportedField] whose source constant the contract doesn't map.
     */
    data class Reading(
        val effective: Observation.Effective,
        val value: BigDecimal?,
        val field: String,
        val sampleTime: Instant?,
        val statesOffset: Boolean,
        val bodySite: CodeableConcept? = null,
        val method: CodeableConcept? = null,
        val unsupportedField: String? = null,
    )

    /**
     * The readings of [record], or `null` when it is no quantity Record.
     */
    fun mapReadings(record: Record): List<Reading>? = pointReading(record = record)?.let { listOf(it) }
        ?: intervalReading(record = record)?.let { listOf(it) }
        ?: seriesReadings(record = record)

    @Suppress("detekt:CyclomaticComplexMethod", "detekt:LongMethod")
    private fun pointReading(record: Record): Reading? = when (record) {
        is BasalMetabolicRateRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.basalMetabolicRate.inKilocaloriesPerDay),
            field = "basalMetabolicRate",
        )
        is BodyFatRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.percentage.value),
            field = "percentage",
        )
        is BodyWaterMassRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.mass.inKilograms),
            field = "mass",
        )
        is BoneMassRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.mass.inKilograms),
            field = "mass",
        )
        is HeightRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.height.inMeters)?.movePointRight(CENTIMETERS_PER_METER_EXPONENT),
            field = "height",
        )
        is HeartRateVariabilityRmssdRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.heartRateVariabilityMillis),
            field = "heartRateVariabilityMillis",
        )
        is LeanBodyMassRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.mass.inKilograms),
            field = "mass",
        )
        is OxygenSaturationRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.percentage.value),
            field = "percentage",
        )
        is RespiratoryRateRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.rate),
            field = "rate",
        )
        is RestingHeartRateRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = BigDecimal.valueOf(record.beatsPerMinute),
            field = "beatsPerMinute",
        )
        is BodyTemperatureRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.temperature.inCelsius),
            field = "temperature",
        ).located(location = record.measurementLocation)
        is BasalBodyTemperatureRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.temperature.inCelsius),
            field = "temperature",
        ).located(location = record.measurementLocation)
        is Vo2MaxRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.vo2MillilitersPerMinuteKilogram),
            field = "vo2MillilitersPerMinuteKilogram",
        ).measured(method = record.measurementMethod)
        is WeightRecord -> point(
            time = record.time,
            offset = record.zoneOffset,
            value = decimal(value = record.weight.inKilograms),
            field = "weight",
        )
        else -> null
    }

    @Suppress("detekt:LongMethod")
    private fun intervalReading(record: Record): Reading? = when (record) {
        is ActiveCaloriesBurnedRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = decimal(value = record.energy.inKilocalories),
            field = "energy",
        )
        is DistanceRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = decimal(value = record.distance.inMeters),
            field = "distance",
        )
        is ElevationGainedRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = decimal(value = record.elevation.inMeters),
            field = "elevation",
        )
        is FloorsClimbedRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = decimal(value = record.floors),
            field = "floors",
        )
        is HydrationRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = decimal(value = record.volume.inMilliliters),
            field = "volume",
        )
        is StepsRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = BigDecimal.valueOf(record.count),
            field = "count",
        )
        is TotalCaloriesBurnedRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = decimal(value = record.energy.inKilocalories),
            field = "energy",
        )
        is WheelchairPushesRecord -> interval(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
            value = BigDecimal.valueOf(record.count),
            field = "count",
        )
        else -> null
    }

    private fun seriesReadings(record: Record): List<Reading>? {
        val (offset, samples) = when (record) {
            is HeartRateRecord -> record.startZoneOffset to record.samples.mapIndexed { index, sample ->
                Triple(sample.time, BigDecimal.valueOf(sample.beatsPerMinute), "samples[$index].beatsPerMinute")
            }
            is SpeedRecord -> record.startZoneOffset to record.samples.mapIndexed { index, sample ->
                Triple(sample.time, decimal(value = sample.speed.inMetersPerSecond), "samples[$index].speed")
            }
            is PowerRecord -> record.startZoneOffset to record.samples.mapIndexed { index, sample ->
                Triple(sample.time, decimal(value = sample.power.inWatts), "samples[$index].power")
            }
            is StepsCadenceRecord -> record.startZoneOffset to record.samples.mapIndexed { index, sample ->
                Triple(sample.time, decimal(value = sample.rate), "samples[$index].rate")
            }
            is CyclingPedalingCadenceRecord -> record.startZoneOffset to record.samples.mapIndexed { index, sample ->
                Triple(sample.time, decimal(value = sample.revolutionsPerMinute), "samples[$index].revolutionsPerMinute")
            }
            else -> return null
        }
        return samples.map { (time, value, field) ->
            Reading(
                effective = times.mapDateTime(instant = time, offset = offset),
                value = value,
                field = field,
                sampleTime = time,
                statesOffset = offset != null,
            )
        }
    }

    /**
     * This reading at the body site [location] names, none for an unknown location.
     */
    private fun Reading.located(location: Int): Reading {
        if (!HealthConnectCatalog.temperatureLocations.containsKey(location)) return copy(unsupportedField = "measurementLocation")
        return copy(bodySite = HealthConnectCatalog.temperatureLocations[location]?.let { CodeableConcept(coding = listOf(it)) })
    }

    /**
     * This reading measured by the exact source [method].
     */
    private fun Reading.measured(method: Int): Reading {
        val coding = HealthConnectCatalog.vo2MaxMethods[method] ?: return copy(unsupportedField = "measurementMethod")
        return copy(method = CodeableConcept(coding = listOf(coding)))
    }

    private fun point(time: Instant, offset: ZoneOffset?, value: BigDecimal?, field: String) = Reading(
        effective = times.mapDateTime(instant = time, offset = offset),
        value = value,
        field = field,
        sampleTime = null,
        statesOffset = offset != null,
    )

    @Suppress("detekt:LongParameterList")
    private fun interval(
        start: Instant,
        startOffset: ZoneOffset?,
        end: Instant,
        endOffset: ZoneOffset?,
        value: BigDecimal?,
        field: String,
    ) = Reading(
        effective = times.mapPeriod(
            start = start,
            startOffset = startOffset,
            end = end,
            endOffset = endOffset,
        ),
        value = value,
        field = field,
        sampleTime = null,
        statesOffset = startOffset != null && endOffset != null,
    )

    /**
     * The shortest decimal that reads back as [value], or `null` when it is not finite.
     */
    private fun decimal(
        value: Double,
    ): BigDecimal? = FhirDecimal.of(value = value).getOrNull()?.let { BigDecimal(it.toString()) }

    private companion object {
        const val CENTIMETERS_PER_METER_EXPONENT = 2
    }
}
