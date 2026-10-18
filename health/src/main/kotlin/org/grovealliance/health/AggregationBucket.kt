//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

import java.time.Instant

/**
 * The aggregated value of one bucket of an aggregation split into [AggregationInterval]s.
 *
 * @property start the start of the bucket.
 * @property end the end of the bucket, exclusive.
 * @property value the aggregated value, in the unit of the aggregated metric, or `null` when the
 *   bucket holds no data.
 */
data class AggregationBucket<T : Any>(
    val start: Instant,
    val end: Instant,
    val value: T?,
)
