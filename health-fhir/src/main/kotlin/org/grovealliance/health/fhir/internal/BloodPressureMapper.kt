//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.BloodPressureRecord
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.Extension
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.Hl7Url
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.MeasurementContract
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.Quantity
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure

/**
 * The one blood pressure Observation of a Record: its systolic and diastolic components, with the
 * body position and measurement site it states.
 */
@OptIn(InternalGroveFhirApi::class)
internal class BloodPressureMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
) {

    fun mapOutputs(record: BloodPressureRecord): Result<OutputMapper.Outputs> {
        val measurement = Measurements.BLOOD_PRESSURE
        val position = context(table = HealthConnectCatalog.bloodPressureBodyPositions, value = record.bodyPosition, field = "bodyPosition")
            .getOrElse { return Result.failure(it) }
        val site = context(
            table = HealthConnectCatalog.bloodPressureLocations,
            value = record.measurementLocation,
            field = "measurementLocation",
        )
            .getOrElse { return Result.failure(it) }
        val components = listOf(
            component(measurement = measurement, id = "systolic", value = record.systolic.inMillimetersOfMercury),
            component(measurement = measurement, id = "diastolic", value = record.diastolic.inMillimetersOfMercury),
        ).map { it.getOrElse { error -> return Result.failure(error) } }
        val effective = times.mapDateTime(instant = record.time, offset = record.zoneOffset)
        val observation = observations.mapObservation(
            claim = observations.mapClaim(measurement = measurement),
            recordType = RECORD_TYPE,
            metadata = record.metadata,
            effective = effective,
            issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
            value = null,
            component = components,
        )
        val positioned = position?.let {
            observation.copy(
                extension = observation.extension + Extension(
                    url = Hl7Url.OBSERVATION_BODY_POSITION,
                    value = Extension.Value.CodeableConceptValue(value = CodeableConcept(coding = listOf(it))),
                ),
            )
        } ?: observation
        return Result.success(
            OutputMapper.Outputs(
                drafts = listOf(
                    ExchangeOutputDraft(
                        role = OutputRole.SINGLE.value,
                        discriminator = measurement.id,
                        resource = ExchangeOutputDraft.Resource.ObservationResource(
                            observation = positioned.copy(bodySite = site?.let { CodeableConcept(coding = listOf(it)) }),
                        ),
                    ),
                ),
                warnings = if (record.zoneOffset == null) times.mapOffsetWarnings(effectives = listOf(effective)) else emptyList(),
            ),
        )
    }

    /**
     * The coding [table] states for the source constant [value]; `null` for one the output omits.
     */
    private fun context(table: Map<Int, Coding?>, value: Int, field: String): Result<Coding?> =
        if (table.containsKey(value)) {
            Result.success(table[value])
        } else {
            Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.UNSUPPORTED_VALUE,
                location = "$RECORD_TYPE.$field",
            ))
        }

    private fun component(measurement: MeasurementContract, id: String, value: Double): Result<Observation.Component> {
        val contract = measurement.components.single { it.id == id }
        val quantity = requireNotNull(contract.quantity)
        val decimal = FhirDecimal.of(value = value).getOrElse {
            return Result.failure(HealthConnectConversionError.InvalidValue(
                failure = ValueFailure.OUTSIDE_DOMAIN,
                location = "$RECORD_TYPE.$id",
            ))
        }
        return Result.success(
            Observation.Component(
                code = CodeableConcept(coding = listOf(observations.displayed(coding = Coding(
                    system = contract.system,
                    code = contract.code,
                )))),
                value = Observation.Value.QuantityValue(
                    value = Quantity(
                        value = decimal,
                        unit = quantity.unit,
                        system = quantity.system,
                        code = quantity.code,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val RECORD_TYPE = "BloodPressureRecord"
    }
}
