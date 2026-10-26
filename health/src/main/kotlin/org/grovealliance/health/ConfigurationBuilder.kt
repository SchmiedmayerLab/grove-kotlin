//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

import org.grovealliance.core.ApplicationModule
import org.grovealliance.core.ConfigurationBuilder
import org.grovealliance.core.GroveDsl
import org.grovealliance.core.coroutines.Concurrency
import org.grovealliance.health.internal.DefaultHealthClientFactory
import org.grovealliance.health.internal.DefaultHealthConnectSdk
import org.grovealliance.health.internal.HealthClientFactory
import org.grovealliance.health.internal.HealthClientProvider
import org.grovealliance.health.internal.HealthConnectSdk
import org.grovealliance.health.internal.HealthConnectStore
import org.grovealliance.health.internal.HealthImpl
import org.grovealliance.health.internal.HealthModuleConfiguration

/**
 * Convenience for registering the [Health] module to the Grove configuration.
 */
@GroveDsl
fun ConfigurationBuilder.health(block: HealthConfigurationScope.() -> Unit) {
    val scope = HealthConfigurationScope().apply(block)

    singleton {
        HealthModuleConfiguration(
            components = scope.components.toSet(),
            privacyConfig = scope.privacyConfigurationScope.config,
        )
    }
    singleton<HealthConnectSdk> {
        DefaultHealthConnectSdk(context = appContext())
    }
    singleton {
        HealthConnectStore(context = appContext())
    }
    singleton<HealthClientFactory> {
        DefaultHealthClientFactory(
            standard = dependency<ApplicationModule>().standard,
            concurrency = dependency(),
            localStorage = dependency(),
            appLifecycle = dependency(),
            timeProvider = dependency(),
            configuration = dependency(),
        )
    }
    singleton {
        HealthClientProvider(
            sdk = dependency(),
            clientFactory = dependency(),
            appLifecycle = dependency(),
            scope = dependency<Concurrency>().defaultCoroutineScope(),
        )
    }
    singleton {
        HealthImpl(
            configuration = dependency(),
            clientProvider = dependency(),
            healthConnectStore = dependency(),
        )
    }
    // Resolves to the same instance as `HealthImpl`, which the graph therefore configures once per key;
    // configuring it again does nothing.
    module<Health> {
        dependency<HealthImpl>()
    }
}
