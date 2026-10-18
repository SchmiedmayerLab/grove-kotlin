//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.scheduler

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.grovealliance.resources.Strings
import java.time.Duration

/**
 * Tuning and appearance of the automatically scheduled task notifications.
 *
 * Only events within [window] of now are pre-scheduled, at most [limit] at a time; all-day events are
 * delivered at [allDayNotificationTime]. Tapping a notification opens the app's launch activity; see
 * [SchedulerNotifications.openedNotification].
 *
 * @property smallIcon the notification's status bar icon, a monochrome drawable.
 * @property color the notification's accent color, or `null` for the platform default.
 * @property channelName the user-visible name of the notification channel.
 * @property channelDescription the user-visible description of the notification channel.
 */
data class SchedulerNotificationsConfiguration(
    val limit: Int,
    val window: Duration,
    val allDayNotificationTime: NotificationTime,
    @DrawableRes val smallIcon: Int,
    @ColorRes val color: Int?,
    @StringRes val channelName: Int,
    @StringRes val channelDescription: Int,
) {
    init {
        require(limit > 0) { "limit must be positive" }
        require(!window.isNegative && !window.isZero) { "window must be positive" }
    }

    companion object {
        private const val DEFAULT_LIMIT = 30
        private const val DEFAULT_WINDOW_DAYS = 28L
        private const val DEFAULT_ALL_DAY_HOUR = 9

        /**
         * Up to 30 notifications within the next 28 days, all-day events at 9:00, with a generic
         * reminder icon and Grove's channel name and description.
         */
        val DEFAULT = SchedulerNotificationsConfiguration(
            limit = DEFAULT_LIMIT,
            window = Duration.ofDays(DEFAULT_WINDOW_DAYS),
            allDayNotificationTime = NotificationTime(hour = DEFAULT_ALL_DAY_HOUR),
            smallIcon = android.R.drawable.ic_popup_reminder,
            color = null,
            channelName = Strings.scheduler_notifications_channel_name,
            channelDescription = Strings.scheduler_notifications_channel_description,
        )
    }
}
