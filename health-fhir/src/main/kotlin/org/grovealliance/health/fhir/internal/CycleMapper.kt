//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SexualActivityRecord
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.MeasurementContract
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure
import java.time.Instant
import java.time.ZoneOffset

/**
 * The one coded Observation of a cycle-tracking Record: its shared result, then the exact source
 * value where Health Connect states one, and a cervical mucus sensation as its component.
 */
@OptIn(InternalGroveFhirApi::class)
internal class CycleMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
) {

    /**
     * What one Record states: its [measurement], [effective] time and whether that carries the source's
     * offset, its [result] codings and its [component]s.
     */
    private data class Reading(
        val measurement: MeasurementContract,
        val effective: Observation.Effective,
        val statesOffset: Boolean,
        val result: List<Coding>,
        val component: List<Observation.Component> = emptyList(),
    )

    fun mapOutputs(record: Record, recordType: String): Result<OutputMapper.Outputs> {
        val reading = reading(record = record, recordType = recordType).getOrElse { return Result.failure(it) }
        val observation = observations.mapObservation(
            claim = observations.mapClaim(measurement = reading.measurement),
            recordType = recordType,
            metadata = record.metadata,
            effective = reading.effective,
            issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
            value = Observation.Value.CodeableConceptValue(value = CodeableConcept(coding = reading.result)),
            component = reading.component,
        )
        return Result.success(
            OutputMapper.Outputs(
                drafts = listOf(
                    ExchangeOutputDraft(
                        role = OutputRole.SINGLE.value,
                        discriminator = reading.measurement.id,
                        resource = ExchangeOutputDraft.Resource.ObservationResource(observation = observation),
                    ),
                ),
                warnings = if (reading.statesOffset) emptyList() else times.mapOffsetWarnings(effectives = listOf(reading.effective)),
            ),
        )
    }

    @Suppress("detekt:CyclomaticComplexMethod")
    private fun reading(record: Record, recordType: String): Result<Reading> = when (record) {
        is MenstruationFlowRecord -> coded(
            measurement = Measurements.MENSTRUATION_FLOW,
            value = HealthConnectCatalog.menstruationFlows[record.flow],
            location = "$recordType.flow",
        ).map { point(measurement = Measurements.MENSTRUATION_FLOW, time = record.time, offset = record.zoneOffset, result = it) }
        is OvulationTestRecord -> coded(
            measurement = Measurements.OVULATION_TEST_RESULT,
            value = HealthConnectCatalog.ovulationTestResults[record.result],
            location = "$recordType.result",
        ).map { point(measurement = Measurements.OVULATION_TEST_RESULT, time = record.time, offset = record.zoneOffset, result = it) }
        is SexualActivityRecord -> coded(
            measurement = Measurements.SEXUAL_ACTIVITY,
            value = HealthConnectCatalog.sexualActivityProtections[record.protectionUsed],
            location = "$recordType.protectionUsed",
        ).map { point(measurement = Measurements.SEXUAL_ACTIVITY, time = record.time, offset = record.zoneOffset, result = it) }
        is CervicalMucusRecord -> cervicalMucus(record = record, recordType = recordType)
        is IntermenstrualBleedingRecord -> Result.success(
            point(
                measurement = Measurements.INTERMENSTRUAL_BLEEDING,
                time = record.time,
                offset = record.zoneOffset,
                result = listOf(present(measurement = Measurements.INTERMENSTRUAL_BLEEDING)),
            ),
        )
        is MenstruationPeriodRecord -> Result.success(
            Reading(
                measurement = Measurements.MENSTRUATION_PERIOD,
                effective = times.mapPeriod(
                    start = record.startTime,
                    startOffset = record.startZoneOffset,
                    end = record.endTime,
                    endOffset = record.endZoneOffset,
                ),
                statesOffset = record.startZoneOffset != null && record.endZoneOffset != null,
                result = listOf(present(measurement = Measurements.MENSTRUATION_PERIOD)),
            ),
        )
        else -> Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = recordType))
    }

    private fun cervicalMucus(record: CervicalMucusRecord, recordType: String): Result<Reading> {
        val measurement = Measurements.CERVICAL_MUCUS_QUALITY
        val appearance = coded(
            measurement = measurement,
            value = HealthConnectCatalog.cervicalMucusAppearances[record.appearance],
            location = "$recordType.appearance",
        ).getOrElse { return Result.failure(it) }
        val component = if (record.sensation == CervicalMucusRecord.SENSATION_UNKNOWN) {
            null
        } else {
            val value = HealthConnectCatalog.cervicalMucusSensations[record.sensation]
                ?: return Result.failure(HealthConnectConversionError.InvalidValue(
                    failure = ValueFailure.UNSUPPORTED_VALUE,
                    location = "$recordType.sensation",
                ))
            val contract = measurement.components.single { it.id == "sensation" }
            Observation.Component(
                code = CodeableConcept(coding = listOf(Coding(system = contract.system, code = contract.code))),
                value = Observation.Value.CodeableConceptValue(
                    value = CodeableConcept(
                        coding = listOf(
                            result(system = contract.resultCodeSystem, code = value.shared, codes = contract.resultCodes),
                            value.source,
                        ),
                    ),
                ),
            )
        }
        return Result.success(
            point(
                measurement = measurement,
                time = record.time,
                offset = record.zoneOffset,
                result = appearance,
            ).copy(component = listOfNotNull(component)),
        )
    }

    private fun point(
        measurement: MeasurementContract,
        time: Instant,
        offset: ZoneOffset?,
        result: List<Coding>,
    ) = Reading(
        measurement = measurement,
        effective = times.mapDateTime(instant = time, offset = offset),
        statesOffset = offset != null,
        result = result,
    )

    /**
     * The shared result and exact source coding of a source constant, refused when the contract doesn't map it.
     */
    private fun coded(
        measurement: MeasurementContract,
        value: HealthConnectCodedValue?,
        location: String,
    ): Result<List<Coding>> =
        if (value == null) {
            Result.failure(HealthConnectConversionError.InvalidValue(failure = ValueFailure.UNSUPPORTED_VALUE, location = location))
        } else {
            val shared = result(system = measurement.resultCodeSystem, code = value.shared, codes = measurement.resultCodes)
            Result.success(listOf(shared, value.source))
        }

    private fun present(measurement: MeasurementContract) =
        result(system = measurement.resultCodeSystem, code = PRESENT, codes = measurement.resultCodes)

    private fun result(system: String?, code: String, codes: List<MeasurementContract.ResultCode>) = Coding(
        system = system,
        code = code,
        display = codes.firstOrNull { it.code == code }?.display,
    )

    private companion object {
        const val PRESENT = "present"
    }
}
