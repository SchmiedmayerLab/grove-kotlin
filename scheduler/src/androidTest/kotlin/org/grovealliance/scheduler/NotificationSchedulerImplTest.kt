//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.scheduler

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import org.grovealliance.core.time.TimeProvider
import org.grovealliance.resources.Strings
import org.grovealliance.scheduler.internal.NotificationSchedulerImpl
import org.grovealliance.scheduler.internal.PlannedNotification
import org.grovealliance.storage.local.KeyValueStorageFactory
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class NotificationSchedulerImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val configuration = SchedulerNotificationsConfiguration.DEFAULT.copy(
        smallIcon = android.R.drawable.ic_dialog_info,
    )
    private val scheduler = NotificationSchedulerImpl(
        context = context,
        timeProvider = TimeProvider.create(context),
        configuration = configuration,
        storageFactory = KeyValueStorageFactory.create(context),
    )

    @get:Rule
    val notificationPermission: TestRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        RuleChain.emptyRuleChain()
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
    }

    @Test
    fun `delivering posts with the configured icon and channel`() {
        // when
        scheduler.deliver(notification = plannedNotification())

        // then
        val posted = postedNotifications().single()
        assertThat(posted.notification.smallIcon.resId).isEqualTo(android.R.drawable.ic_dialog_info)
        assertThat(posted.notification.extras.getString("android.title")).isEqualTo("Survey")
        val channel = notificationManager.getNotificationChannel(posted.notification.channelId)
        assertThat(channel.name).isEqualTo(context.getString(Strings.scheduler_notifications_channel_name))
        assertThat(channel.description)
            .isEqualTo(context.getString(Strings.scheduler_notifications_channel_description))
    }

    @Test
    fun `an open intent names its task event`() {
        // given
        val intent = Intent()
            .putExtra(NotificationSchedulerImpl.EXTRA_OPENED_TASK_ID, "task")
            .putExtra(NotificationSchedulerImpl.EXTRA_OPENED_OCCURRENCE_START, OCCURRENCE_START.toEpochMilli())

        // when
        val opened = scheduler.openedNotification(intent = intent)

        // then
        assertThat(opened).isEqualTo(
            OpenedTaskNotification(
                taskId = "task",
                occurrenceStart = OCCURRENCE_START,
            ),
        )
    }

    @Test
    fun `an unrelated intent names no task event`() {
        // when
        val opened = scheduler.openedNotification(intent = Intent())

        // then
        assertThat(opened).isNull()
    }

    /**
     * The notifications this app has posted, waiting briefly for the first one, since the system
     * records a posted notification asynchronously.
     */
    private fun postedNotifications(): Array<StatusBarNotification> {
        val deadline = SystemClock.uptimeMillis() + POST_TIMEOUT_MILLIS
        var posted = notificationManager.activeNotifications
        while (posted.isEmpty() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
            posted = notificationManager.activeNotifications
        }
        return posted
    }

    private fun plannedNotification() = PlannedNotification(
        id = "notification",
        taskId = "task",
        occurrenceStartMillis = OCCURRENCE_START.toEpochMilli(),
        fireTimeMillis = OCCURRENCE_START.toEpochMilli(),
        title = "Survey",
        body = "Answer the survey",
        threadId = null,
        timeSensitive = true,
    )

    private companion object {
        val OCCURRENCE_START: Instant = Instant.parse("2026-06-15T16:00:00Z")
        const val POST_TIMEOUT_MILLIS = 5_000L
        const val POLL_INTERVAL_MILLIS = 50L
    }
}
