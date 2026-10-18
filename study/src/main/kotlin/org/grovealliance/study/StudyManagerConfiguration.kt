//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.study

import androidx.room.Room
import org.grovealliance.core.ConfigurationBuilder
import org.grovealliance.core.DependenciesGraph
import org.grovealliance.core.GroveDsl
import org.grovealliance.core.coroutines.Concurrency
import org.grovealliance.study.internal.BundleStore
import org.grovealliance.study.internal.ScheduleCompiler
import org.grovealliance.study.internal.StudyDatabase
import org.grovealliance.study.internal.StudyEnrollmentMapper
import org.grovealliance.study.internal.StudyEnvironment
import org.grovealliance.study.internal.StudyHealthCollector
import org.grovealliance.study.internal.StudyManagerImpl
import java.io.File
import java.util.Locale

/**
 * Registers the [StudyManager] module in the Grove configuration.
 *
 * Requires a `Scheduler` (register via `scheduler()`), a `TimeProvider`, and a [Concurrency] module
 * to be part of the graph. Resolve the registered module elsewhere via `dependency<StudyManager>()`.
 *
 * @param preferredLocale resolves the locale used to resolve localized bundle resources such as task
 *   titles, in the built dependency graph, once when the manager is created. See
 *   [StudyManager.updatePreferredLocale] for changing it later.
 * @param health when non-`null`, the manager also configures the `Health` module from the studies'
 *   health data collection components, which requires a `Health` module in the graph. Leaving it
 *   `null` keeps the manager free of that dependency.
 */
@GroveDsl
fun ConfigurationBuilder.studyManager(
    preferredLocale: DependenciesGraph.() -> Locale = { Locale.getDefault() },
    health: StudyHealthConfiguration? = null,
) {
    singleton {
        Room.databaseBuilder(
            appContext(),
            StudyDatabase::class.java,
            StudyDatabase.NAME,
        ).build()
    }
    singleton {
        BundleStore(File(appContext().filesDir, STUDY_BUNDLES_DIRECTORY))
    }
    factory { StudyEnrollmentMapper() }
    factory {
        ScheduleCompiler(
            calculator = dependency(),
            timeProvider = dependency()
        )
    }
    health?.let { configuration ->
        factory {
            StudyHealthCollector(
                health = dependency(),
                configuration = configuration,
            )
        }
    }
    module<StudyManager> {
        val database = dependency<StudyDatabase>()
        StudyManagerImpl(
            enrollmentDao = database.dao(),
            scheduler = dependency(),
            scheduleCompiler = dependency(),
            timeProvider = dependency(),
            bundleStore = dependency(),
            mapper = dependency(),
            scope = dependency<Concurrency>().ioCoroutineScope(),
            healthCollector = health?.let { dependency<StudyHealthCollector>() },
            environment = StudyEnvironment(preferredLocale = preferredLocale()),
        )
    }
}

private const val STUDY_BUNDLES_DIRECTORY = "org.grovealliance.study/StudyBundles"
