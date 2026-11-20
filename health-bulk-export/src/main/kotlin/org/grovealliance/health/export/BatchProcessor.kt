//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

import androidx.health.connect.client.records.Record
import org.grovealliance.health.AnyRecordType

/**
 * Processes the records of one batch of a [BulkExportSession], for example by uploading them.
 *
 * Batches cover consecutive windows of time, but a record spanning the edge of a window may be
 * handed over twice; deduplicate by record identifier where that matters.
 */
interface BatchProcessor<Output> {

    /**
     * Processes the [records] of [recordType] in one batch.
     *
     * A failure marks the batch as failed; the session moves on to the next batch and retries
     * failed batches only when started with `retryFailedBatches`. Cancellation, when the session
     * is paused or terminated, leaves the batch pending.
     */
    suspend fun process(records: List<Record>, recordType: AnyRecordType): Result<Output>

    /**
     * Called once the session has checkpointed that [output]'s batch is done, to release state
     * kept only for retrying it.
     *
     * The process can die between the checkpoint and this call, so the work must be idempotent
     * and limited to cleanup.
     */
    suspend fun didPersist(output: Output) = Unit
}
