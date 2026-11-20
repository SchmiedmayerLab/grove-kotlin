//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import androidx.health.connect.client.records.Record
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.grovealliance.core.time.FakeTimeProvider
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.RecordType
import org.grovealliance.health.export.BatchProcessor
import org.grovealliance.health.export.BulkExportConcurrencyLevel
import org.grovealliance.health.export.BulkExportException
import org.grovealliance.health.export.BulkExportPauseReason
import org.grovealliance.health.export.BulkExportSession
import org.grovealliance.health.export.BulkExportSessionId
import org.grovealliance.health.export.BulkExportSessionState
import org.grovealliance.health.export.BulkHealthExporter
import org.grovealliance.health.export.ExportSessionBatchSize
import org.grovealliance.health.export.ExportSessionStartDate
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.Period
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BulkHealthExporterTest {
    private val id = BulkExportSessionId(value = "historical")
    private val reader = FakeExportRecordReader()
    private val checkpoints = FakeExportCheckpointStore()
    private val processor = RecordingBatchProcessor()
    private val timeProvider = FakeTimeProvider()

    @Before
    fun setup() {
        timeProvider.setZone(zone = ZoneId.of("UTC"))
        timeProvider.setNow(instant = Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `processes every batch once and completes`() = runTest {
        // given
        val session = session(exporter = exporter(scope = backgroundScope))

        // when
        val outputs = session.start(concurrencyLevel = BulkExportConcurrencyLevel.Limit(limit = 3)).getOrThrow().toList()

        // then
        assertThat(session.currentState).isEqualTo(BulkExportSessionState.Completed)
        assertThat(outputs.sorted()).isEqualTo((1..24).toList())
        assertThat(reader.reads.distinct()).hasSize(24)
        assertThat(processor.persisted.sorted()).isEqualTo((1..24).toList())
        assertThat(checkpoints.saved(id = id)?.completed).hasSize(24)
    }

    @Test
    fun `pauses with the failed batches and retries only them`() = runTest {
        // given
        processor.setFailing(recordTypes = setOf(RecordType.weight))
        val session = session(exporter = exporter(scope = backgroundScope))
        session.start().getOrThrow().toList()
        processor.setFailing(recordTypes = emptySet())

        // when
        val outputs = session.start(retryFailedBatches = true).getOrThrow().toList()

        // then
        assertThat(outputs).hasSize(12)
        assertThat(reader.reads).hasSize(36)
        assertThat(session.currentState).isEqualTo(BulkExportSessionState.Completed)
    }

    @Test
    fun `keeps failed batches until retried`() = runTest {
        // given
        reader.setFailing(recordTypes = setOf(RecordType.weight))
        val session = session(exporter = exporter(scope = backgroundScope))

        // when
        session.start().getOrThrow().toList()

        // then
        assertThat(session.currentState)
            .isEqualTo(BulkExportSessionState.Paused(reason = BulkExportPauseReason.FailedBatches))
        assertThat(session.currentProgress.failedBatches).hasSize(12)
        assertThat(session.currentProgress.completedBatches).isEqualTo(12)
    }

    @Test
    fun `resumes after a pause without repeating finished batches`() = runTest {
        // given
        val session = session(exporter = exporter(scope = backgroundScope))
        processor.setOnProcess { number ->
            if (number == 5) {
                launch { session.pause() }
                awaitCancellation()
            }
        }
        session.start(concurrencyLevel = BulkExportConcurrencyLevel.Disabled).getOrThrow().toList()
        val paused = session.currentState
        val finishedBeforePause = session.currentProgress.completedBatches
        processor.setOnProcess {}

        // when
        session.start(concurrencyLevel = BulkExportConcurrencyLevel.Disabled).getOrThrow().toList()

        // then
        assertThat(paused).isEqualTo(BulkExportSessionState.Paused(reason = BulkExportPauseReason.Requested))
        assertThat(finishedBeforePause).isEqualTo(4)
        assertThat(session.currentState).isEqualTo(BulkExportSessionState.Completed)
        assertThat(checkpoints.saved(id = id)?.completed?.distinct()).hasSize(24)
        assertThat(reader.reads.distinct()).hasSize(24)
        assertThat(reader.reads).hasSize(25)
    }

    @Test
    fun `exports ten years of 25 record types across process deaths without gaps`() = runTest {
        // given
        val recordTypes = TWENTY_FIVE_RECORD_TYPES
        val kills = mutableListOf<Int>()
        var processed = 0

        // when
        repeat(3) { attempt ->
            val process = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val session = exporter(scope = process).session(
                id = id,
                recordTypes = recordTypes,
                startDate = ExportSessionStartDate.Last(period = Period.ofYears(10)),
                batchSize = ExportSessionBatchSize.ByMonth,
                processor = processor,
            ).getOrThrow()
            processor.setOnProcess { if (attempt < 2 && ++processed % 1000 == 0) process.cancel().also { kills += processed } }
            session.start(concurrencyLevel = BulkExportConcurrencyLevel.Limit(limit = 4)).getOrThrow()
            advanceUntilIdle()
        }

        // then
        val saved = checkpoints.saved(id = id)
        assertThat(kills).hasSize(2)
        assertThat(saved?.pending).isEmpty()
        assertThat(saved?.completed?.map { Triple(it.recordType, it.start, it.end) }?.toSet())
            .isEqualTo(reader.reads.map { Triple(it.recordType, it.start, it.end) }.toSet())
        assertThat(saved?.completed).hasSize(25 * 120)
        assertThat(saved?.completed?.distinct()).hasSize(25 * 120)
    }

    @Test
    fun `holds outputs while the checkpoint can't be written and emits them on the next start`() = runTest {
        // given
        val session = session(exporter = exporter(scope = backgroundScope))
        processor.setOnProcess { number -> if (number == 3) checkpoints.setSavesFailing(failing = true) }
        val firstRun = session.start(concurrencyLevel = BulkExportConcurrencyLevel.Disabled).getOrThrow().toList()
        checkpoints.setSavesFailing(failing = false)
        processor.setOnProcess {}

        // when
        val secondRun = session.start(concurrencyLevel = BulkExportConcurrencyLevel.Disabled).getOrThrow().toList()

        // then
        assertThat(firstRun).containsExactly(1, 2).inOrder()
        assertThat(secondRun.first()).isEqualTo(3)
        assertThat((firstRun + secondRun).sorted()).isEqualTo((1..24).toList())
        assertThat(reader.reads).hasSize(24)
        assertThat(session.currentState).isEqualTo(BulkExportSessionState.Completed)
    }

    @Test
    fun `reports a checkpoint that can't be written as the reason for pausing`() = runTest {
        // given
        val session = session(exporter = exporter(scope = backgroundScope))
        checkpoints.setSavesFailing(failing = true)

        // when
        session.start().getOrThrow().toList()

        // then
        assertThat(session.currentState).isEqualTo(
            BulkExportSessionState.Paused(
                reason = BulkExportPauseReason.CheckpointWriteFailed(message = "No space left on device."),
            ),
        )
        assertThat(reader.reads).isEmpty()
    }

    @Test
    fun `deleting a session terminates it and starts the next one over`() = runTest {
        // given
        val exporter = exporter(scope = backgroundScope)
        val session = session(exporter = exporter)
        session.start().getOrThrow().toList()

        // when
        exporter.deleteSession(id = id).getOrThrow()
        val next = session(exporter = exporter)

        // then
        assertThat(session.currentState).isEqualTo(BulkExportSessionState.Terminated)
        assertThat(session.start().exceptionOrNull()).isInstanceOf(BulkExportException.Terminated::class.java)
        assertThat(next.currentProgress.completedBatches).isEqualTo(0)
        assertThat(next.currentProgress.totalBatches).isEqualTo(24)
    }

    @Test
    fun `returns the session already created for an identifier`() = runTest {
        // given
        val exporter = exporter(scope = backgroundScope)
        val session = session(exporter = exporter)

        // when
        val again = session(exporter = exporter)

        // then
        assertThat(again).isSameInstanceAs(session)
    }

    @Test
    fun `refuses a session with a processor of another class under the same identifier`() = runTest {
        // given
        val exporter = exporter(scope = backgroundScope)
        session(exporter = exporter)

        // when
        val result = exporter.session(
            id = id,
            recordTypes = setOf(RecordType.steps),
            startDate = ExportSessionStartDate.Last(period = Period.ofYears(1)),
            processor = object : BatchProcessor<Unit> {
                override suspend fun process(records: List<Record>, recordType: AnyRecordType) = Result.success(Unit)
            },
        )

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(BulkExportException.ConflictingSession::class.java)
    }

    @Test
    fun `refuses to start a running session`() = runTest {
        // given
        val session = session(exporter = exporter(scope = backgroundScope))
        session.start().getOrThrow()

        // when
        val result = session.start()

        // then
        assertThat(result.exceptionOrNull()).isInstanceOf(BulkExportException.AlreadyRunning::class.java)
    }

    private fun exporter(scope: CoroutineScope): BulkHealthExporter = BulkHealthExporterImpl(
        reader = reader,
        planner = ExportBatchPlannerImpl(
            reader = reader,
            timeProvider = timeProvider,
        ),
        checkpoints = checkpoints,
        scope = scope,
    )

    private suspend fun session(exporter: BulkHealthExporter): BulkExportSession<Int> = exporter.session(
        id = id,
        recordTypes = setOf(RecordType.steps, RecordType.weight),
        startDate = ExportSessionStartDate.Last(period = Period.ofYears(1)),
        batchSize = ExportSessionBatchSize.ByMonth,
        processor = processor,
    ).getOrThrow()

    private companion object {
        val TWENTY_FIVE_RECORD_TYPES = setOf(
            RecordType.activeCaloriesBurned,
            RecordType.basalBodyTemperature,
            RecordType.basalMetabolicRate,
            RecordType.bloodGlucose,
            RecordType.bloodPressure,
            RecordType.bodyFat,
            RecordType.bodyTemperature,
            RecordType.bodyWaterMass,
            RecordType.boneMass,
            RecordType.cervicalMucus,
            RecordType.distance,
            RecordType.heartRate,
            RecordType.heartRateVariabilityRmssd,
            RecordType.steps,
            RecordType.stepsCadence,
            RecordType.weight,
            RecordType.height,
            RecordType.hydration,
            RecordType.nutrition,
            RecordType.oxygenSaturation,
            RecordType.respiratoryRate,
            RecordType.restingHeartRate,
            RecordType.sleepSession,
            RecordType.speed,
            RecordType.totalCaloriesBurned,
        )
    }
}
