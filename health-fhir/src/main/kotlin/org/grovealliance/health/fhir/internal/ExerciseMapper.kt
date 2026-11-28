//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:ReturnCount")

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import org.grovealliance.fhir.contract.Annotation
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.Extension
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Observation
import org.grovealliance.fhir.contract.ProducerDiagnostic
import org.grovealliance.fhir.contract.WorkoutSegmentType
import org.grovealliance.health.fhir.HealthConnectConversionError
import org.grovealliance.health.fhir.HealthConnectConversionError.ValueFailure
import org.grovealliance.health.fhir.HealthConnectFhirExporter
import java.time.Instant

/**
 * The outputs of an exercise session: one workout summary over the session, with its shared activity,
 * its exact exercise type, and its title and note when user-authored text travels; then one
 * workout-segment Observation per segment and per lap, in source order, each listed by the summary.
 */
@OptIn(InternalGroveFhirApi::class)
internal class ExerciseMapper(
    private val times: SourceTimeMapper,
    private val observations: ObservationMapper,
    private val projection: ExerciseProjection,
    private val userText: HealthConnectFhirExporter.Options.UserText,
) {

    /**
     * One child interval of a session: its bounds, exact source coding and shared coding, and where it was read from.
     */
    private data class Child(
        val start: Instant,
        val end: Instant,
        val source: Coding,
        val shared: Coding,
        val field: String,
    )

    fun mapOutputs(record: ExerciseSessionRecord): Result<OutputMapper.Outputs> {
        val source = HealthConnectCatalog.exerciseTypes[record.exerciseType]
            ?: return Result.failure(failure(failure = ValueFailure.UNSUPPORTED_VALUE, field = "exerciseType"))
        val children = children(record = record).getOrElse { return Result.failure(it) }
        children.firstOrNull { it.start < record.startTime || it.end > record.endTime }?.let {
            return Result.failure(failure(failure = ValueFailure.EFFECTIVE_PERIOD_INVALID, field = it.field))
        }
        val effective = times.mapPeriod(
            start = record.startTime,
            startOffset = record.startZoneOffset,
            end = record.endTime,
            endOffset = record.endZoneOffset,
        )
        val activity = shared(
            system = Measurements.WORKOUT.resultCodeSystem,
            code = projection.mapActivity(type = record.exerciseType).code,
        )
        val summary = ExchangeOutputDraft(
            role = OutputRole.SINGLE.value,
            discriminator = Measurements.WORKOUT.id,
            resource = ExchangeOutputDraft.Resource.ObservationResource(
                observation = described(
                    observation = observation(record = record, effective = effective, shared = activity, source = source, isSummary = true),
                    record = record,
                ),
            ),
        )
        val segments = segmentDrafts(record = record, children = children)
        val statesOffset = record.startZoneOffset != null && record.endZoneOffset != null
        val offsetWarnings = if (statesOffset) emptyList() else times.mapOffsetWarnings(effectives = listOf(effective))
        return Result.success(
            OutputMapper.Outputs(
                drafts = listOf(summary) + segments,
                warnings = offsetWarnings + omissions(record = record),
            ),
        )
    }

    /**
     * One workout-segment output per child, each numbered among children with the same coordinates.
     */
    private fun segmentDrafts(record: ExerciseSessionRecord, children: List<Child>): List<ExchangeOutputDraft> {
        val occurrences = mutableMapOf<String, Int>()
        return children.map { child ->
            val coordinates = listOf(
                times.mapDiscriminator(instant = child.start),
                times.mapDiscriminator(instant = child.end),
                requireNotNull(child.source.code),
            ).joinToString(separator = "|")
            ExchangeOutputDraft(
                role = OutputRole.WORKOUT_SEGMENT.value,
                discriminator = "$coordinates|${occurrences.merge(coordinates, 1, Int::plus)?.minus(1)}",
                resource = ExchangeOutputDraft.Resource.ObservationResource(
                    observation = observation(
                        record = record,
                        effective = times.mapPeriod(
                            start = child.start,
                            startOffset = record.startZoneOffset,
                            end = child.end,
                            endOffset = record.startZoneOffset,
                        ),
                        shared = child.shared,
                        source = child.source,
                        isSummary = false,
                    ),
                ),
                memberOfPrimary = true,
            )
        }
    }

    /**
     * Every segment, then every lap, as the session lists them.
     */
    private fun children(record: ExerciseSessionRecord): Result<List<Child>> {
        val segments = record.segments.mapIndexed { index, segment ->
            val source = HealthConnectCatalog.exerciseSegmentTypes[segment.segmentType]
                ?: return Result.failure(failure(failure = ValueFailure.UNSUPPORTED_VALUE, field = "segments[$index].segmentType"))
            val projected = projection.mapSegment(type = segment.segmentType)
            val vocabulary = if (projected.isStructural) Measurements.WORKOUT_SEGMENT else Measurements.WORKOUT
            val system = vocabulary.resultCodeSystem
            Child(
                start = segment.startTime,
                end = segment.endTime,
                source = source,
                shared = shared(system = system, code = projected.code),
                field = "segments[$index].startTime",
            )
        }
        val laps = record.laps.mapIndexed { index, lap ->
            Child(
                start = lap.startTime,
                end = lap.endTime,
                source = HealthConnectCatalog.exerciseLap,
                shared = shared(system = Measurements.WORKOUT_SEGMENT.resultCodeSystem, code = WorkoutSegmentType.LAP.code),
                field = "laps[$index].startTime",
            )
        }
        return Result.success(segments + laps)
    }

    private fun observation(
        record: ExerciseSessionRecord,
        effective: Observation.Effective,
        shared: Coding,
        source: Coding,
        isSummary: Boolean,
    ): Observation = observations.mapObservation(
        claim = observations.mapClaim(measurement = if (isSummary) Measurements.WORKOUT else Measurements.WORKOUT_SEGMENT),
        recordType = RECORD_TYPE,
        metadata = record.metadata,
        effective = effective,
        issued = times.mapIssued(instant = record.metadata.lastModifiedTime),
        value = Observation.Value.CodeableConceptValue(value = CodeableConcept(coding = listOf(shared, source))),
    )

    private fun shared(system: String?, code: String) = observations.displayed(coding = Coding(system = system, code = code))

    /**
     * [observation] with the session's title and note when user-authored text travels and they aren't blank.
     */
    private fun described(observation: Observation, record: ExerciseSessionRecord): Observation {
        val retains = userText == HealthConnectFhirExporter.Options.UserText.RETAIN
        val title = record.title?.takeIf { retains && it.isNotBlank() }?.let {
            Extension(
                url = HealthConnectCatalog.SESSION_TITLE_EXTENSION,
                value = Extension.Value.StringValue(value = it),
            )
        }
        val notes = record.notes?.takeIf { retains && it.isNotBlank() }
        return observation.copy(
            extension = observation.extension + listOfNotNull(title),
            note = listOfNotNull(notes?.let { Annotation(text = it) }),
        )
    }

    /**
     * One `mobile-omission.unmodeled-metadata` warning per session fact no output states: segment repetitions, lap lengths and the route.
     */
    private fun omissions(record: ExerciseSessionRecord): List<ProducerDiagnostic> = listOfNotNull(
        "$RECORD_TYPE.segments.repetitions".takeIf { record.segments.any { it.repetitions > 0 } },
        "$RECORD_TYPE.laps.length".takeIf { record.laps.any { it.length != null } },
        "$RECORD_TYPE.exerciseRouteResult".takeIf { record.exerciseRouteResult is ExerciseRouteResult.Data },
    ).map { ExchangeGraphRule.MOBILE_OMISSION_UNMODELED_METADATA.diagnostic(location = it) }

    private fun failure(failure: ValueFailure, field: String) = HealthConnectConversionError.InvalidValue(
        failure = failure,
        location = "$RECORD_TYPE.$field",
    )

    private companion object {
        const val RECORD_TYPE = "ExerciseSessionRecord"
    }
}
