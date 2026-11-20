//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export

import org.grovealliance.core.ConfigurationBuilder
import org.grovealliance.core.coroutines.Concurrency
import org.grovealliance.health.export.internal.BulkHealthExporterImpl
import org.grovealliance.health.export.internal.ExportBatchPlanner
import org.grovealliance.health.export.internal.ExportBatchPlannerImpl
import org.grovealliance.health.export.internal.ExportCheckpointMapper
import org.grovealliance.health.export.internal.ExportCheckpointMapperImpl
import org.grovealliance.health.export.internal.ExportCheckpointStore
import org.grovealliance.health.export.internal.ExportCheckpointStoreImpl
import org.grovealliance.health.export.internal.ExportRecordReader
import org.grovealliance.health.export.internal.ExportRecordReaderImpl

/**
 * Registers the [BulkHealthExporter] within a Grove [ConfigurationBuilder]. It reads through the
 * `health` module, which must be registered as well.
 *
 * ```kotlin
 * override val configuration = Configuration {
 *     health { … }
 *     bulkHealthExport()
 * }
 * ```
 */
fun ConfigurationBuilder.bulkHealthExport() {
    factory<ExportRecordReader> { ExportRecordReaderImpl(health = dependency()) }
    factory<ExportBatchPlanner> {
        ExportBatchPlannerImpl(
            reader = dependency(),
            timeProvider = dependency(),
        )
    }
    factory<ExportCheckpointMapper> { ExportCheckpointMapperImpl() }
    factory<ExportCheckpointStore> {
        ExportCheckpointStoreImpl(
            storage = dependency(),
            mapper = dependency(),
        )
    }
    singleton<BulkHealthExporter> {
        BulkHealthExporterImpl(
            reader = dependency(),
            planner = dependency(),
            checkpoints = dependency(),
            scope = dependency<Concurrency>().ioCoroutineScope(),
        )
    }
}
