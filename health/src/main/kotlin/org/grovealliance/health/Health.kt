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
import kotlinx.coroutines.flow.StateFlow
import org.grovealliance.core.Module
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Health module provides access to health data via Health Connect.
 *
 * Register in your Grove Configuration via:
 *
 * ```kotlin
 * override val configuration = Configuration {
 *    health {
 *        requestReadAccess(RecordType.bloodPressure, RecordType.weight)
 *        requestWriteAccess(RecordType.heartRate)
 *        collectRecord(
 *            recordType = RecordType.bloodPressure,
 *            start = CollectionMode.Manual,
 *            continueInBackground = true,
 *        )
 *    }
 * }
 * ```
 *
 * Below Android 14, Health Connect is a separate app. Check [availability] before asking for
 * permissions, and offer [installOrUpdateHealthConnect] while it is
 * [HealthConnectAvailability.UpdateRequired]:
 *
 * ```kotlin
 * when (health.availability.value) {
 *     HealthConnectAvailability.Available -> health.requestPermissionsIfNeeded(activity)
 *     HealthConnectAvailability.UpdateRequired -> health.installOrUpdateHealthConnect()
 *     HealthConnectAvailability.Unavailable -> Unit
 * }
 * ```
 */
@Suppress("TooManyFunctions")
interface Health : Module {
    /**
     * The read and write access this module requests: what it was configured with, plus what the
     * latest [updateConfiguration] added.
     */
    val dataAccessRequirements: HealthDataAccessRequirements

    /**
     * Whether Health Connect can be used on this device.
     *
     * It is checked again each time the app comes to the foreground, or on [refreshAvailability]. Once it
     * becomes [HealthConnectAvailability.Available], configured collection starts and every operation
     * reaches Health Connect, without an app restart. Until then, operations do nothing, queries return
     * no records and permission requests report `false`.
     */
    val availability: StateFlow<HealthConnectAvailability>

    /**
     * Indicates whether all requested permissions have been granted.
     */
    val isFullyAuthorizedState: StateFlow<Boolean>

    /**
     * Checks again whether Health Connect can be used, for example after the user returned from installing
     * it. See [availability].
     */
    fun refreshAvailability()

    /**
     * Opens the store listing of Health Connect, where the user can install or update it.
     *
     * @return `true` if a store listing was opened, `false` if no app could open it.
     */
    fun installOrUpdateHealthConnect(): Boolean

    /**
     * Requests all permissions that have not yet been granted, and suspends until the user closes the
     * Health Connect permission screen, or [activity] finishes before that.
     *
     * Health Connect stops showing its permission screen after the user denied a permission twice; the
     * request then returns at once.
     *
     * @param activity The [FragmentActivity] to use for requesting permissions.
     *
     * @return `true` if every requested permission is granted afterwards, `false` otherwise, including
     * when Health Connect isn't [available][availability].
     */
    suspend fun requestPermissionsIfNeeded(activity: FragmentActivity): Boolean

    /**
     * Extends the configuration the module was registered with, using the same [HealthConfigurationScope]
     * as `health { }`, for access and collection only known at runtime.
     *
     * Each call replaces what the previous call added; the registered configuration is kept. A record
     * type no longer collected by the update stops being collected and its collection state is reset.
     * The update is kept when Health Connect becomes [available][availability] later. Privacy settings
     * in [block] are ignored.
     */
    suspend fun updateConfiguration(block: HealthConfigurationScope.() -> Unit)

    /**
     * Requests read permission for the given [Record] [type].
     *
     * @param type The [Record] type to request read permission for.
     * @param activity The [FragmentActivity] to use for requesting permissions.
     */
    fun requestReadPermission(type: AnyRecordType, activity: FragmentActivity)

    /**
     * Requests write permission for the given [Record] [type].
     *
     * @param type The [Record] type to request write permission for.
     * @param activity The [FragmentActivity] to use for requesting permissions.
     */
    fun requestWritePermission(type: AnyRecordType, activity: FragmentActivity)

    /**
     * Resets the record collection for the given [Record] [type].
     *
     * @param type The [Record] type to reset the collection for.
     */
    fun resetRecordCollection(type: AnyRecordType)

    /**
     * Inserts a [Record] into Health Connect.
     *
     * @param record The [Record] to insert.
     *
     * @return `true` if the insertion was successful, `false` otherwise.
     */
    suspend fun insert(record: Record): Boolean

