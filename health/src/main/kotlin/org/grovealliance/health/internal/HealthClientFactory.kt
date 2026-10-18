//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.health.connect.client.HealthConnectClient
import org.grovealliance.core.Standard
import org.grovealliance.core.coroutines.Concurrency
import org.grovealliance.core.lifecycle.AppLifecycle
import org.grovealliance.core.time.TimeProvider
import org.grovealliance.health.HealthConstraint
import org.grovealliance.storage.local.LocalStorage

/**
 * Creates the [HealthClient]s that [HealthClientProvider] switches between.
 */
internal interface HealthClientFactory {
    /**
     * Creates a client that reaches Health Connect through [healthConnectClient].
     */
    fun create(healthConnectClient: HealthConnectClient): HealthClient

    /**
     * Creates a client for when Health Connect can't be reached, on which every operation does nothing.
     */
    fun createNoOp(): HealthClient
}

/**
 * The [HealthClientFactory] creating clients for the app's [configuration].
 */
internal class DefaultHealthClientFactory(
    private val standard: Standard,
    private val concurrency: Concurrency,
    private val localStorage: LocalStorage,
    private val appLifecycle: AppLifecycle,
    private val timeProvider: TimeProvider,
    private val configuration: HealthModuleConfiguration,
) : HealthClientFactory {

    override fun create(healthConnectClient: HealthConnectClient): HealthClient = DefaultHealthClient(
        configurations = configuration.components,
        standard = standard as? HealthConstraint,
        concurrency = concurrency,
        healthConnectClient = healthConnectClient,
        appLifecycle = appLifecycle,
        timeProvider = timeProvider,
        localStorage = localStorage,
    )

    override fun createNoOp(): HealthClient = NoOpHealthClient(
        dataAccessRequirements = configuration.dataAccessRequirements,
    )
}
