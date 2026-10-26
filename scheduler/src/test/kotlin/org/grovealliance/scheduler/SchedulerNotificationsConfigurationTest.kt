//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.scheduler

import com.google.common.truth.Truth.assertThat
import org.grovealliance.resources.Strings
import org.junit.Test
import java.time.Duration

class SchedulerNotificationsConfigurationTest {

    @Test
    fun `default schedules four weeks ahead at nine in the morning`() {
        // when
        val configuration = SchedulerNotificationsConfiguration.DEFAULT

        // then
        assertThat(configuration.limit).isEqualTo(30)
        assertThat(configuration.window).isEqualTo(Duration.ofDays(28))
        assertThat(configuration.allDayNotificationTime).isEqualTo(NotificationTime(hour = 9))
    }

    @Test
    fun `default uses a generic icon and the grove channel`() {
        // when
        val configuration = SchedulerNotificationsConfiguration.DEFAULT

        // then
        assertThat(configuration.smallIcon).isEqualTo(android.R.drawable.ic_popup_reminder)
        assertThat(configuration.color).isNull()
        assertThat(configuration.channelName).isEqualTo(Strings.scheduler_notifications_channel_name)
        assertThat(configuration.channelDescription).isEqualTo(Strings.scheduler_notifications_channel_description)
    }

    @Test
    fun `rejects a non-positive limit`() {
        // when / then
        assertThrows<IllegalArgumentException> {
            SchedulerNotificationsConfiguration.DEFAULT.copy(limit = 0)
        }
    }

    @Test
    fun `rejects a non-positive window`() {
        // when / then
        assertThrows<IllegalArgumentException> {
            SchedulerNotificationsConfiguration.DEFAULT.copy(window = Duration.ZERO)
        }
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        assertThat(thrown).isInstanceOf(T::class.java)
    }
}
