//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

import com.google.common.truth.Truth.assertThat
import org.grovealliance.health.fhir.HealthConnectFhirExporter
import org.junit.Test

class OutputMapperCoverageTest {
    private val times = SourceTimeMapper()
    private val observations = ObservationMapper()
    private val mapper = OutputMapper(
        times = times,
        observations = observations,
        quantities = QuantityReadingMapper(times = times),
        bloodPressure = BloodPressureMapper(times = times, observations = observations),
        bloodGlucose = BloodGlucoseMapper(times = times, observations = observations),
        mindfulness = MindfulnessMapper(
            times = times,
            observations = observations,
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
        ),
        sleep = SleepMapper(times = times, observations = observations),
        exercise = ExerciseMapper(
            times = times,
            observations = observations,
            projection = ExerciseProjection(),
            userText = HealthConnectFhirExporter.Options.UserText.OMIT,
        ),
        nutrition = NutritionMapper(times = times, observations = observations),
        skinTemperature = SkinTemperatureMapper(times = times, observations = observations),
        cycle = CycleMapper(times = times, observations = observations),
    )

    @Test
    fun `converts every record type the contract supports`() {
        // given
        val supported = HealthConnectCatalog.recordTypes.values
            .filter { it.status == HealthConnectRecordContract.Status.SUPPORTED }
            .map { it.token }

        // when
        val convertible = HealthConnectCatalog.tokens.filterKeys { mapper.converts(recordType = it) }.values

        // then
        assertThat(convertible).containsExactlyElementsIn(supported)
    }
}
