//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.export.ExportBatch
import java.time.Instant

/**
 * Converts between a session's batches and its persisted [ExportCheckpoint].
 */
internal interface ExportCheckpointMapper {

    /**
     * The checkpoint of a session with [pending] and [completed] batches.
     */
    fun mapCheckpoint(pending: List<ExportBatch>, completed: List<ExportBatch>): ExportCheckpoint

    /**
     * The batches of [checkpoint] whose record type is one of [recordTypes]; failed batches come
     * back pending without their failure.
     */
    fun mapBatches(checkpoint: ExportCheckpoint, recordTypes: Set<AnyRecordType>): RestoredBatches
}

/**
 * The batches restored from a checkpoint.
 */
internal data class RestoredBatches(
    val pending: List<ExportBatch>,
    val completed: List<ExportBatch>,
)

/**
 * Default [ExportCheckpointMapper] implementation.
 */
internal class ExportCheckpointMapperImpl : ExportCheckpointMapper {

    override fun mapCheckpoint(pending: List<ExportBatch>, completed: List<ExportBatch>) = ExportCheckpoint(
        pending = pending.map { stored(batch = it) },
        completed = completed.map { stored(batch = it) },
    )

    override fun mapBatches(checkpoint: ExportCheckpoint, recordTypes: Set<AnyRecordType>): RestoredBatches {
        val byIdentifier = recordTypes.associateBy { it.identifier }
        fun restore(batches: List<StoredBatch>, outcome: Result<Unit>?) = batches.mapNotNull { stored ->
            byIdentifier[stored.recordType]?.let { recordType ->
                ExportBatch(
                    recordType = recordType,
                    start = Instant.ofEpochMilli(stored.start),
                    end = Instant.ofEpochMilli(stored.end),
                    outcome = outcome,
                )
            }
        }
        return RestoredBatches(
            pending = restore(batches = checkpoint.pending, outcome = null),
            completed = restore(batches = checkpoint.completed, outcome = Result.success(Unit)),
        )
    }

    private fun stored(batch: ExportBatch) = StoredBatch(
        recordType = batch.recordType.identifier,
        start = batch.start.toEpochMilli(),
        end = batch.end.toEpochMilli(),
        failure = batch.outcome?.exceptionOrNull()?.let { it.message ?: it.toString() },
    )
}
