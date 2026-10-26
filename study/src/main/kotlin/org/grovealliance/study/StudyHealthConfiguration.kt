//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How the [StudyManager] configures the `Health` module from the studies' health data collection
 * components.
 *
 * @property mapping how the studies' health data sample types map to Health Connect record types.
 * @property pollingInterval how often the record types collected for enrolled studies are polled.
 *   Must be positive.
 */
data class StudyHealthConfiguration(
    val mapping: StudyHealthDataMapping,
    val pollingInterval: Duration,
) {
    init {
        require(pollingInterval.isPositive()) { "pollingInterval must be positive" }
    }

    companion object {
        /**
         * The [StudyHealthDataMapping.Default] mapping, polled every 15 minutes.
         */
        val DEFAULT = StudyHealthConfiguration(
            mapping = StudyHealthDataMapping.Default,
            pollingInterval = 15.minutes,
        )
    }
}
