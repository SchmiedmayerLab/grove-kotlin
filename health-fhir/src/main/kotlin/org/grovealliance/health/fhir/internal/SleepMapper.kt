//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.SleepSessionRecord
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure

/**
 * One sleep-stage Observation per stage of a sleep session, in the session's order: the stage's exact
 * interval, its shared stage, then its exact source stage. The session itself states no summary.
 */
@OptIn(InternalGroveFhirApi::class)
internal class SleepMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
) {

    fun mapOutputs(record: SleepSessionRecord): Result<OutputMapper.Outputs> {
        if (record.stages.isEmpty()) return Result.failure(HealthConnectConversionError.NotYetConvertible(recordType = RECORD_TYPE))
        val occurrences = mutableMapOf<String, Int>()
        val effectives = mutableListOf<Observation.Effective>()
        val drafts = record.stages.mapIndexed { index, stage ->
            val value = HealthConnectCatalog.sleepStages[stage.stage]
                ?: return Result.failure(
                    HealthConnectConversionError.InvalidValue(
                        failure = ValueFailure.UNSUPPORTED_VALUE,
                        location = "$RECORD_TYPE.stages[$index].stage",
                    ),
                )
            if (stage.startTime < record.startTime || stage.endTime > record.endTime) {
                return Result.failure(
                    HealthConnectConversionError.InvalidValue(
                        failure = ValueFailure.EFFECTIVE_PERIOD_INVALID,
                        location = "$RECORD_TYPE.stages[$index].startTime",
                    ),
                )
            }
            val effective = times.mapPeriod(
                start = stage.startTime,
                startOffset = record.startZoneOffset,
                end = stage.endTime,
                endOffset = record.startZoneOffset,
            )
            effectives += effective
            val coordinates = listOf(
                times.mapDiscriminator(instant = stage.startTime),
                times.mapDiscriminator(instant = stage.endTime),
                requireNotNull(value.source.code),
            ).joinToString(separator = "|")
            val occurrence = occurrences.merge(coordinates, 1, Int::plus)?.minus(1)
            ExchangeOutputDraft(
                role = OutputRole.SLEEP_STAGE.value,
                discriminator = "$coordinates|$occurrence",
                resource = ExchangeOutputDraft.Resource.ObservationResource(
                    observation = stageObservation(record = record, effective = effective, value = value),
                ),
            )
        }
        return Result.success(
            OutputMapper.Outputs(
                drafts = drafts,
                warnings = if (record.startZoneOffset == null) times.mapOffsetWarnings(effectives = effectives) else emptyList(),
            ),
        )
    }

    private fun stageObservation(
        record: SleepSessionRecord,
        effective: Observation.Effective,
        value: HealthConnectCodedValue,
    ): Observation {
        val measurement = Measurements.SLEEP_STAGE
        return observations.mapObservation(
            claim = observations.mapClaim(measurement = measurement),
            recordType = RECORD_TYPE,
            metadata = record.metadata,
            effective = effective,
            issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
            value = Observation.Value.CodeableConceptValue(
                value = CodeableConcept(
                    coding = listOf(
                        observations.displayed(coding = Coding(system = measurement.resultCodeSystem, code = value.shared)),
                        value.source,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val RECORD_TYPE = "SleepSessionRecord"
    }
}
