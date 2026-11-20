//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

/**
 * How much time one batch of a [BulkExportSession] covers.
 *
 * Batches are aligned to calendar boundaries in the device's time zone; the first and the last
 * batch of a record type are cut to the session's start and end.
 */
sealed interface ExportSessionBatchSize {

    /**
     * Chosen per record type: a month for the record types with many records a day, such as steps
     * and heart rate, and six months for every other.
     */
    data object Automatic : ExportSessionBatchSize

    /**
     * [multiplier] units of [unit], for example two weeks; a [multiplier] below 1 counts as 1.
     */
    data class Calendar(
        val unit: Unit,
        val multiplier: Int = 1,
    ) : ExportSessionBatchSize

    /**
     * A calendar unit a batch can span. A week starts on Monday.
     */
    enum class Unit {
        YEAR,
        MONTH,
        WEEK,
        DAY,
        HOUR,
    }

    companion object {

        /**
         * One calendar year per batch.
         */
        val ByYear: ExportSessionBatchSize = Calendar(unit = Unit.YEAR)

        /**
         * One calendar month per batch.
         */
        val ByMonth: ExportSessionBatchSize = Calendar(unit = Unit.MONTH)
    }
}
