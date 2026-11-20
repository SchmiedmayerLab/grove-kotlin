//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import androidx.health.connect.client.records.Record
import org.grovealliance.core.coroutines.runCatchingAsync
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.Health
import org.grovealliance.health.HealthQueryTimeRange
import java.time.Instant

/**
 * Reads the records a bulk export processes.
 */
internal interface ExportRecordReader {

    /**
     * The records of [recordType] from [start] to [end].
     */
    suspend fun read(recordType: AnyRecordType, start: Instant, end: Instant): Result<List<Record>>

    /**
     * The start of the oldest record of [recordType], or `null` when there is none or it can't be read.
     */
    suspend fun oldestRecordStart(recordType: AnyRecordType): Instant?
}

/**
 * Default [ExportRecordReader] implementation, reading through [health].
 */
internal class ExportRecordReaderImpl(
    private val health: Health,
) : ExportRecordReader {

    override suspend fun read(recordType: AnyRecordType, start: Instant, end: Instant): Result<List<Record>> =
        runCatchingAsync {
            health.query(
                type = recordType,
                timeRange = HealthQueryTimeRange(start, end),
            )
        }

    override suspend fun oldestRecordStart(recordType: AnyRecordType): Instant? =
        runCatchingAsync { health.oldestSampleDate(type = recordType) }.getOrNull()
}
