//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.grovealliance.core.logging.groveLogger
import org.grovealliance.health.export.BatchProcessor
import org.grovealliance.health.export.BulkExportConcurrencyLevel
import org.grovealliance.health.export.BulkExportException
import org.grovealliance.health.export.BulkExportPauseReason
import org.grovealliance.health.export.BulkExportSession
import org.grovealliance.health.export.BulkExportSessionId
import org.grovealliance.health.export.BulkExportSessionProgress
import org.grovealliance.health.export.BulkExportSessionState
import org.grovealliance.health.export.ExportBatch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Default [BulkExportSession] implementation: runs its [batches] on [scope], reads them with
 * [reader], hands them to [processor] and saves a checkpoint to [checkpoints] after each.
 *
 * Every change to the batches is saved while holding the session's lock, so checkpoints are
 * written in the order the changes happened.
 */
@Suppress("TooManyFunctions")
internal class BulkExportSessionImpl<Output>(
    override val id: BulkExportSessionId,
    val processor: BatchProcessor<Output>,
    private val reader: ExportRecordReader,
    private val checkpoints: ExportCheckpointStore,
    private val scope: CoroutineScope,
    batches: RestoredBatches,
) : BulkExportSession<Output> {
    private val logger by groveLogger()
    private val lock = Mutex()
    private val pending = batches.pending.toMutableList()
    private val completed = batches.completed.toMutableList()
    private val active = mutableSetOf<ExportBatch>()
    private val unpublished = LinkedHashMap<ExportBatch, Output>()
    private var stateChange: StateChange? = null
    private var checkpointFailure: String? = null
    private var runJob: Job? = null

    private val mutableState = MutableStateFlow<BulkExportSessionState>(
        BulkExportSessionState.Paused(reason = BulkExportPauseReason.NotStarted),
    )
    private val mutableProgress = MutableStateFlow(progressSnapshot())

    override val state = mutableState.asStateFlow()

    override val currentState get() = mutableState.value

    override val progress = mutableProgress.asStateFlow()

    override val currentProgress get() = mutableProgress.value

    override suspend fun start(
        retryFailedBatches: Boolean,
        concurrencyLevel: BulkExportConcurrencyLevel,
    ): Result<Flow<Output>> = lock.withLock {
        when {
            mutableState.value == BulkExportSessionState.Terminated -> return@withLock Result.failure(
                BulkExportException.Terminated(id = id),
            )
            runJob != null || mutableState.value == BulkExportSessionState.Running -> return@withLock Result.failure(
                BulkExportException.AlreadyRunning(id = id),
            )
        }
        if (retryFailedBatches) {
            pending.replaceAll { if (it.outcome?.isFailure == true) it.copy(outcome = null) else it }
        }
        checkpointFailure = null
        mutableState.value = BulkExportSessionState.Running
        publishProgress()
        val outputs = Channel<Output>(capacity = Channel.UNLIMITED)
        runJob = scope.launch { run(limit = concurrencyLevel.limit, outputs = outputs) }
        Result.success(outputs.receiveAsFlow())
    }

    override suspend fun pause() {
        val job = lock.withLock {
            if (mutableState.value != BulkExportSessionState.Running) return
            if (stateChange != StateChange.TERMINATED) stateChange = StateChange.PAUSED
            runJob
        }
        job?.cancelAndJoin()
    }

    /**
     * Stops the session for good, waiting for a run to stop, and drops the outputs it kept.
     */
    suspend fun terminate() {
        val job = lock.withLock {
            stateChange = StateChange.TERMINATED
            runJob
        }
        job?.cancelAndJoin()
        lock.withLock {
            unpublished.clear()
            mutableState.value = BulkExportSessionState.Terminated
            publishProgress()
        }
    }

    private suspend fun run(limit: Int, outputs: SendChannel<Output>) {
        try {
            if (saveCheckpoint()) {
                lock.withLock { unpublished.keys.toList() }.forEach { publish(batch = it, outputs = outputs) }
                val batches = lock.withLock { pending.filter { it.outcome == null } }
                val permits = Semaphore(permits = limit)
                coroutineScope {
                    batches.forEach { batch ->
                        launch { permits.withPermit { handle(batch = batch, outputs = outputs) } }
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                saveCheckpoint()
                finishRun(outputs = outputs)
            }
        }
    }

    private suspend fun handle(batch: ExportBatch, outputs: SendChannel<Output>) {
        if (!begin(batch = batch)) return
        val result = try {
            reader.read(recordType = batch.recordType, start = batch.start, end = batch.end)
                .fold(
                    onSuccess = { records -> processor.process(records = records, recordType = batch.recordType) },
                    onFailure = { Result.failure(it) },
                )
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { finish(batch = batch, result = null) }
            throw cancellation
        }
        result.exceptionOrNull()?.let { logger.e(it) { "Exporting ${batch.description} failed." } }
        if (finish(batch = batch, result = result)) {
            publish(batch = batch, outputs = outputs)
        }
    }

    private suspend fun begin(batch: ExportBatch): Boolean = lock.withLock {
        if (!currentCoroutineContext().isActive || stateChange != null || checkpointFailure != null) {
            return@withLock false
        }
        active += batch
        publishProgress()
        true
    }

    /**
     * Records the [result] of [batch], `null` when it was cancelled, and saves the checkpoint.
     *
     * @return whether the checkpoint was saved
     */
    private suspend fun finish(batch: ExportBatch, result: Result<Output>?): Boolean = lock.withLock {
        active -= batch
        pending.remove(batch)
        when {
            result == null -> pending.add(index = 0, element = batch)
            result.isSuccess -> {
                @Suppress("UNCHECKED_CAST")
                unpublished[batch.copy(outcome = SUCCESS)] = result.getOrNull() as Output
                completed += batch.copy(outcome = SUCCESS)
            }
            else -> pending += batch.copy(outcome = result.map {})
        }
        publishProgress()
        saveLocked()
    }

    private suspend fun publish(batch: ExportBatch, outputs: SendChannel<Output>) {
        val key = batch.copy(outcome = SUCCESS)
        val output = lock.withLock {
            if (!unpublished.containsKey(key)) return
            unpublished.remove(key)
        }
        @Suppress("UNCHECKED_CAST")
        outputs.trySend(output as Output)
        processor.didPersist(output = output)
    }

    private suspend fun saveCheckpoint(): Boolean = lock.withLock { saveLocked() }

    private suspend fun saveLocked(): Boolean {
        return checkpoints.save(
            id = id,
            pending = pending,
            completed = completed,
        )
            .onFailure { error ->
                if (checkpointFailure == null) {
                    checkpointFailure = error.message ?: error.toString()
                    logger.e(error) { "Saving the checkpoint of ${id.value} failed." }
                }
            }
            .isSuccess
    }

    private suspend fun finishRun(outputs: SendChannel<Output>) {
        lock.withLock {
            runJob = null
            active.clear()
            mutableState.value = when (stateChange) {
                StateChange.TERMINATED -> {
                    unpublished.clear()
                    BulkExportSessionState.Terminated
                }
                StateChange.PAUSED, null -> pausedOrCompleted()
            }
            stateChange = null
            publishProgress()
        }
        outputs.close()
    }

    private fun pausedOrCompleted(): BulkExportSessionState {
        val failure = checkpointFailure
        return when {
            failure != null -> BulkExportSessionState.Paused(
                reason = BulkExportPauseReason.CheckpointWriteFailed(message = failure),
            )
            stateChange == StateChange.PAUSED -> BulkExportSessionState.Paused(reason = BulkExportPauseReason.Requested)
            pending.isNotEmpty() -> BulkExportSessionState.Paused(reason = BulkExportPauseReason.FailedBatches)
            else -> BulkExportSessionState.Completed
        }
    }

    private fun publishProgress() {
        mutableProgress.value = progressSnapshot()
    }

    private fun progressSnapshot() = BulkExportSessionProgress(
        completedBatches = completed.size,
        failedBatches = pending.filter { it.outcome?.isFailure == true },
        totalBatches = pending.size + completed.size,
        activeBatches = active.toSet(),
    )

    private val ExportBatch.description get() = "${recordType.identifier} $start–$end"

    private val BulkExportConcurrencyLevel.limit
        get() = when (this) {
            BulkExportConcurrencyLevel.Disabled -> 1
            is BulkExportConcurrencyLevel.Limit -> maxOf(1, limit)
            BulkExportConcurrencyLevel.Unlimited -> Int.MAX_VALUE
        }

    private companion object {
        val SUCCESS: Result<Unit> = Result.success(Unit)
    }

    private enum class StateChange {
        PAUSED,
        TERMINATED,
    }
}
