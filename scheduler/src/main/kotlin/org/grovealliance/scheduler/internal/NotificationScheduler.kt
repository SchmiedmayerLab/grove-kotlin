//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.scheduler.internal

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.grovealliance.core.logging.groveLogger
import org.grovealliance.core.time.TimeProvider
import org.grovealliance.foundation.JsonSerializer
import org.grovealliance.scheduler.OpenedTaskNotification
import org.grovealliance.scheduler.SchedulerNotificationsConfiguration
import org.grovealliance.storage.local.KeyValueStorageFactory
import org.grovealliance.storage.local.KeyValueStorageType
import org.grovealliance.storage.local.getSerializableList
import org.grovealliance.storage.local.putSerializable
import java.time.Instant

/**
 * Arms and cancels the platform alarms that deliver task notifications.
 */
internal interface NotificationScheduler {
    /**
     * Replaces all pending scheduler notifications with [notifications].
     */
    fun schedule(notifications: List<PlannedNotification>)

    /**
     * Cancels every pending scheduler notification.
     */
    fun cancelAll()

    /**
     * Re-arms the persisted pending notifications whose delivery time is still in the future. Used to
     * restore alarms after a device reboot, which clears them.
     */
    fun reArm()

    /**
     * Posts [notification] now, if the app may post notifications.
     */
    fun deliver(notification: PlannedNotification)

    /**
     * The task event whose delivered notification started [intent], or `null` if none did.
     */
    fun openedNotification(intent: Intent): OpenedTaskNotification?
}

/**
 * [NotificationScheduler] backed by [AlarmManager]: each request is a one-shot exact alarm that wakes
 * [SchedulerNotificationReceiver] to post the notification. The pending set is persisted so it can be
 * re-armed after a reboot. Posted notifications open the app's launch activity.
 */
internal class NotificationSchedulerImpl(
    private val context: Context,
    private val timeProvider: TimeProvider,
    private val configuration: SchedulerNotificationsConfiguration,
    storageFactory: KeyValueStorageFactory,
) : NotificationScheduler {

    private val storage = storageFactory.create(
        fileName = STORAGE_FILE,
        type = KeyValueStorageType.UNENCRYPTED,
    )
    private val logger by groveLogger()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(notifications: List<PlannedNotification>) {
        cancelAll()
        ensureChannel()
        notifications.forEach(::arm)
        storage.putSerializable(KEY_SNAPSHOT, notifications)
    }

    override fun cancelAll() {
        loadSnapshot().forEach(::cancel)
        storage.delete(KEY_SNAPSHOT)
    }

    override fun reArm() {
        ensureChannel()
        val now = timeProvider.nowInstant().toEpochMilli()
        loadSnapshot().filter { it.fireTimeMillis > now }.forEach(::arm)
    }

    @SuppressLint("MissingPermission") // Checked through areNotificationsEnabled, which covers the permission.
    override fun deliver(notification: PlannedNotification) {
        val notificationManager = NotificationManagerCompat.from(context)
        // Also false while the notification permission isn't granted, on versions that have one.
        if (!notificationManager.areNotificationsEnabled()) return

        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(configuration.smallIcon)
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        configuration.color?.let { builder.setColor(ContextCompat.getColor(context, it)) }
        openPendingIntent(notification)?.let { builder.setContentIntent(it) }
        if (notification.timeSensitive) {
            builder.priority = NotificationCompat.PRIORITY_HIGH
        }
        notification.threadId?.let { builder.setGroup(it) }

        notificationManager.notify(notification.id.hashCode(), builder.build())
    }

    override fun openedNotification(intent: Intent): OpenedTaskNotification? {
        val taskId = intent.getStringExtra(EXTRA_OPENED_TASK_ID) ?: return null
        if (!intent.hasExtra(EXTRA_OPENED_OCCURRENCE_START)) return null
        return OpenedTaskNotification(
            taskId = taskId,
            occurrenceStart = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_OPENED_OCCURRENCE_START, 0L)),
        )
    }

    private fun arm(notification: PlannedNotification) {
        val pendingIntent = deliverPendingIntent(notification, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true
        try {
            if (exact) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notification.fireTimeMillis, pendingIntent)
            } else {
                alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notification.fireTimeMillis, pendingIntent)
            }
        } catch (error: SecurityException) {
            logger.e(error) { "Missing exact-alarm permission; falling back to an inexact alarm." }
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notification.fireTimeMillis, pendingIntent)
        }
    }

    private fun cancel(notification: PlannedNotification) {
        deliverPendingIntent(notification, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager?.cancel(it)
            it.cancel()
        }
    }

    private fun deliverPendingIntent(notification: PlannedNotification, extraFlags: Int): PendingIntent? {
        val intent = Intent(context, SchedulerNotificationReceiver::class.java).apply {
            action = SchedulerNotificationReceiver.ACTION_DELIVER
            data = "$URI_SCHEME://notification/${notification.id}".toUri()
            putExtra(
                SchedulerNotificationReceiver.EXTRA_NOTIFICATION,
                JsonSerializer.encode(value = notification, strategy = PlannedNotification.serializer()),
            )
        }
        return PendingIntent.getBroadcast(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlags,
        )
    }

    /**
     * Opens the app's launch activity, carrying the task event that [notification] is for.
     */
    private fun openPendingIntent(notification: PlannedNotification): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.putExtra(EXTRA_OPENED_TASK_ID, notification.taskId)
        intent.putExtra(EXTRA_OPENED_OCCURRENCE_START, notification.occurrenceStartMillis)
        return PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun loadSnapshot(): List<PlannedNotification> = storage.getSerializableList(KEY_SNAPSHOT)

    /**
     * Creates the channel, or updates its name and description, which the user may see in a new
     * language; the importance the user chose is kept.
     */
    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(configuration.channelName),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(configuration.channelDescription)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "org.grovealliance.scheduler.tasks"
        const val STORAGE_FILE = "org.grovealliance.scheduler.notifications"
        const val EXTRA_OPENED_TASK_ID = "org.grovealliance.scheduler.extra.TASK_ID"
        const val EXTRA_OPENED_OCCURRENCE_START = "org.grovealliance.scheduler.extra.OCCURRENCE_START"
        private const val KEY_SNAPSHOT = "pending"
        private const val URI_SCHEME = "grove-scheduler"
    }
}
