//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.Record
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.grovealliance.health.internal.CollectRecord
import org.grovealliance.health.internal.dataAccessRequirements
import java.time.Instant
import kotlin.time.Duration

/**
 * A [Health] that records the runtime configuration it is given and reads no data.
 */
@Suppress("TooManyFunctions")
class FakeHealth : Health {
    private val authorizedToRead = mutableSetOf<AnyRecordType>()
    private var runtimeRequirements = HealthDataAccessRequirements()

    /**
     * The record types the latest [updateConfiguration] collects.
     */
    var collectedRecordTypes: Set<AnyRecordType> = emptySet()
        private set

    /**
     * The collection mode of each record type the latest [updateConfiguration] collects.
     */
    var collectionModes: Map<AnyRecordType, CollectionMode> = emptyMap()
        private set

    /**
     * How often [updateConfiguration] was called.
     */
    var configurationUpdateCount = 0
        private set

    override val dataAccessRequirements: HealthDataAccessRequirements
        get() = runtimeRequirements

    override val availability: StateFlow<HealthConnectAvailability> =
        MutableStateFlow(HealthConnectAvailability.Available).asStateFlow()

    override val isFullyAuthorizedState: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    /**
     * Makes [isAuthorizedToRead] report `true` for [types].
     */
    fun setAuthorizedToRead(types: Set<AnyRecordType>) {
        authorizedToRead.clear()
        authorizedToRead.addAll(types)
    }

    override suspend fun updateConfiguration(block: HealthConfigurationScope.() -> Unit) {
        val components = HealthConfigurationScope().apply(block).components
        runtimeRequirements = components.dataAccessRequirements()
        val collectRecords = components.filterIsInstance<CollectRecord>()
        collectedRecordTypes = collectRecords.map { it.recordType }.toSet()
        collectionModes = collectRecords.associate { it.recordType to it.start }
        configurationUpdateCount += 1
    }

    override suspend fun isAuthorizedToRead(type: AnyRecordType): Boolean = type in authorizedToRead

    override suspend fun isAuthorizedToWrite(type: AnyRecordType): Boolean = false

    override fun refreshAvailability() = Unit

    override fun installOrUpdateHealthConnect(): Boolean = false

    override suspend fun requestPermissionsIfNeeded(activity: FragmentActivity): Boolean = false

    override fun requestReadPermission(type: AnyRecordType, activity: FragmentActivity) = Unit

    override fun requestWritePermission(type: AnyRecordType, activity: FragmentActivity) = Unit

    override fun resetRecordCollection(type: AnyRecordType) = Unit

    override suspend fun insert(record: Record): Boolean = false

    override suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
        sortedBy: QuerySort,
        limit: Int,
        predicate: ((T) -> Boolean)?,
    ): List<T> = emptyList()

    override suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): QueryResult<T> = QueryResult(added = emptyList(), deletedIds = emptyList(), nextAnchor = anchor)

    override suspend fun <T : Record> oldestSampleDate(type: RecordType<T>): Instant? = null

    override suspend fun <T : Record> mostRecent(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T? = null

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T? = null

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval,
        source: String?,
    ): List<AggregationBucket<T>> = emptyList()

    override fun <T : Record> continuousQuery(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        interval: Duration,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): Flow<QueryResult<T>> = emptyFlow()

    override fun configure() = Unit
}