    /**
     * Queries health data of the given [Record] [type].
     *
     * @param type The [Record] type to query.
     * @param timeRange The [HealthQueryTimeRange] to query data for. Defaults to today's data.
     * @param source The data source to filter by. Defaults to `null`, which includes all sources.
     * @param sortedBy The [QuerySort] to sort the results by. Defaults to [QuerySort.NONE].
     * @param limit The maximum number of records to return. Defaults to [Int.MAX_VALUE].
     * @param predicate An optional predicate to filter the results.
     *
     * @return A list of queried records of the specified [type].
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
     * @param type The [Record] type to query.
     * @param timeRange The [HealthQueryTimeRange] to query data for. Defaults to today's data.
     * @param anchor The anchor string to continue querying from a previous result. Defaults to `null`.
     * @param predicate An optional predicate to filter the results.
     *
     * @return A [QueryResult] containing the queried records, deleted record IDs, and the next anchor.
     */
    suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange = HealthQueryTimeRange.today(),
        anchor: String?,
        predicate: ((T) -> Boolean)? = null,
    ): QueryResult<T>

    /**
     * Retrieves the oldest sample date for the given [Record] [type].
     *
     * @param type The [Record] type to retrieve the oldest sample date for.
     *
     * @return The oldest sample date as an [Instant], or `null` if no samples exist.
     */
    suspend fun <T : Record> oldestSampleDate(type: RecordType<T>): Instant?

    /**
     * Retrieves the most recent record of the given [type], by start time.
     *
     * @param type The [Record] type to retrieve.
     * @param timeRange The [HealthQueryTimeRange] to look in. Defaults to all time.
     * @param source The data source to filter by. Defaults to `null`, which includes all sources.
     *
     * @return The most recent record, or `null` if there is none or reading [type] isn't permitted.
     */
    suspend fun <T : Record> mostRecent(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange = HealthQueryTimeRange.ever(),
        source: String? = null,
    ): T?

    /**
     * Aggregates [metric] over [timeRange], such as the total steps or the average heart rate.
     * Health Connect removes overlapping data from different sources.
     *
     * @param metric The metric of a record type, such as `StepsRecord.COUNT_TOTAL`.
     * @param timeRange The [HealthQueryTimeRange] to aggregate.
     * @param source The data source to filter by. Defaults to `null`, which includes all sources.
     *
     * @return The aggregated value in the unit of [metric], or `null` if there is no data or reading
     * the metric's record type isn't permitted.
     */
    suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        source: String? = null,
    ): T?

    /**
     * Aggregates [metric] over [timeRange] split into buckets of [interval], such as the daily steps
     * of a week.
     *
     * @param metric The metric of a record type, such as `StepsRecord.COUNT_TOTAL`.
     * @param timeRange The [HealthQueryTimeRange] to aggregate; it must have a start and an end.
     * @param interval The [AggregationInterval] to split [timeRange] into.
     * @param source The data source to filter by. Defaults to `null`, which includes all sources.
     *
     * @return The buckets in chronological order, or an empty list if reading the metric's record type
     * isn't permitted.
     */
    suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval,
        source: String? = null,
    ): List<AggregationBucket<T>>

    /**
     * Performs a continuous query for health data of the given [Record] [type].
     *
     * @param type The [Record] type to query.
     * @param timeRange The [HealthQueryTimeRange] to query data for.
     * @param interval The interval at which to perform the query. Defaults to 15 seconds.
     * @param anchor The anchor string to continue querying from a previous result. Defaults to `null`.
     * @param predicate An optional predicate to filter the results.
     *
     * @return A [Flow] emitting [QueryResult]s containing the queried records, deleted record IDs, and the next anchor.
     */
    fun <T : Record> continuousQuery(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        interval: Duration = 15.seconds,
        anchor: String? = null,
        predicate: ((T) -> Boolean)? = null,
    ): Flow<QueryResult<T>>

    /**
     * Checks if the app is authorized to read data of the given [Record] [type].
     *
     * @param type The [Record] type to check read authorization for.
     *
     * @return `true` if the app is authorized to read data of the specified [type], `false` otherwise.
     */
    suspend fun isAuthorizedToRead(type: AnyRecordType): Boolean

    /**
     * Checks if the app is authorized to write data of the given [Record] [type].
     *
     * @param type The [Record] type to check write authorization for.
     *
     * @return `true` if the app is authorized to write data of the specified [type], `false` otherwise.
     */
    suspend fun isAuthorizedToWrite(type: AnyRecordType): Boolean

    /**
     * Represents the configuration state of Health module
     */
    enum class ConfigState {
        Pending,
        Ongoing,
        Completed,
    }
}
