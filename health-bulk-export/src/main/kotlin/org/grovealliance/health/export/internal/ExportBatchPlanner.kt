//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import org.grovealliance.core.time.TimeProvider
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.RecordType
import org.grovealliance.health.export.ExportBatch
import org.grovealliance.health.export.ExportSessionBatchSize
import org.grovealliance.health.export.ExportSessionStartDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.Period
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Splits the time a new bulk export session covers into batches.
 */
internal interface ExportBatchPlanner {

    /**
     * The batches of [recordTypes] from [startDate] until now, each [batchSize] long, in the order
     * of [recordTypes] and then of time.
     */
    suspend fun plan(
        recordTypes: Set<AnyRecordType>,
        startDate: ExportSessionStartDate,
        batchSize: ExportSessionBatchSize,
    ): List<ExportBatch>
}

/**
 * Default [ExportBatchPlanner] implementation, finding oldest records with [reader] and aligning
 * batches to the calendar of [timeProvider]'s zone.
 */
internal class ExportBatchPlannerImpl(
    private val reader: ExportRecordReader,
    private val timeProvider: TimeProvider,
) : ExportBatchPlanner {

    override suspend fun plan(
        recordTypes: Set<AnyRecordType>,
        startDate: ExportSessionStartDate,
        batchSize: ExportSessionBatchSize,
    ): List<ExportBatch> {
        val end = timeProvider.nowZonedDateTime()
        return recordTypes.flatMap { recordType ->
            val start = start(recordType = recordType, startDate = startDate, end = end)
            val size = resolve(batchSize = batchSize, recordType = recordType)
            windows(
                start = start.atZone(end.zone),
                end = end,
                size = size,
            ).map { (windowStart, windowEnd) ->
                ExportBatch(
                    recordType = recordType,
                    start = windowStart,
                    end = windowEnd,
                    outcome = null,
                )
            }
        }
    }

    private suspend fun start(
        recordType: AnyRecordType,
        startDate: ExportSessionStartDate,
        end: ZonedDateTime,
    ): Instant = when (startDate) {
        ExportSessionStartDate.OldestRecord -> oldest(recordType = recordType)
        is ExportSessionStartDate.Last -> if (startDate.period == Period.ZERO) {
            oldest(recordType = recordType)
        } else {
            end.minus(startDate.period).toInstant()
        }
        is ExportSessionStartDate.Absolute -> startDate.instant
    }

    /**
     * The oldest record's start, or, when it can't be determined, a date old enough to cover any
     * record Health Connect is likely to hold.
     */
    private suspend fun oldest(recordType: AnyRecordType): Instant =
        reader.oldestRecordStart(recordType = recordType) ?: FALLBACK_START

    private fun resolve(batchSize: ExportSessionBatchSize, recordType: AnyRecordType): ExportSessionBatchSize.Calendar =
        when (batchSize) {
            is ExportSessionBatchSize.Calendar -> batchSize
            ExportSessionBatchSize.Automatic -> if (recordType in DENSE_RECORD_TYPES) {
                ExportSessionBatchSize.Calendar(unit = ExportSessionBatchSize.Unit.MONTH)
            } else {
                ExportSessionBatchSize.Calendar(
                    unit = ExportSessionBatchSize.Unit.MONTH,
                    multiplier = SPARSE_MONTHS,
                )
            }
        }

    private fun windows(
        start: ZonedDateTime,
        end: ZonedDateTime,
        size: ExportSessionBatchSize.Calendar,
    ): List<Pair<Instant, Instant>> {
        if (!start.isBefore(end)) return emptyList()
        val step = maxOf(1, size.multiplier).toLong()
        val windows = mutableListOf<Pair<Instant, Instant>>()
        var lower = truncate(dateTime = start, unit = size.unit)
        do {
            val upper = lower.plus(step, chronoUnit(unit = size.unit))
            windows += maxOf(lower, start).toInstant() to minOf(upper, end).toInstant()
            lower = upper
        } while (lower.isBefore(end))
        return windows
    }

    private fun truncate(dateTime: ZonedDateTime, unit: ExportSessionBatchSize.Unit): ZonedDateTime {
        val day = dateTime.truncatedTo(ChronoUnit.DAYS)
        return when (unit) {
            ExportSessionBatchSize.Unit.YEAR -> day.withDayOfYear(1)
            ExportSessionBatchSize.Unit.MONTH -> day.withDayOfMonth(1)
            ExportSessionBatchSize.Unit.WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ExportSessionBatchSize.Unit.DAY -> day
            ExportSessionBatchSize.Unit.HOUR -> dateTime.truncatedTo(ChronoUnit.HOURS)
        }
    }

    private fun chronoUnit(unit: ExportSessionBatchSize.Unit) = when (unit) {
        ExportSessionBatchSize.Unit.YEAR -> ChronoUnit.YEARS
        ExportSessionBatchSize.Unit.MONTH -> ChronoUnit.MONTHS
        ExportSessionBatchSize.Unit.WEEK -> ChronoUnit.WEEKS
        ExportSessionBatchSize.Unit.DAY -> ChronoUnit.DAYS
        ExportSessionBatchSize.Unit.HOUR -> ChronoUnit.HOURS
    }

    private companion object {
        val FALLBACK_START: Instant = Instant.parse("2014-06-02T00:00:00Z")
        const val SPARSE_MONTHS = 6

        /**
         * Record types with many records a day, exported a month per batch.
         */
        val DENSE_RECORD_TYPES: Set<AnyRecordType> = setOf(
            RecordType.activeCaloriesBurned,
            RecordType.basalMetabolicRate,
            RecordType.distance,
            RecordType.heartRate,
            RecordType.steps,
        )
    }
}
