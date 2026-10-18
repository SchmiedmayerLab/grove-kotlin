//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study

import com.google.common.truth.Truth.assertThat
import org.grovealliance.health.RecordType
import org.junit.Test

class StudyHealthDataMappingTest {

    private val mapping = StudyHealthDataMapping.Default

    @Test
    fun `it should map a sample type to the record type measuring the same thing`() {
        // when
        val recordTypes = mapping.recordTypes(sampleType = "HKQuantityTypeIdentifierStepCount")

        // then
        assertThat(recordTypes).containsExactly(RecordType.steps)
    }

    @Test
    fun `it should map a sample type declared with its kind`() {
        // when
        val recordTypes = mapping.recordTypes(sampleType = "HKWorkoutType;HKWorkoutTypeIdentifier")

        // then
        assertThat(recordTypes).containsExactly(RecordType.exerciseSession)
    }

    @Test
    fun `it should map blood pressure correlations to blood pressure records`() {
        // when
        val recordTypes = mapping.recordTypes(sampleType = "HKCorrelationTypeIdentifierBloodPressure")

        // then
        assertThat(recordTypes).containsExactly(RecordType.bloodPressure)
    }

    @Test
    fun `it should not map heart rate variability measured differently`() {
        // when
        val recordTypes = mapping.recordTypes(sampleType = "HKQuantityTypeIdentifierHeartRateVariabilitySDNN")

        // then
        assertThat(recordTypes).isEmpty()
    }

    @Test
    fun `it should not map basal energy burned to basal metabolic rate`() {
        // when
        val recordTypes = mapping.recordTypes(sampleType = "HKQuantityTypeIdentifierBasalEnergyBurned")

        // then
        assertThat(recordTypes).isEmpty()
    }

    @Test
    fun `it should not map an unknown sample type`() {
        // when
        val recordTypes = mapping.recordTypes(sampleType = "HKQuantityTypeIdentifierAppleExerciseTime")

        // then
        assertThat(recordTypes).isEmpty()
    }
}
