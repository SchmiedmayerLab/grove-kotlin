//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.grovealliance.core.ConfigurationBuilder
import org.grovealliance.core.DependenciesGraph
import org.grovealliance.core.GroveDsl

/**
 * Convenience for registering the [FirebaseAppConfiguration] module to the Grove configuration.
 *
 * Register this before any other Firebase-backed module. Both parameters run in the built
 * [DependenciesGraph], so they can resolve the app's own components:
 * ```kotlin
 * firebaseApp(
 *     options = { dependency<RegionStore>().persistedOptions() },
 *     onInitialized = { dependency<FirestoreSetup>().apply() },
 * )
 * ```
 *
 * @param options Resolves the options to initialize the default `FirebaseApp` with during start-up,
 * or `null` to defer initialization until [FirebaseAppConfiguration.configure] is called. Resolved
 * once, when the module is configured.
 * @param onInitialized Setup that has to run before any other Firebase API is used, such as
 * `FirebaseFirestore.setFirestoreSettings`. See [FirebaseAppConfiguration].
 */
@GroveDsl
fun ConfigurationBuilder.firebaseApp(
    options: DependenciesGraph.() -> FirebaseOptions? = { null },
    onInitialized: DependenciesGraph.(FirebaseApp) -> Unit = {},
) {
    module<FirebaseAppConfiguration> {
        val graph = this
        FirebaseAppConfiguration(
            options = options(),
            onInitialized = { app -> graph.onInitialized(app) },
        )
    }
}
