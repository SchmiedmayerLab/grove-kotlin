//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.export.BatchProcessor
import org.grovealliance.health.export.BulkExportException
import org.grovealliance.health.export.BulkExportSession
import org.grovealliance.health.export.BulkExportSessionId
import org.grovealliance.health.export.BulkHealthExporter
import org.grovealliance.health.export.ExportSessionBatchSize
import org.grovealliance.health.export.ExportSessionStartDate

/**
 * Default [BulkHealthExporter] implementation, keeping the sessions created in this process.
 */
internal class BulkHealthExporterImpl(
    private val reader: ExportRecordReader,
    private val planner: ExportBatchPlanner,
    private val checkpoints: ExportCheckpointStore,
    private val scope: CoroutineScope,
) : BulkHealthExporter {
    private val lock = Mutex()
    private val sessions = mutableMapOf<BulkExportSessionId, BulkExportSessionImpl<*>>()

    override suspend fun <Output> session(
        id: BulkExportSessionId,
        recordTypes: Set<AnyRecordType>,
        startDate: ExportSessionStartDate,
        batchSize: ExportSessionBatchSize,
        processor: BatchProcessor<Output>,
    ): Result<BulkExportSession<Output>> = lock.withLock {
        sessions[id]?.let { existing ->
            return@withLock if (existing.processor::class == processor::class) {
                @Suppress("UNCHECKED_CAST")
                Result.success(existing as BulkExportSession<Output>)
            } else {
                Result.failure(BulkExportException.ConflictingSession(id = id))
            }
        }
        checkpoints.load(id = id, recordTypes = recordTypes).map { restored ->
            val batches = restored
                ?: RestoredBatches(
                    pending = planner.plan(
                        recordTypes = recordTypes,
                        startDate = startDate,
                        batchSize = batchSize,
                    ),
                    completed = emptyList(),
                )
            BulkExportSessionImpl(
                id = id,
                processor = processor,
                reader = reader,
                checkpoints = checkpoints,
                scope = scope,
                batches = batches,
            ).also { sessions[id] = it }
        }
    }

    override suspend fun deleteSession(id: BulkExportSessionId): Result<Unit> {
        val session = lock.withLock { sessions.remove(id) }
        session?.terminate()
        return checkpoints.delete(id = id)
    }
}
