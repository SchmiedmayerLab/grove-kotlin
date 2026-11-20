//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import androidx.health.connect.client.records.Record
import org.grovealliance.health.AnyRecordType
import java.time.Instant

/**
 * An [ExportRecordReader] that returns no records, remembering every window read, and fails the
 * reads of the record types set with [setFailing].
 */
internal class FakeExportRecordReader : ExportRecordReader {
    private val oldest = mutableMapOf<AnyRecordType, Instant>()
    private val failing = mutableSetOf<AnyRecordType>()
    private val _reads = mutableListOf<Read>()

    /**
     * Every window read so far, in order.
     */
    val reads: List<Read> get() = synchronized(_reads) { _reads.toList() }

    /**
     * Sets the start of the oldest record of [recordType].
     */
    fun setOldestRecordStart(recordType: AnyRecordType, start: Instant) {
        oldest[recordType] = start
    }

    /**
     * Sets the record types whose reads fail.
     */
    fun setFailing(recordTypes: Set<AnyRecordType>) {
        failing.clear()
        failing += recordTypes
    }

    override suspend fun read(recordType: AnyRecordType, start: Instant, end: Instant): Result<List<Record>> {
        synchronized(_reads) {
            _reads += Read(
                recordType = recordType,
                start = start,
                end = end,
            )
        }
        return if (recordType in failing) Result.failure(IllegalStateException("Reading failed.")) else Result.success(emptyList())
    }

    override suspend fun oldestRecordStart(recordType: AnyRecordType): Instant? = oldest[recordType]

    /**
     * One window read.
     */
    data class Read(
        val recordType: AnyRecordType,
        val start: Instant,
        val end: Instant,
    )
}
