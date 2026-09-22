//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

/** One Health Connect record as the producer names it: its native `Metadata.id` and its catalog source type. */
public data class HealthConnectSourceRecord(public val id: String, public val type: HealthConnectSourceType) {
    init {
        require(id.isNotBlank()) { "A Health Connect source record requires its native Metadata.id." }
    }

    override fun toString(): String = "HealthConnectSourceRecord(type=${type.token}, id=<redacted>)"
}
