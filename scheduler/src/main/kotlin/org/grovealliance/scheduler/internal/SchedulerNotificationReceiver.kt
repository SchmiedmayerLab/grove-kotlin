//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.scheduler.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.grovealliance.core.optionalDependency
import org.grovealliance.foundation.JsonSerializer

/**
 * Posts a task notification when its scheduled alarm fires. The notification travels in the alarm's
 * intent; alarms armed with an earlier format are dropped.
 *
 * The [NotificationScheduler] is resolved optionally: when the app no longer enables scheduler
 * notifications, an alarm that is still armed posts nothing.
 */
internal class SchedulerNotificationReceiver : BroadcastReceiver() {

    private val notificationScheduler: NotificationScheduler? by optionalDependency()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELIVER) return
        val notification = intent.getStringExtra(EXTRA_NOTIFICATION)
            ?.let { JsonSerializer.decodeOrNull(text = it, deserializer = PlannedNotification.serializer()) }
            ?: return
        notificationScheduler?.deliver(notification = notification)
    }

    companion object {
        const val ACTION_DELIVER = "org.grovealliance.scheduler.action.DELIVER_NOTIFICATION"
        const val EXTRA_NOTIFICATION = "notification"
    }
}
