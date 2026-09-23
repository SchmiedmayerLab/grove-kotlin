//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:OptIn(InternalGroveFhirApi::class, ExperimentalMindfulnessSessionApi::class)

package org.grovealliance.health.fhir

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import org.grovealliance.fhir.GraphEntry
import org.grovealliance.fhir.InternalGroveFhirApi
import org.grovealliance.fhir.RouteDisclosurePolicy
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Observation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Sleep, exercise and mindfulness sessions: one summary Observation with per-stage or per-segment members. */
internal fun RecordConversion.convertSession(): HealthConnectConversionResult? = when (val record = record) {
    is SleepSessionRecord -> convertSleep(record)
    is ExerciseSessionRecord -> convertExercise(record)
    is MindfulnessSessionRecord -> convertMindfulness(record)
    else -> null
}

private fun RecordConversion.convertMindfulness(record: MindfulnessSessionRecord): HealthConnectConversionResult {
    val spec = spec("mindfulness-session")
    val minutes = durationOf(record.startTime, record.endTime, NANOSECONDS_PER_MINUTE)
    val output = observation(spec, singleOutput("mindfulness-session"), disclosesNativeIdentifier = true) {
        effective = period(record.span(), type.token)
        value = quantity(requireNotNull(spec.quantity), minutes)
        method = CodeableConcept(
            sourceOnlyCoding(
                HealthConnectContextMappings.mindfulnessSessionType,
                HealthConnectSourceTokens.mindfulnessSessionType,
                record.mindfulnessSessionType.at("mindfulnessSessionType"),
            ),
        )
        retainSessionText(this, record.title, record.notes)
    }
    return finish(output)
}

private fun RecordConversion.convertSleep(record: SleepSessionRecord): HealthConnectConversionResult {
    val hours = durationOf(record.startTime, record.endTime, NANOSECONDS_PER_HOUR)
    val stageSpec = spec("sleep-stage")
    val stageSystem = requireNotNull(stageSpec.resultCodeSystem)
    val offset = spanOffset(record.startZoneOffset, record.endZoneOffset)
    // The platform list order is the only stable slot identity for otherwise identical stages.
    val stages = assignSourceListOccurrences(record.stages) { Triple(it.startTime, it.endTime, it.stage) }
        .sortedWith(compareBy({ it.first.startTime }, { it.first.endTime }, { it.first.stage }, { it.second }))
        .mapIndexed { index, (stage, occurrence) ->
            val field = "${type.token}.stages[$index]"
            if (!stage.startTime.isBefore(stage.endTime) || stage.startTime < record.startTime || stage.endTime > record.endTime) {
                refuse(HealthConnectValueFailure.EffectivePeriodInvalid(field))
            }
            val token = HealthConnectSourceTokens.sleepStage.token(stage.stage, "$field.stage")
            val coded = HealthConnectContextMappings.sleepStage.value(token)
                ?: refuse(HealthConnectValueFailure.UnsupportedSourceValue("$field.stage"))
            val discriminator = listOf(
                HealthConnectTime.utc9(stage.startTime, "$field.startTime"),
                HealthConnectTime.utc9(stage.endTime, "$field.endTime"),
                token,
                occurrence.toString(),
            ).joinToString(DISCRIMINATOR_SEPARATOR)
            observation(stageSpec, output(SLEEP_STAGE_ROLE, discriminator)) {
                effective = period(stage.startTime.at(offset) until stage.endTime.at(offset), "${type.token}.stages")
                value = codedValue(HealthConnectContextMappings.sleepStage, stageSystem, coded)
            }
        }
    val summarySpec = spec("sleep-duration")
    val summary = observation(summarySpec, singleOutput("sleep-duration"), disclosesNativeIdentifier = true) {
        effective = period(record.span(), type.token)
        value = quantity(requireNotNull(summarySpec.quantity), hours)
        stages.forEach { addHasMember(it.reference()) }
        retainSessionText(this, record.title, record.notes)
    }
    return finish(summary, stages)
}

private fun RecordConversion.convertExercise(record: ExerciseSessionRecord): HealthConnectConversionResult {
    withheldExerciseFields(record).takeIf { it.isNotEmpty() }?.let {
        warn(HealthConnectConversionWarning.UnmodeledMetadataWithheld(it.sorted()))
    }
    val summarySpec = spec("workout")
    val activity = HealthConnectWorkoutVocabulary.activity(record.exerciseType)
    val children = workoutSegments(record) + workoutLaps(record)
    val summary = observation(summarySpec, singleOutput("workout"), disclosesNativeIdentifier = true) {
        effective = period(record.span(), type.token)
        value = codedValue(
            HealthConnectContextMappings.exerciseType.asSourceMapping(),
            requireNotNull(summarySpec.resultCodeSystem),
            activity,
        )
        children.forEach { addHasMember(it.reference()) }
        retainSessionText(this, record.title, record.notes)
    }
    return finish(summary, children)
}

