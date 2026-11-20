//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

import java.time.Instant
import java.time.Period

/**
 * Where a new [BulkExportSession] starts, per record type.
 */
sealed interface ExportSessionStartDate {

    /**
     * At the oldest record of each record type.
     */
    data object OldestRecord : ExportSessionStartDate

    /**
     * The [period] before the session is created; a zero [period] means [OldestRecord].
     */
    data class Last(val period: Period) : ExportSessionStartDate

    /**
     * At [instant].
     */
    data class Absolute(val instant: Instant) : ExportSessionStartDate
}
