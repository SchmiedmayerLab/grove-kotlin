//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.grovealliance.core.lifecycle.AppLifecycle
import org.grovealliance.health.HealthConnectAvailability
import org.grovealliance.health.healthLogger

/**
 * Holds the [HealthClient] that [org.grovealliance.health.Health] delegates to, and the availability of
 * Health Connect.
 *
 * While Health Connect can't be reached, the current client is a no-op. Once a refresh finds Health
 * Connect available, the provider switches to a client bound to it and configures that client if the
 * provider was already configured, so collection starts without an app restart, and applies the latest
 * runtime configuration to it. Availability is refreshed whenever the app comes to the foreground.
 */
internal class HealthClientProvider(
    private val sdk: HealthConnectSdk,
    private val clientFactory: HealthClientFactory,
    private val appLifecycle: AppLifecycle,
    private val scope: CoroutineScope,
) {
    private val logger by healthLogger()
    private var isConfigured = false
    private var isBound = false

    @Volatile
    private var runtimeComponents: Set<HealthConfigurationComponent> = emptySet()

    private val _availability = MutableStateFlow(HealthConnectAvailability.Unavailable)
    private val _client: MutableStateFlow<HealthClient>

    init {
        val reported = sdk.availability()
        val boundClient = if (reported == HealthConnectAvailability.Available) bind() else null
        _client = MutableStateFlow(boundClient ?: clientFactory.createNoOp())
        _availability.value = effectiveAvailability(reported = reported, bound = boundClient != null)
    }

    /**
     * The current availability of Health Connect.
     */
    val availability: StateFlow<HealthConnectAvailability> = _availability.asStateFlow()

    /**
     * The client every operation goes to.
     */
    val client: StateFlow<HealthClient> = _client.asStateFlow()

    /**
     * Whether the current client holds every permission it needs.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isFullyAuthorizedState: StateFlow<Boolean> = _client
        .flatMapLatest { it.isFullyAuthorizedState }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = false)

    /**
     * Configures the current client and starts refreshing availability whenever the app comes to the
     * foreground.
     */
    @Synchronized
    fun configure() {
        if (isConfigured) return
        isConfigured = true
        _client.value.configure()
        scope.launch {
            appLifecycle.state
                .filter { it == AppLifecycle.State.FOREGROUND }
                .collect { refreshAvailability() }
        }
    }

    /**
     * Replaces the runtime configuration of the current client and every client bound later.
     */
    suspend fun updateConfiguration(components: Set<HealthConfigurationComponent>) {
        runtimeComponents = components
        _client.value.updateRuntimeConfiguration(components = components)
    }

    /**
     * Reads the availability of Health Connect again, and binds to it if it became available.
     */
    @Synchronized
    fun refreshAvailability() {
        val reported = sdk.availability()
        if (reported == HealthConnectAvailability.Available && !isBound) {
            bind()?.let { boundClient ->
                logger.i { "Health Connect became available" }
                _client.value = boundClient
                if (isConfigured) boundClient.configure()
                val components = runtimeComponents
                if (components.isNotEmpty()) {
                    scope.launch { boundClient.updateRuntimeConfiguration(components = components) }
                }
            }
        }
        _availability.value = effectiveAvailability(reported = reported, bound = isBound)
    }

    private fun bind(): HealthClient? {
        val healthConnectClient = sdk.createClient() ?: return null
        isBound = true
        return clientFactory.create(healthConnectClient = healthConnectClient)
    }

    /**
     * Reports [HealthConnectAvailability.UpdateRequired] while Health Connect claims to be available but
     * no client could be bound, so callers never see it as available while operations still go nowhere.
     */
    private fun effectiveAvailability(reported: HealthConnectAvailability, bound: Boolean) =
        if (reported == HealthConnectAvailability.Available && !bound) {
            HealthConnectAvailability.UpdateRequired
        } else {
            reported
        }
}
