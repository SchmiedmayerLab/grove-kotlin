//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.Record
import java.time.Instant
import kotlin.reflect.KClass

/**
 * A Record Health Connect reports deleted: its [recordId] and [recordType], when it was still present
 * ([deletedAfter], when known), and when the deletion was [detectedAt].
 */
data class HealthConnectDeletion(
    val recordId: String,
    val recordType: KClass<out Record>,
    val deletedAfter: Instant?,
    val detectedAt: Instant,
)
