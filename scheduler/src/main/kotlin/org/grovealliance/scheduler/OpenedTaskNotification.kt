//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.scheduler

import java.time.Instant

/**
 * The task event whose notification the user tapped to open the app.
 *
 * @property taskId the [Task.id] of the event's task.
 * @property occurrenceStart the start of the event's occurrence.
 */
data class OpenedTaskNotification(
    val taskId: String,
    val occurrenceStart: Instant,
)