private fun RecordConversion.withheldExerciseFields(record: ExerciseSessionRecord): Set<String> = buildSet {
    val routePresent = record.exerciseRouteResult !is ExerciseRouteResult.NoData
    if (routePresent && context.options.routeDisclosure == RouteDisclosurePolicy.AUTHORIZED) {
        add("${type.token}.exerciseRouteResult")
    }
    if (record.plannedExerciseSessionId != null) add("${type.token}.plannedExerciseSessionId")
}

private fun RecordConversion.workoutSegments(record: ExerciseSessionRecord): List<GraphEntry> =
    assignSourceListOccurrences(record.segments) { Triple(it.startTime, it.endTime, it.segmentType) }
        .sortedWith(compareBy({ it.first.startTime }, { it.first.endTime }, { it.first.segmentType }, { it.second }))
        .mapIndexed { index, (segment, occurrence) ->
            val field = "${type.token}.segments[$index]"
            requireWorkoutInterval(record, segment.startTime, segment.endTime, field)
            val classification = HealthConnectWorkoutVocabulary.segment(segment.segmentType)
            workoutSegment(segment.startTime, segment.endTime, occurrence, classification, field) {
                if (segment.repetitions > 0) {
                    val repetitions = spec("workout-segment").component("repetitions")
                    addComponent().apply {
                        code = repetitions.code.concept()
                        value = quantity(requireNotNull(repetitions.quantity), segment.repetitions.toBigDecimal())
                    }
                }
            }
        }

private fun RecordConversion.workoutLaps(record: ExerciseSessionRecord): List<GraphEntry> =
    assignSourceListOccurrences(record.laps) { it.startTime to it.endTime }
        .sortedWith(compareBy({ it.first.startTime }, { it.first.endTime }, { it.second }))
        .mapIndexed { index, (lap, occurrence) ->
            val field = "${type.token}.laps[$index]"
            requireWorkoutInterval(record, lap.startTime, lap.endTime, field)
            workoutSegment(lap.startTime, lap.endTime, occurrence, HealthConnectWorkoutVocabulary.lap(), field) {
                lap.length?.let { length ->
                    val lapLength = spec("workout-segment").component("lap-length")
                    val quantitySpec = requireNotNull(lapLength.quantity)
                    addComponent().apply {
                        code = lapLength.code.concept()
                        value = quantity(quantitySpec, decimal(length.inMeters.at("laps[$index].length"), quantitySpec))
                    }
                }
            }
        }

private fun RecordConversion.workoutSegment(
    start: Instant,
    end: Instant,
    occurrence: Int,
    classification: WorkoutClassification,
    field: String,
    configure: Observation.() -> Unit,
): GraphEntry {
    val spec = spec("workout-segment")
    val discriminator = listOf(
        HealthConnectTime.utc9(start, "$field.startTime"),
        HealthConnectTime.utc9(end, "$field.endTime"),
        classification.value.source,
        occurrence.toString(),
    ).joinToString(DISCRIMINATOR_SEPARATOR)
    val session = record as ExerciseSessionRecord
    val offset = spanOffset(session.startZoneOffset, session.endZoneOffset)
    return observation(spec, output(WORKOUT_SEGMENT_ROLE, discriminator)) {
        effective = period(start.at(offset) until end.at(offset), field.substringBefore('['))
        value = codedValue(
            HealthConnectContextMappings.exerciseSegmentType.asSourceMapping(),
            classification.sharedSystem,
            classification.value,
        )
        configure()
    }
}

private fun RecordConversion.requireWorkoutInterval(record: ExerciseSessionRecord, start: Instant, end: Instant, field: String) {
    if (!start.isBefore(end) || start < record.startTime || end > record.endTime) {
        refuse(HealthConnectValueFailure.EffectivePeriodInvalid(field))
    }
}

private fun RecordConversion.durationOf(start: Instant, end: Instant, nanosecondsPerUnit: BigDecimal): BigDecimal {
    val nanos = ChronoUnit.NANOS.between(start, end)
    if (nanos <= 0L) refuse(HealthConnectValueFailure.EffectivePeriodInvalid(type.token))
    val duration = BigDecimal.valueOf(nanos).divide(nanosecondsPerUnit, DURATION_SCALE, RoundingMode.HALF_EVEN).stripTrailingZeros()
    return if (duration.scale() < 0) duration.setScale(0) else duration
}

private fun MindfulnessSessionRecord.span() = startTime.at(startZoneOffset) until endTime.at(endZoneOffset)

private fun SleepSessionRecord.span() = startTime.at(startZoneOffset) until endTime.at(endZoneOffset)

private fun ExerciseSessionRecord.span() = startTime.at(startZoneOffset) until endTime.at(endZoneOffset)

private fun AllowedSourceCodes.asSourceMapping(): SourceCodedMapping = SourceCodedMapping(sourceSystem, appliesToMeasurement, emptyList())

private const val SLEEP_STAGE_ROLE = "sleep-stage"
private const val WORKOUT_SEGMENT_ROLE = "workout-segment"
private const val DISCRIMINATOR_SEPARATOR = "|"
private const val DURATION_SCALE = 12
private val NANOSECONDS_PER_HOUR = BigDecimal("3600000000000")
private val NANOSECONDS_PER_MINUTE = BigDecimal("60000000000")
