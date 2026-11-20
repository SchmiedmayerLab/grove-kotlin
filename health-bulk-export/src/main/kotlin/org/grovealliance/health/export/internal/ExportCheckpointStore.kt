//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import kotlinx.serialization.Serializable
import org.grovealliance.core.coroutines.runCatchingAsync
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.export.BulkExportSessionId
import org.grovealliance.health.export.ExportBatch
import org.grovealliance.storage.local.LocalStorage
import org.grovealliance.storage.local.LocalStorageSetting

/**
 * Keeps the checkpoint of each bulk export session.
 */
internal interface ExportCheckpointStore {

    /**
     * The batches checkpointed for the session [id] whose record type is one of [recordTypes], or
     * `null` when there is no checkpoint. Failed batches come back pending without their failure.
     */
    suspend fun load(id: BulkExportSessionId, recordTypes: Set<AnyRecordType>): Result<RestoredBatches?>

    /**
     * Replaces the checkpoint of the session [id] with its [pending] and [completed] batches.
     */
    suspend fun save(
        id: BulkExportSessionId,
        pending: List<ExportBatch>,
        completed: List<ExportBatch>,
    ): Result<Unit>

    /**
     * Deletes the checkpoint of the session [id].
     */
    suspend fun delete(id: BulkExportSessionId): Result<Unit>
}

/**
 * The persisted progress of a bulk export session: its batches still to do, failed ones
 * included, and those done.
 */
@Serializable
internal data class ExportCheckpoint(
    val pending: List<StoredBatch>,
    val completed: List<StoredBatch>,
)

/**
 * A persisted export batch: the record type by identifier, the window in epoch milliseconds, and
 * the [failure] message of a failed batch.
 */
@Serializable
internal data class StoredBatch(
    val recordType: String,
    val start: Long,
    val end: Long,
    val failure: String? = null,
)

/**
 * Default [ExportCheckpointStore] implementation, keeping checkpoints in [storage] in the form
 * [mapper] converts them to.
 */
internal class ExportCheckpointStoreImpl(
    private val storage: LocalStorage,
    private val mapper: ExportCheckpointMapper,
) : ExportCheckpointStore {

    override suspend fun load(id: BulkExportSessionId, recordTypes: Set<AnyRecordType>) = runCatchingAsync {
        storage.read(
            key = key(id = id),
            settings = LocalStorageSetting.Unencrypted,
            serializer = ExportCheckpoint.serializer(),
        )?.let { mapper.mapBatches(checkpoint = it, recordTypes = recordTypes) }
    }

    override suspend fun save(
        id: BulkExportSessionId,
        pending: List<ExportBatch>,
        completed: List<ExportBatch>,
    ) = runCatchingAsync {
        storage.store(
            key = key(id = id),
            value = mapper.mapCheckpoint(pending = pending, completed = completed),
            settings = LocalStorageSetting.Unencrypted,
            serializer = ExportCheckpoint.serializer(),
        )
    }

    override suspend fun delete(id: BulkExportSessionId) = runCatchingAsync { storage.delete(key = key(id = id)) }

    private fun key(id: BulkExportSessionId) = "Health.bulkExport.${id.value}"
}
