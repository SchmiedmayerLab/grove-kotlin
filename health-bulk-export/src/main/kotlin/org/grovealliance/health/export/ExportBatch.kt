//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

import org.grovealliance.health.AnyRecordType
import java.time.Instant

/**
 * The records of one [recordType] in the window from [start] (inclusive) to [end] (exclusive).
 *
 * @property outcome the result of processing the batch, or `null` while it hasn't been processed
 */
data class ExportBatch(
    val recordType: AnyRecordType,
    val start: Instant,
    val end: Instant,
    val outcome: Result<Unit>?,
)
