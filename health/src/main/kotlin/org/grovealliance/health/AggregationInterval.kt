//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

import java.time.Period
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The length of the buckets an aggregation over a time range is split into. Buckets start at the
 * start of the range.
 */
sealed interface AggregationInterval {

    /**
     * Buckets of exactly [duration], such as hours, regardless of local time changes.
     *
     * @property duration the length of each bucket. Must be positive.
     */
    data class Fixed(val duration: Duration) : AggregationInterval {
        init {
            require(duration.isPositive()) { "duration must be positive" }
        }
    }

    /**
     * Buckets of [period] in the device's local time, such as days or weeks, so that a day bucket
     * spans 23 or 25 hours when the clocks change.
     *
     * @property period the length of each bucket. Must be positive.
     */
    data class Calendar(val period: Period) : AggregationInterval {
        init {
            require(!period.isNegative && !period.isZero) { "period must be positive" }
        }
    }

    companion object {
        /**
         * Buckets of one hour.
         */
        val hourly: AggregationInterval = Fixed(duration = 1.hours)

        /**
         * Buckets of one local day.
         */
        val daily: AggregationInterval = Calendar(period = Period.ofDays(1))

        /**
         * Buckets of one local week.
         */
        val weekly: AggregationInterval = Calendar(period = Period.ofWeeks(1))

        /**
         * Buckets of one local month.
         */
        val monthly: AggregationInterval = Calendar(period = Period.ofMonths(1))
    }
}
