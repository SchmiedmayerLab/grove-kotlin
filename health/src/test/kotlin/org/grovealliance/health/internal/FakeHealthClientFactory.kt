//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.health.connect.client.HealthConnectClient
import org.grovealliance.health.HealthDataAccessRequirements

internal class FakeHealthClientFactory : HealthClientFactory {
    private val _createdClients = mutableListOf<FakeHealthClient>()

    val createdClients: List<FakeHealthClient> get() = _createdClients

    override fun create(healthConnectClient: HealthConnectClient): HealthClient =
        FakeHealthClient().also { _createdClients += it }

    override fun createNoOp(): HealthClient = NoOpHealthClient(dataAccessRequirements = HealthDataAccessRequirements())
}
