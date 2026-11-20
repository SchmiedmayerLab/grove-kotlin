//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import androidx.health.connect.client.records.Record
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.export.BatchProcessor

/**
 * A [BatchProcessor] that outputs the number of the batch it processed, counting from 1, fails
 * the batches of the record types set with [setFailing], and calls the action set with
 * [setOnProcess] before each batch.
 */
class RecordingBatchProcessor : BatchProcessor<Int> {
    private val failing = mutableSetOf<AnyRecordType>()
    private var onProcess: suspend (Int) -> Unit = {}
    private var processed = 0
    private val _persisted = mutableListOf<Int>()

    /**
     * The outputs acknowledged through [didPersist], in order.
     */
    val persisted: List<Int> get() = _persisted.toList()

    /**
     * Sets the record types whose batches fail.
     */
    fun setFailing(recordTypes: Set<AnyRecordType>) {
        failing.clear()
        failing += recordTypes
    }

    /**
     * Sets the action called with the batch's number before each batch is processed.
     */
    fun setOnProcess(action: suspend (Int) -> Unit) {
        onProcess = action
    }

    override suspend fun process(records: List<Record>, recordType: AnyRecordType): Result<Int> {
        val number = synchronized(this) { ++processed }
        onProcess(number)
        return if (recordType in failing) Result.failure(IllegalStateException("Processing failed.")) else Result.success(number)
    }

    override suspend fun didPersist(output: Int) {
        synchronized(_persisted) { _persisted += output }
    }
}
