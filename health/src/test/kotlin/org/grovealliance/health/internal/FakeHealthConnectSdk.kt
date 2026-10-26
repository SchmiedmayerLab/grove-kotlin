//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.testing.FakeHealthConnectClient
import org.grovealliance.health.HealthConnectAvailability

internal class FakeHealthConnectSdk : HealthConnectSdk {
    private var availability = HealthConnectAvailability.Available
    private var client: HealthConnectClient? = FakeHealthConnectClient()

    var createClientCount = 0
        private set

    fun setAvailability(availability: HealthConnectAvailability) {
        this.availability = availability
    }

    fun setClient(client: HealthConnectClient?) {
        this.client = client
    }

    override fun availability(): HealthConnectAvailability = availability

    override fun createClient(): HealthConnectClient? {
        createClientCount += 1
        return client
    }
}
