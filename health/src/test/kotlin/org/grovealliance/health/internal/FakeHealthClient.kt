//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grovealliance.health.HealthDataAccessRequirements

internal class FakeHealthClient : HealthClient by NoOpHealthClient(dataAccessRequirements = HealthDataAccessRequirements()) {
    private val fullyAuthorized = MutableStateFlow(false)

    override val isFullyAuthorizedState: StateFlow<Boolean> = fullyAuthorized.asStateFlow()

    var configureCount = 0
        private set

    var runtimeComponents: Set<HealthConfigurationComponent> = emptySet()
        private set

    fun setFullyAuthorized(isFullyAuthorized: Boolean) {
        fullyAuthorized.value = isFullyAuthorized
    }

    override fun configure() {
        configureCount += 1
    }

    override suspend fun updateRuntimeConfiguration(components: Set<HealthConfigurationComponent>) {
        runtimeComponents = components
    }
}
