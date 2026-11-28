//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.populatedWithTestValues
import java.time.Instant

/**
 * Metadata as Health Connect assigns it to a stored Record.
 */
object MetadataFixtures {
    val lastModified: Instant = Instant.parse("2026-01-01T12:00:00Z")

    val watch = Device(
        type = Device.TYPE_WATCH,
        manufacturer = "Example Devices",
        model = "Study Watch 2",
    )

    fun create(
        id: String = "record-1",
        clientRecordId: String? = null,
        clientRecordVersion: Long = 0,
    ): Metadata {
        val base = if (clientRecordId == null) {
            Metadata.autoRecorded(device = watch)
        } else {
            Metadata.autoRecorded(
                device = watch,
                clientRecordId = clientRecordId,
                clientRecordVersion = clientRecordVersion,
            )
        }
        return base.populatedWithTestValues(
            id = id,
            dataOrigin = DataOrigin(packageName = "com.example.wearable"),
            lastModifiedTime = lastModified,
        )
    }
}
