//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(InternalGroveFhirApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import org.grovealliance.fhir.InternalGroveFhirApi
import org.hl7.fhir.r4.model.CodeableConcept
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

internal data class SeriesSample(val time: Instant, val value: Double, val exact: BigDecimal? = null)

/** Every source type that fans out into one Observation per sample; an empty series is an admitted zero-output result. */
internal fun RecordConversion.convertSeries(): HealthConnectConversionResult? = when (val record = record) {
    is HeartRateRecord -> sampleSeries(
        "heart-rate",
        record.startTime..record.endTime,
        spanOffset(record.startZoneOffset, record.endZoneOffset),
        record.samples.map { SeriesSample(it.time, it.beatsPerMinute.toDouble(), it.beatsPerMinute.toBigDecimal()) },
    )
    is CyclingPedalingCadenceRecord -> sampleSeries(
        "cycling-cadence",
        record.startTime..record.endTime,
        spanOffset(record.startZoneOffset, record.endZoneOffset),
        record.samples.map { SeriesSample(it.time, it.revolutionsPerMinute) },
    )
    is PowerRecord -> sampleSeries(
        "power",
        record.startTime..record.endTime,
        spanOffset(record.startZoneOffset, record.endZoneOffset),
        record.samples.map { SeriesSample(it.time, it.power.inWatts) },
    )
    is SpeedRecord -> sampleSeries(
        "speed",
        record.startTime..record.endTime,
        spanOffset(record.startZoneOffset, record.endZoneOffset),
        record.samples.map { SeriesSample(it.time, it.speed.inMetersPerSecond) },
    )
    is StepsCadenceRecord -> sampleSeries(
        "step-cadence",
        record.startTime..record.endTime,
        spanOffset(record.startZoneOffset, record.endZoneOffset),
        record.samples.map { SeriesSample(it.time, it.rate) },
    )
    is SkinTemperatureRecord -> convertSkinTemperature(record)
    else -> null
}

private fun RecordConversion.convertSkinTemperature(record: SkinTemperatureRecord): HealthConnectConversionResult {
    val baseline = record.baseline
    if (record.deltas.isNotEmpty() && baseline == null) {
        refuse(HealthConnectValueFailure.RequiredMetadataMissing("${type.token}.baseline"))
    }
    return sampleSeries(
        "skin-temperature",
        record.startTime..record.endTime,
        spanOffset(record.startZoneOffset, record.endZoneOffset),
        record.deltas.map { delta -> SeriesSample(delta.time, requireNotNull(baseline).inCelsius + delta.delta.inCelsius) },
        bodySite = externalConcept(
            HealthConnectContextMappings.skinTemperatureMeasurementLocation,
            HealthConnectSourceTokens.skinTemperatureMeasurementLocation,
            record.measurementLocation.at("measurementLocation"),
        ),
    )
}

private fun RecordConversion.sampleSeries(
    measurement: String,
    span: ClosedRange<Instant>,
    offset: ZoneOffset?,
    samples: List<SeriesSample>,
    bodySite: CodeableConcept? = null,
): HealthConnectConversionResult {
    if (span.start.isAfter(span.endInclusive)) refuse(HealthConnectValueFailure.EffectivePeriodInvalid("${type.token}.startTime"))
    val spec = spec(measurement)
    val quantitySpec = requireNotNull(spec.quantity) { "$measurement is a Quantity measurement." }
    // Occurrences bind equal-time samples to their exact platform slot before deterministic output sorting.
    val outputs = assignSourceListOccurrences(samples) { it.time }
        .sortedWith(compareBy({ it.first.time }, { it.second }))
        .mapIndexed { index, (sample, occurrence) ->
            val field = "${type.token}.samples[$index]"
            if (sample.time !in span) refuse(HealthConnectValueFailure.EffectivePeriodInvalid("$field.time"))
            val decimal = decimal(SourceValue(sample.value, "samples[$index].value", sample.exact), quantitySpec)
            val identity = output(SAMPLE_ROLE, "${HealthConnectTime.utc9(sample.time, "$field.time")}|$occurrence")
            observation(spec, identity) {
                effective = dateTime(sample.time.at(offset), "${type.token}.samples")
                value = quantity(quantitySpec, decimal)
                this.bodySite = bodySite?.copy()
            }
        }
    return finish(outputs.firstOrNull(), outputs.drop(1))
}

private const val SAMPLE_ROLE = "sample"
