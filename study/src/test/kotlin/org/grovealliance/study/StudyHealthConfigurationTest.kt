//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class StudyHealthConfigurationTest {

    @Test
    fun `default polls the default mapping every fifteen minutes`() {
        // when
        val configuration = StudyHealthConfiguration.DEFAULT

        // then
        assertThat(configuration.mapping).isSameInstanceAs(StudyHealthDataMapping.Default)
        assertThat(configuration.pollingInterval).isEqualTo(15.minutes)
    }

    @Test
    fun `rejects a zero polling interval`() {
        // when / then
        assertThrows(IllegalArgumentException::class.java) {
            StudyHealthConfiguration(
                mapping = StudyHealthDataMapping.Default,
                pollingInterval = Duration.ZERO,
            )
        }
    }

    @Test
    fun `rejects a negative polling interval`() {
        // when / then
        assertThrows(IllegalArgumentException::class.java) {
            StudyHealthConfiguration(
                mapping = StudyHealthDataMapping.Default,
                pollingInterval = (-1).minutes,
            )
        }
    }
}
