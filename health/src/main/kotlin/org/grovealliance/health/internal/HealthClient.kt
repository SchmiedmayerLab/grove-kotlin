//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.Record
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.grovealliance.health.AggregationBucket
import org.grovealliance.health.AggregationInterval
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.Health
import org.grovealliance.health.HealthDataAccessRequirements
import org.grovealliance.health.HealthQueryTimeRange
import org.grovealliance.health.QueryResult
import org.grovealliance.health.QuerySort
import org.grovealliance.health.RecordType
import java.time.Instant
import kotlin.time.Duration

@Suppress("TooManyFunctions")
internal interface HealthClient {
    /**
     * The current configuration state of the HealthClient.
     */
    val configState: Health.ConfigState

    /**
     * A [StateFlow] indicating whether all requested permissions have been granted.
     */
    val isFullyAuthorizedState: StateFlow<Boolean>

    /**
     * The [org.grovealliance.health.HealthDataAccessRequirements] defining the requested read and write access.
     */
    val dataAccessRequirements: HealthDataAccessRequirements

    /**
     * Configures the HealthClient with the provided [HealthConfigurationComponent]s.
     */
    fun configure()

    /**
     * Notifies the HealthClient that the specified permissions have been granted.
     *
     * @param granted The set of granted permission strings.
     */
    fun onPermissionsGranted(granted: Set<String>)

    /**
     * Notifies the HealthClient that a permission request ended without a result, because its activity
     * finished first.
     */
    fun onPermissionRequestAbandoned()

    /**
     * Requests all permissions that have not yet been granted and suspends until the request ends.
     *
     * @param activity The [FragmentActivity] to use for requesting permissions.
     * @return `true` if every needed permission is granted afterwards, `false` otherwise.
     */
    suspend fun requestPermissionsIfNeeded(activity: FragmentActivity): Boolean

    /**
     * Replaces the components added at runtime with [components], on top of the registered
     * configuration. Collection of record types only the previous components collected is reset.
     */
    suspend fun updateRuntimeConfiguration(components: Set<HealthConfigurationComponent>)

    /**
     * Requests read permission for the specified [AnyRecordType].
     *
     * @param type The [AnyRecordType] to request read permission for.
     * @param activity The [FragmentActivity] to use for requesting permission.
     */
    fun requestReadPermission(type: AnyRecordType, activity: FragmentActivity)

    /**
     * Requests write permission for the specified [AnyRecordType].
     *
     * @param type The [AnyRecordType] to request write permission for.
     * @param activity The [FragmentActivity] to use for requesting permission.
     */
    fun requestWritePermission(type: AnyRecordType, activity: FragmentActivity)

    /**
     * Checks if the client is authorized to read data of the specified [AnyRecordType].
     *
     * @param type The [AnyRecordType] to check read authorization for.
     * @return `true` if authorized to read, `false` otherwise.
     */
    suspend fun isAuthorizedToRead(type: AnyRecordType): Boolean

    /**
     * Checks if the client is authorized to write data of the specified [AnyRecordType].
     *
     * @param type The [AnyRecordType] to check write authorization for.
     * @return `true` if authorized to write, `false` otherwise.
     */
    suspend fun isAuthorizedToWrite(type: AnyRecordType): Boolean

    /**
     * Resets the internal state associated with a record type's collection via collect record.
     *
     * Use this function to reset the collection state, so that the next time a collector is registered,
     * its behavior will be as if it were being registered for the first time.
     */
    fun resetRecordCollection(type: AnyRecordType)

    /**
     * Adds a [HealthDataCollector] to the client for data collection.
     *
     * @param collector The [HealthDataCollector] to add.
     */
    suspend fun addHealthDataCollector(collector: HealthDataCollector)

    /**
     * Triggers an immediate collection of data from all registered data collectors with a manual collection mode.
     */
    suspend fun triggerDataSourceCollection()

    /**
     * Inserts a [Record] into Health Connect.
     *
     * @param record The [Record] to insert.
     * @return `true` if the insertion was successful, `false` otherwise.
     */
    suspend fun insert(record: Record): Boolean

    /**
     * Queries health data of the given [Record] [type].
     *
     * @param type The [RecordType] to query data for.
     * @param timeRange The [HealthQueryTimeRange] defining the time range for the query.
     * @param source An optional source filter for the query.
     * @param sortedBy The [QuerySort] defining the sorting order of the results.
     * @param limit The maximum number of records to return.
     * @param predicate An optional predicate to filter the results.
     *
     * @return A list of queried records of type [T].
     */
    suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange = HealthQueryTimeRange.today(),
        source: String? = null,
        sortedBy: QuerySort = QuerySort.NONE,
        limit: Int = Int.MAX_VALUE,
        predicate: ((T) -> Boolean)? = null,
    ): List<T>

    /**
     * Queries health data of the given [Record] [type] with pagination support.
     *
     * @param type The [RecordType] to query data for.
     * @param timeRange The [HealthQueryTimeRange] defining the time range for the query.
     * @param anchor An optional anchor string for pagination.
     * @param predicate An optional predicate to filter the results.
     *
     * @return A [QueryResult] containing the queried records, deleted IDs, and next anchor.
     */
    suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): QueryResult<T>

    /**
     * Performs a continuous query for health data of the given [Record] [type].
     *
     * @param type The [RecordType] to query data for.
     * @param timeRange The [HealthQueryTimeRange] defining the time range for the query.
     * @param interval The [Duration] defining the interval between queries.
     * @param anchor An optional anchor string for pagination.
     * @param predicate An optional predicate to filter the results.
     *
     * @return A [Flow] emitting [QueryResult]s containing the queried records, deleted IDs, and next anchor.
     */
    fun <T : Record> continuousQuery(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        interval: Duration,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): Flow<QueryResult<T>>

    /**
     * Retrieves the oldest sample date for the given [Record] [type].
     *
     * @param type The [RecordType] to retrieve the oldest sample date for.
     *
     * @return The oldest sample date as an [Instant], or `null` if no samples exist.
     */
    suspend fun <T : Record> oldestSampleDate(type: RecordType<T>): Instant?

    /**
     * Aggregates [metric] over [timeRange], filtered to [source] if given.
     *
     * @return The aggregated value, or `null` if there is no data or reading isn't permitted.
     */
    suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T?

    /**
     * Aggregates [metric] over [timeRange] split into buckets of [interval], filtered to [source] if
     * given.
     *
     * @return The buckets in chronological order, or an empty list if reading isn't permitted.
     */
    suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval,
        source: String?,
    ): List<AggregationBucket<T>>
}
