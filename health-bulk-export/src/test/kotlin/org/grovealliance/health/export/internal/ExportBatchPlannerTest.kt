//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.grovealliance.core.time.FakeTimeProvider
import org.grovealliance.health.RecordType
import org.grovealliance.health.export.ExportSessionBatchSize
import org.grovealliance.health.export.ExportSessionStartDate
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.Period
import java.time.ZoneId

class ExportBatchPlannerTest {
    private val reader = FakeExportRecordReader()
    private val timeProvider = FakeTimeProvider()
    private val planner = ExportBatchPlannerImpl(
        reader = reader,
        timeProvider = timeProvider,
    )

    @Before
    fun setup() {
        timeProvider.setZone(zone = ZoneId.of("Europe/Zurich"))
        timeProvider.setNow(instant = Instant.parse("2026-01-15T12:00:00Z"))
    }

    @Test
    fun `cuts the windows at calendar boundaries and at the session's start and end`() = runTest {
        // when
        val batches = planner.plan(
            recordTypes = setOf(RecordType.weight),
            startDate = ExportSessionStartDate.Absolute(instant = Instant.parse("2025-11-10T08:00:00Z")),
            batchSize = ExportSessionBatchSize.ByMonth,
        )

        // then
        assertThat(batches.map { it.start to it.end }).containsExactly(
            Instant.parse("2025-11-10T08:00:00Z") to Instant.parse("2025-11-30T23:00:00Z"),
            Instant.parse("2025-11-30T23:00:00Z") to Instant.parse("2025-12-31T23:00:00Z"),
            Instant.parse("2025-12-31T23:00:00Z") to Instant.parse("2026-01-15T12:00:00Z"),
        ).inOrder()
        assertThat(batches.all { it.outcome == null }).isTrue()
    }

    @Test
    fun `exports dense record types a month and others six months per batch`() = runTest {
        // when
        val batches = planner.plan(
            recordTypes = setOf(RecordType.steps, RecordType.weight),
            startDate = ExportSessionStartDate.Last(period = Period.ofYears(1)),
            batchSize = ExportSessionBatchSize.Automatic,
        )

        // then
        assertThat(batches.count { it.recordType == RecordType.steps }).isEqualTo(13)
        assertThat(batches.count { it.recordType == RecordType.weight }).isEqualTo(3)
    }

    @Test
    fun `starts at each record type's oldest record`() = runTest {
        // given
        reader.setOldestRecordStart(recordType = RecordType.weight, start = Instant.parse("2025-06-20T00:00:00Z"))

        // when
        val batches = planner.plan(
            recordTypes = setOf(RecordType.weight),
            startDate = ExportSessionStartDate.OldestRecord,
            batchSize = ExportSessionBatchSize.ByYear,
        )

        // then
        assertThat(batches.map { it.start }).containsExactly(
            Instant.parse("2025-06-20T00:00:00Z"),
            Instant.parse("2025-12-31T23:00:00Z"),
        ).inOrder()
    }

    @Test
    fun `plans no batch for a start after the end`() = runTest {
        // when
        val batches = planner.plan(
            recordTypes = setOf(RecordType.weight),
            startDate = ExportSessionStartDate.Absolute(instant = Instant.parse("2026-02-01T00:00:00Z")),
            batchSize = ExportSessionBatchSize.ByMonth,
        )

        // then
        assertThat(batches).isEmpty()
    }
}
