//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.export.BulkExportSessionId
import org.grovealliance.health.export.ExportBatch
import java.io.IOException

/**
 * An [ExportCheckpointStore] keeping checkpoints in memory, whose saves fail after [setSavesFailing].
 */
internal class FakeExportCheckpointStore : ExportCheckpointStore {
    private val checkpoints = mutableMapOf<BulkExportSessionId, RestoredBatches>()
    private var savesFailing = false

    /**
     * Sets whether saving a checkpoint fails.
     */
    fun setSavesFailing(failing: Boolean) {
        savesFailing = failing
    }

    /**
     * The batches last saved for [id].
     */
    fun saved(id: BulkExportSessionId): RestoredBatches? = checkpoints[id]

    override suspend fun load(id: BulkExportSessionId, recordTypes: Set<AnyRecordType>) = Result.success(
        checkpoints[id]?.let { batches ->
            RestoredBatches(
                pending = batches.pending.filter { it.recordType in recordTypes }.map { it.copy(outcome = null) },
                completed = batches.completed.filter { it.recordType in recordTypes },
            )
        },
    )

    override suspend fun save(
        id: BulkExportSessionId,
        pending: List<ExportBatch>,
        completed: List<ExportBatch>,
    ): Result<Unit> {
        if (savesFailing) return Result.failure(IOException("No space left on device."))
        checkpoints[id] = RestoredBatches(
            pending = pending.toList(),
            completed = completed.toList(),
        )
        return Result.success(Unit)
    }

    override suspend fun delete(id: BulkExportSessionId): Result<Unit> {
        checkpoints.remove(id)
        return Result.success(Unit)
    }
}
