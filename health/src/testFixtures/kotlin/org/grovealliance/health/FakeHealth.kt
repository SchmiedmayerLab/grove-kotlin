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
 * A [Health] with configurable records, permissions and read failures, recording its query ranges.
 */
@Suppress("TooManyFunctions")
class FakeHealth : Health {
    private val authorizedToRead = mutableSetOf<AnyRecordType>()
    private val aggregateResults = mutableMapOf<AggregateMetric<*>, Any?>()
    private val aggregationBuckets = mutableMapOf<AggregateMetric<*>, List<AggregationBucket<*>>>()
    private val changeResults = mutableMapOf<AnyRecordType, Result<HealthChanges<*>>>()

    fun <T : Any> setAggregate(metric: AggregateMetric<T>, value: T?) {
        aggregateResults[metric] = value
    }

    fun <T : Any> setAggregationBuckets(metric: AggregateMetric<T>, buckets: List<AggregationBucket<T>>) {
        aggregationBuckets[metric] = buckets
    }

    fun <T : Record> setChanges(type: RecordType<T>, result: Result<HealthChanges<T>>) {
        changeResults[type] = result
    }

    private val recordsByType = mutableMapOf<AnyRecordType, List<Record>>()
    private val queryHistory = mutableListOf<HealthQueryTimeRange>()
    private var queryFailure: Exception? = null
    private var historyReadable = false
    private var backgroundReadable = false

    val queries: List<HealthQueryTimeRange> get() = queryHistory.toList()

    fun <T : Record> setRecords(type: RecordType<T>, records: List<T>) {
        recordsByType[type] = records
        queryFailure = null
    }

    fun setQueryFailure(error: Exception) {
        queryFailure = error
    }

    fun setHistoryReadable(value: Boolean) {
        historyReadable = value
    }

    fun setBackgroundReadable(value: Boolean) {
        backgroundReadable = value
    }

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

    override suspend fun readAccess(type: AnyRecordType): HealthReadAccess = HealthReadAccess(
        records = type in authorizedToRead,
        history = historyReadable,
        background = backgroundReadable,
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> changes(type: RecordType<T>, token: String?): Result<HealthChanges<T>> =
        changeResults[type] as Result<HealthChanges<T>>? ?: Result.success(
            HealthChanges(
                added = emptyList(),
                deletedIds = emptySet(),
                nextToken = token ?: "initial-token",
                isReset = token == null,
            ),
        )

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
    ): List<T> {
        queryHistory += timeRange
        queryFailure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return (recordsByType[type].orEmpty() as List<T>).filter { predicate?.invoke(it) ?: true }.take(limit)
    }

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
    ): T? {
        queryHistory += timeRange
        queryFailure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return recordsByType[type]?.lastOrNull() as T?
    }

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T? {
        @Suppress("UNCHECKED_CAST")
        return aggregateResults[metric] as T?
    }

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval,
        source: String?,
    ): List<AggregationBucket<T>> {
        @Suppress("UNCHECKED_CAST")
        return aggregationBuckets[metric].orEmpty() as List<AggregationBucket<T>>
    }

    override fun <T : Record> continuousQuery(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        interval: Duration,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): Flow<QueryResult<T>> = emptyFlow()

    override fun configure() = Unit
}
