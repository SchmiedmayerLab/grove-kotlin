//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

import org.grovealliance.health.AnyRecordType

/**
 * Exports historical Health Connect records in batches that survive process death.
 *
 * A session splits the time from its start date until the moment it is first created into
 * batches per record type, and hands each batch's records to a [BatchProcessor]. Progress is
 * checkpointed after every batch, so a session created again with the same [BulkExportSessionId]
 * after the process died resumes with the batches still pending instead of starting over.
 *
 * ```kotlin
 * val session = exporter.session(
 *     id = BulkExportSessionId(value = "historical"),
 *     recordTypes = setOf(RecordType.steps, RecordType.heartRate),
 *     startDate = ExportSessionStartDate.Last(period = Period.ofYears(10)),
 *     processor = uploader,
 * ).getOrThrow()
 * session.start().getOrThrow().collect { output -> … }
 * ```
 *
 * The exporter reads whatever Health Connect returns: record types it may not read yield empty
 * batches, and records older than 30 days need the history read permission.
 */
interface BulkHealthExporter {

    /**
     * Returns the session identified by [id], creating it on first use in this process.
     *
     * A session already created in this process is returned as it is, whatever the other
     * arguments say; it must have been created with a [processor] of the same class, or the
     * result is a [BulkExportException.ConflictingSession]. Otherwise a checkpoint left by an
     * earlier process is restored, with its failed batches queued again; the record types, start
     * date and batch size given here then only describe the record types to resolve, and record
     * types missing from [recordTypes] are dropped from the restored session. Without a
     * checkpoint, a new session covers [recordTypes] from [startDate] until now.
     *
     * The returned session is paused until [BulkExportSession.start] is called.
     */
    suspend fun <Output> session(
        id: BulkExportSessionId,
        recordTypes: Set<AnyRecordType>,
        startDate: ExportSessionStartDate,
        batchSize: ExportSessionBatchSize = ExportSessionBatchSize.Automatic,
        processor: BatchProcessor<Output>,
    ): Result<BulkExportSession<Output>>

    /**
     * Terminates the session identified by [id], if one was created in this process, and deletes
     * its checkpoint, so that the next [session] call starts over.
     *
     * Suspends until a running session has stopped, which takes as long as its processor needs to
     * react to cancellation.
     */
    suspend fun deleteSession(id: BulkExportSessionId): Result<Unit>
}

/**
 * Identifies a [BulkExportSession] across processes.
 */
@JvmInline
value class BulkExportSessionId(val value: String)

/**
 * Why an operation of the [BulkHealthExporter] or a [BulkExportSession] was refused.
 */
sealed class BulkExportException(message: String) : Exception(message) {

    /**
     * A session with the same identifier but a processor of another class already exists.
     */
    class ConflictingSession(id: BulkExportSessionId) :
        BulkExportException("A session ${id.value} with a different processor already exists.")

    /**
     * The session is already running.
     */
    class AlreadyRunning(id: BulkExportSessionId) : BulkExportException("The session ${id.value} is already running.")

    /**
     * The session was terminated and can't be started again.
     */
    class Terminated(id: BulkExportSessionId) : BulkExportException("The session ${id.value} was terminated.")
}
