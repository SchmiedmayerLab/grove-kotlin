//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study.internal

import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.RecordType

/**
 * The HealthKit sample type identifiers that have a Health Connect record type measuring the same
 * thing.
 */
internal object HealthKitSampleTypes {
    @OptIn(ExperimentalMindfulnessSessionApi::class)
    private val recordTypes: Map<String, AnyRecordType> = mapOf(
        "HKQuantityTypeIdentifierActiveEnergyBurned" to RecordType.activeCaloriesBurned,
        "HKQuantityTypeIdentifierBasalBodyTemperature" to RecordType.basalBodyTemperature,
        "HKQuantityTypeIdentifierBloodGlucose" to RecordType.bloodGlucose,
        "HKCorrelationTypeIdentifierBloodPressure" to RecordType.bloodPressure,
        "HKQuantityTypeIdentifierBodyFatPercentage" to RecordType.bodyFat,
        "HKQuantityTypeIdentifierBodyTemperature" to RecordType.bodyTemperature,
        "HKQuantityTypeIdentifierDistanceWalkingRunning" to RecordType.distance,
        "HKWorkoutTypeIdentifier" to RecordType.exerciseSession,
        "HKQuantityTypeIdentifierFlightsClimbed" to RecordType.floorsClimbed,
        "HKQuantityTypeIdentifierHeartRate" to RecordType.heartRate,
        "HKQuantityTypeIdentifierHeight" to RecordType.height,
        "HKQuantityTypeIdentifierLeanBodyMass" to RecordType.leanBodyMass,
        "HKCategoryTypeIdentifierMindfulSession" to RecordType.mindfulnessSession,
        "HKQuantityTypeIdentifierOxygenSaturation" to RecordType.oxygenSaturation,
        "HKQuantityTypeIdentifierRespiratoryRate" to RecordType.respiratoryRate,
        "HKQuantityTypeIdentifierRestingHeartRate" to RecordType.restingHeartRate,
        "HKCategoryTypeIdentifierSleepAnalysis" to RecordType.sleepSession,
        "HKQuantityTypeIdentifierStepCount" to RecordType.steps,
        "HKQuantityTypeIdentifierVO2Max" to RecordType.vo2Max,
        "HKQuantityTypeIdentifierBodyMass" to RecordType.weight,
        "HKQuantityTypeIdentifierPushCount" to RecordType.wheelchairPushes,
    )

    /**
     * The record type for [identifier], or `null` when Health Connect has none that measures the same
     * thing.
     */
    fun recordType(identifier: String): AnyRecordType? = recordTypes[identifier]
}
