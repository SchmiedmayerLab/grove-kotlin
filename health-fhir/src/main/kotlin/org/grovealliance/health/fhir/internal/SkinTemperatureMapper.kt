//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.SkinTemperatureRecord
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.Quantity
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure
import java.math.BigDecimal

/**
 * One skin temperature Observation per delta of a Record, at the delta's instant: the baseline plus
 * the delta in degrees Celsius, at the body site the Record states.
 */
@OptIn(InternalGroveFhirApi::class)
internal class SkinTemperatureMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
) {

    fun mapOutputs(record: SkinTemperatureRecord): Result<OutputMapper.Outputs> {
        if (record.deltas.isEmpty()) return Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = RECORD_TYPE))
        val baseline = record.baseline?.let { decimal(value = it.inCelsius) }
            ?: return Result.failure(failure(failure = ValueFailure.REQUIRED_COMPONENT_MISSING, field = "baseline"))
        if (!HealthConnectCatalog.skinTemperatureLocations.containsKey(record.measurementLocation)) {
            return Result.failure(failure(failure = ValueFailure.UNSUPPORTED_VALUE, field = "measurementLocation"))
        }
        val site = HealthConnectCatalog.skinTemperatureLocations[record.measurementLocation]?.let { CodeableConcept(coding = listOf(it)) }
        val measurement = Measurements.SKIN_TEMPERATURE
        val quantity = requireNotNull(measurement.quantity)
        val occurrences = mutableMapOf<String, Int>()
        val effectives = mutableListOf<Observation.Effective>()
        val drafts = record.deltas.mapIndexed { index, delta ->
            val value = decimal(value = delta.delta.inCelsius)?.add(baseline)
                ?.let { FhirDecimal.parse(lexeme = it.stripTrailingZeros().toPlainString()).getOrNull() }
                ?: return Result.failure(failure(failure = ValueFailure.OUTSIDE_DOMAIN, field = "deltas[$index].delta"))
            val effective = times.mapDateTime(instant = delta.time, offset = record.startZoneOffset)
            effectives += effective
            val time = times.mapDiscriminator(instant = delta.time)
            val observation = observations.mapObservation(
                claim = observations.mapClaim(measurement = measurement),
                recordType = RECORD_TYPE,
                metadata = record.metadata,
                effective = effective,
                issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
                value = Observation.Value.QuantityValue(
                    value = Quantity(
                        value = value,
                        unit = quantity.unit,
                        system = quantity.system,
                        code = quantity.code,
                    ),
                ),
            )
            ExchangeOutputDraft(
                role = OutputRole.SAMPLE.value,
                discriminator = "$time|${occurrences.merge(time, 1, Int::plus)?.minus(1)}",
                resource = ExchangeOutputDraft.Resource.ObservationResource(observation = observation.copy(bodySite = site)),
            )
        }
        return Result.success(
            OutputMapper.Outputs(
                drafts = drafts,
                warnings = if (record.startZoneOffset == null) times.mapOffsetWarnings(effectives = effectives) else emptyList(),
            ),
        )
    }

    private fun decimal(value: Double): BigDecimal? = FhirDecimal.of(value = value).getOrNull()?.let { BigDecimal(it.toString()) }

    private fun failure(failure: ValueFailure, field: String) = HealthConnectConversionError.InvalidValue(
        failure = failure,
        location = "$RECORD_TYPE.$field",
    )

    private companion object {
        const val RECORD_TYPE = "SkinTemperatureRecord"
    }
}
