//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(ExperimentalMindfulnessSessionApi::class)
@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.MindfulnessSessionRecord
import org.grovealliance.fhir.contract.Annotation
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.Extension
import org.grovealliance.fhir.contract.FhirDecimal
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.Quantity
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure
import org.grovealliance.health.fhir.HealthConnectFhirExporter
import java.time.Duration

/**
 * The one Observation of a mindfulness session: its duration in minutes over the session, its exact
 * session type as the method, and its title and note when user-authored text travels.
 */
@OptIn(InternalGroveFhirApi::class)
internal class MindfulnessMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
    private val userText: HealthConnectFhirExporter.Options.UserText,
) {

    fun mapOutputs(record: MindfulnessSessionRecord): Result<OutputMapper.Outputs> {
        val measurement = Measurements.MINDFULNESS_SESSION
        val method = HealthConnectCatalog.mindfulnessSessionTypes[record.mindfulnessSessionType]
            ?: return Result.failure(failure(failure = ValueFailure.UNSUPPORTED_VALUE, field = "mindfulnessSessionType"))
        val duration = Duration.between(record.startTime, record.endTime)
        if (duration.isNegative || duration.isZero) {
            return Result.failure(failure(
                failure = ValueFailure.EFFECTIVE_PERIOD_INVALID,
                field = "startTime",
            ))
        }
        val minutes = FhirDecimal.of(value = duration.toNanos() / NANOS_PER_SECOND / SECONDS_PER_MINUTE).getOrElse {
            return Result.failure(failure(failure = ValueFailure.OUTSIDE_DOMAIN, field = "endTime"))
        }
        val quantity = requireNotNull(measurement.quantity)
        val effective = times.mapPeriod(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
        )
        val observation = observations.mapObservation(
            claim = observations.mapClaim(measurement = measurement),
            recordType = RECORD_TYPE,
            metadata = record.metadata,
            effective = effective,
            issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
            value = Observation.Value.QuantityValue(
                value = Quantity(
                    value = minutes,
                    unit = quantity.unit,
                    system = quantity.system,
                    code = quantity.code,
                ),
            ),
        )
        val described = described(observation = observation, record = record).copy(method = CodeableConcept(coding = listOf(method)))
        val statesOffset = record.startZoneOffset != null && record.endZoneOffset != null
        return Result.success(
            OutputMapper.Outputs(
                drafts = listOf(
                    ExchangeOutputDraft(
                        role = OutputRole.SINGLE.value,
                        discriminator = measurement.id,
                        resource = ExchangeOutputDraft.Resource.ObservationResource(observation = described),
                    ),
                ),
                warnings = if (statesOffset) emptyList() else times.mapOffsetWarnings(effectives = listOf(effective)),
            ),
        )
    }

    /**
     * [observation] with the session's title and note when user-authored text travels and they aren't blank.
     */
    private fun described(observation: Observation, record: MindfulnessSessionRecord): Observation {
        val retains = userText == HealthConnectFhirExporter.Options.UserText.RETAIN
        val title = record.title?.takeIf { retains && it.isNotBlank() }
        val notes = record.notes?.takeIf { retains && it.isNotBlank() }
        val titleExtension = title?.let {
            Extension(
                url = HealthConnectCatalog.SESSION_TITLE_EXTENSION,
                value = Extension.Value.StringValue(value = it),
            )
        }
        return observation.copy(
            extension = observation.extension + listOfNotNull(titleExtension),
            note = listOfNotNull(notes?.let { Annotation(text = it) }),
        )
    }

    private fun failure(failure: ValueFailure, field: String) = HealthConnectConversionError.InvalidValue(
        failure = failure,
        location = "$RECORD_TYPE.$field",
    )

    private companion object {
        const val RECORD_TYPE = "MindfulnessSessionRecord"
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val SECONDS_PER_MINUTE = 60.0
    }
}
