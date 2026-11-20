//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One resumable export, created by [BulkHealthExporter.session].
 */
interface BulkExportSession<Output> {

    /**
     * The session's identifier.
     */
    val id: BulkExportSessionId

    /**
     * The session's state.
     */
    val state: StateFlow<BulkExportSessionState>

    /**
     * The session's state; see [state].
     */
    val currentState: BulkExportSessionState

    /**
     * The session's batches and how far it got through them.
     */
    val progress: StateFlow<BulkExportSessionProgress>

    /**
     * The session's batches and how far it got through them; see [progress].
     */
    val currentProgress: BulkExportSessionProgress

    /**
     * Starts processing the pending batches, and returns the outputs of the batches completed
     * in this run, each emitted once its completion is checkpointed. The flow completes when the
     * run ends, and can be collected only once.
     *
     * When a checkpoint could not be written, the session pauses with
     * [BulkExportPauseReason.CheckpointWriteFailed] and keeps the outputs it could not emit; the
     * next start writes the checkpoint again and then emits them, without processing their
     * batches again. Outputs are only kept in memory: a process that dies in between may process
     * a batch again, or never emit an output whose completion was saved.
     *
     * @param retryFailedBatches whether the batches that failed are processed again
     * @param concurrencyLevel how many batches are processed at the same time
     * @return a [BulkExportException.AlreadyRunning] or [BulkExportException.Terminated] failure
     * when the session can't be started
     */
    suspend fun start(
        retryFailedBatches: Boolean = false,
        concurrencyLevel: BulkExportConcurrencyLevel = BulkExportConcurrencyLevel.Automatic,
    ): Result<Flow<Output>>

    /**
     * Pauses a running session and suspends until its batches in progress have stopped and the
     * checkpoint is written. A checkpoint failure takes precedence over the requested pause in
     * [state].
     */
    suspend fun pause()
}

/**
 * The state of a [BulkExportSession].
 */
sealed interface BulkExportSessionState {

    /**
     * The session isn't running, for the given [reason].
     */
    data class Paused(val reason: BulkExportPauseReason) : BulkExportSessionState

    /**
     * The session is processing batches.
     */
    data object Running : BulkExportSessionState

    /**
     * Every batch was processed successfully and the final checkpoint was written.
     */
    data object Completed : BulkExportSessionState

    /**
     * The session was deleted with [BulkHealthExporter.deleteSession] and can't be started again.
     */
    data object Terminated : BulkExportSessionState
}

/**
 * Why a [BulkExportSession] is paused.
 */
sealed interface BulkExportPauseReason {

    /**
     * The session was created or restored and hasn't been started in this process.
     */
    data object NotStarted : BulkExportPauseReason

    /**
     * [BulkExportSession.pause] was called. Failed batches may be present as well.
     */
    data object Requested : BulkExportPauseReason

    /**
     * Every other batch is done; the failed ones are retried by starting with
     * `retryFailedBatches`.
     */
    data object FailedBatches : BulkExportPauseReason

    /**
     * The checkpoint could not be written, with [message] describing why. Starting again retries
     * it; failed batches may be present as well.
     */
    data class CheckpointWriteFailed(val message: String) : BulkExportPauseReason
}

/**
 * How many batches a [BulkExportSession] processes at the same time.
 */
sealed interface BulkExportConcurrencyLevel {

    /**
     * One batch at a time.
     */
    data object Disabled : BulkExportConcurrencyLevel

    /**
     * At most [limit] batches at a time.
     */
    data class Limit(val limit: Int) : BulkExportConcurrencyLevel

    /**
     * Every pending batch at once.
     */
    data object Unlimited : BulkExportConcurrencyLevel

    companion object {

        /**
         * The level used when none is given: every pending batch at once.
         */
        val Automatic: BulkExportConcurrencyLevel = Unlimited
    }
}

/**
 * The batches of a [BulkExportSession] and how far it got through them.
 *
 * @property completedBatches how many batches were processed successfully
 * @property failedBatches the batches whose processing failed
 * @property totalBatches how many batches the session has in all
 * @property activeBatches the batches being processed right now
 */
data class BulkExportSessionProgress(
    val completedBatches: Int,
    val failedBatches: List<ExportBatch>,
    val totalBatches: Int,
    val activeBatches: Set<ExportBatch>,
) {

    /**
     * The share of batches processed successfully, from 0 to 1.
     */
    val completion: Double
        get() = if (totalBatches == 0) 1.0 else minOf(1.0, completedBatches.toDouble() / totalBatches)
}
