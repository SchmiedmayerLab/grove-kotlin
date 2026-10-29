//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

import androidx.health.connect.client.records.Record

/**
 * A fully paged change read. [isReset] means a full snapshot is needed before acknowledging
 * [nextToken], because this is a new subscription or its preceding token expired.
 */
data class HealthChanges<T : Record>(
    val added: List<T>,
    val deletedIds: Set<String>,
    val nextToken: String,
    val isReset: Boolean,
)

/**
 * The permissions currently granted for reading a record type, its history and background data.
 */
data class HealthReadAccess(
    val records: Boolean,
    val history: Boolean,
    val background: Boolean,
)
