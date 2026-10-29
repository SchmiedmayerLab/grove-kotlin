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
import org.grovealliance.health.HealthChanges
import org.grovealliance.health.HealthConfigurationScope
import org.grovealliance.health.HealthConnectAvailability
import org.grovealliance.health.HealthDataAccessRequirements
import org.grovealliance.health.HealthQueryTimeRange
import org.grovealliance.health.HealthReadAccess
import org.grovealliance.health.QueryResult
import org.grovealliance.health.QuerySort
import org.grovealliance.health.RecordType
import java.time.Instant
import kotlin.time.Duration

/**
 * The [Health] module, delegating every operation to the current client of [clientProvider].
 */
@Suppress("TooManyFunctions")
internal class HealthImpl(
    private val configuration: HealthModuleConfiguration,
    private val clientProvider: HealthClientProvider,
    private val healthConnectStore: HealthConnectStore,
) : Health {
    private val healthClient get() = clientProvider.client.value

    /**
     * How the permission rationale is presented.
     */
    val privacyConfig: PrivacyConfig
        get() = configuration.privacyConfig

    override val dataAccessRequirements: HealthDataAccessRequirements
        get() = healthClient.dataAccessRequirements

    override val availability: StateFlow<HealthConnectAvailability>
        get() = clientProvider.availability

    override val isFullyAuthorizedState: StateFlow<Boolean>
        get() = clientProvider.isFullyAuthorizedState

    /**
     * Hands the result of a permission request to the current client.
     */
    fun onPermissionsGranted(granted: Set<String>) {
        healthClient.onPermissionsGranted(granted = granted)
    }

    /**
     * Tells the current client that a permission request ended without a result.
     */
    fun onPermissionRequestAbandoned() {
        healthClient.onPermissionRequestAbandoned()
    }

    override fun refreshAvailability() {
        clientProvider.refreshAvailability()
    }

    override fun installOrUpdateHealthConnect(): Boolean = healthConnectStore.openListing()

    override suspend fun requestPermissionsIfNeeded(activity: FragmentActivity): Boolean =
        healthClient.requestPermissionsIfNeeded(activity = activity)

    override suspend fun updateConfiguration(block: HealthConfigurationScope.() -> Unit) {
        val scope = HealthConfigurationScope().apply(block)
        clientProvider.updateConfiguration(components = scope.components.toSet())
    }

    override fun requestReadPermission(type: AnyRecordType, activity: FragmentActivity) {
        healthClient.requestReadPermission(type = type, activity = activity)
    }

    override fun requestWritePermission(type: AnyRecordType, activity: FragmentActivity) {
        healthClient.requestWritePermission(type = type, activity = activity)
    }

    override fun resetRecordCollection(type: AnyRecordType) {
        healthClient.resetRecordCollection(type)
    }

    override suspend fun insert(record: Record): Boolean = healthClient.insert(record)

    override suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
        sortedBy: QuerySort,
        limit: Int,
        predicate: ((T) -> Boolean)?,
    ): List<T> = healthClient.query(
        type = type,
        timeRange = timeRange,
        source = source,
        sortedBy = sortedBy,
        limit = limit,
        predicate = predicate,
    )

    override suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): QueryResult<T> = healthClient.query(
        type = type,
        timeRange = timeRange,
        anchor = anchor,
        predicate = predicate,
    )

    override suspend fun <T : Record> oldestSampleDate(type: RecordType<T>): Instant? = healthClient.oldestSampleDate(type)

    override suspend fun <T : Record> mostRecent(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T? = healthClient.query(
        type = type,
        timeRange = timeRange,
        source = source,
        sortedBy = QuerySort.BY_START_TIME_DESC,
        limit = 1,
        predicate = null,
    ).firstOrNull()

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T? = healthClient.aggregate(
        metric = metric,
        timeRange = timeRange,
        source = source,
    )

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval,
        source: String?,
    ): List<AggregationBucket<T>> = healthClient.aggregate(
        metric = metric,
        timeRange = timeRange,
        interval = interval,
        source = source,
    )

    override fun <T : Record> continuousQuery(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        interval: Duration,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): Flow<QueryResult<T>> = healthClient.continuousQuery(
        type = type,
        timeRange = timeRange,
        interval = interval,
        anchor = anchor,
        predicate = predicate,
    )

    override fun configure() {
        clientProvider.configure()
    }

    override suspend fun readAccess(type: AnyRecordType): HealthReadAccess = healthClient.readAccess(type = type)

    override suspend fun <T : Record> changes(type: RecordType<T>, token: String?): Result<HealthChanges<T>> =
        healthClient.changes(type = type, token = token)

    override suspend fun isAuthorizedToRead(type: AnyRecordType): Boolean = healthClient.isAuthorizedToRead(type)

    override suspend fun isAuthorizedToWrite(type: AnyRecordType): Boolean = healthClient.isAuthorizedToWrite(type)
}
