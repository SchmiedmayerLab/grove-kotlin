//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study

import org.grovealliance.health.AnyRecordType
import org.grovealliance.study.internal.HealthKitSampleTypes

/**
 * Maps the sample types a study definition declares for health data collection to the Health Connect
 * record types collected for them.
 */
fun interface StudyHealthDataMapping {
    /**
     * The record types collected for [sampleType], or an empty set when nothing is collected for it.
     *
     * [sampleType] is as the study definition declares it: a HealthKit identifier, usually preceded by
     * its kind, as in `HKQuantityType;HKQuantityTypeIdentifierStepCount`.
     */
    fun recordTypes(sampleType: String): Set<AnyRecordType>

    companion object {
        /**
         * Maps each HealthKit sample type identifier to the Health Connect record type that measures
         * the same thing, and nothing else.
         *
         * Sample types without such a record type map to nothing, including ones where a record type
         * of a similar name measures something different: heart rate variability is SDNN in HealthKit
         * but RMSSD in Health Connect, and basal energy burned is an energy total where Health
         * Connect's basal metabolic rate is a rate.
         */
        val Default: StudyHealthDataMapping = StudyHealthDataMapping { sampleType ->
            setOfNotNull(HealthKitSampleTypes.recordType(identifier = sampleType.substringAfter(KIND_SEPARATOR)))
        }

        private const val KIND_SEPARATOR = ";"
    }
}
