//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.testing.AggregationResult
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.testing.stubs.MutableStub
import androidx.health.connect.client.time.TimeRangeFilter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.grovealliance.core.coroutines.Concurrency
import org.grovealliance.core.lifecycle.AppLifecycle
import org.grovealliance.core.time.FakeTimeProvider
import org.grovealliance.health.AggregationBucket
import org.grovealliance.health.AggregationInterval
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.CollectionMode
import org.grovealliance.health.CollectionTimeRange
import org.grovealliance.health.HealthConstraint
import org.grovealliance.health.HealthQueryTimeRange
import org.grovealliance.health.QuerySort
import org.grovealliance.health.RecordType
import org.grovealliance.storage.local.FakeLocalStorage
import org.grovealliance.testing.concurrency.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultHealthClientTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val permissionController = FakePermissionController(grantAll = false)
    private val features = FakeHealthConnectFeatures()
    private val fakeHealthConnectClient = FakeHealthConnectClient(permissionController = permissionController)
    private val healthConnectClient = FeaturedHealthConnectClient(
        delegate = fakeHealthConnectClient,
        features = features,
    )
    private val timeProvider = FakeTimeProvider().apply { setZone(zone = ZONE) }
    private lateinit var client: DefaultHealthClient

    @Before
    fun setUp() {
        client = DefaultHealthClient(
            configurations = setOf(RequestReadAccess(RecordType.steps)),
            standard = IgnoringHealthConstraint(),
            concurrency = Concurrency(),
            healthConnectClient = healthConnectClient,
            appLifecycle = AppLifecycle(),
            timeProvider = timeProvider,
            localStorage = FakeLocalStorage(),
        )
    }

    @Test
    fun `it should aggregate a metric over a time range`() = runTest {
        // given
        val requests = mutableListOf<AggregateRequest>()
        fakeHealthConnectClient.overrides.aggregate = MutableStub { request: AggregateRequest ->
            requests.add(request)
            stepsResult(count = 1234L)
        }

        // when
        val steps = client.aggregate(metric = StepsRecord.COUNT_TOTAL, timeRange = TWO_DAYS, source = null)

        // then
        assertThat(steps).isEqualTo(1234L)
        assertThat(requests.single()).isEqualTo(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(TWO_DAYS.start, TWO_DAYS.end),
            )
        )
    }

    @Test
    fun `it should aggregate a metric per local day`() = runTest {
        // given
        val requests = mutableListOf<AggregateGroupByPeriodRequest>()
        fakeHealthConnectClient.overrides.aggregateGroupByPeriod = MutableStub { request: AggregateGroupByPeriodRequest ->
            requests.add(request)
            listOf(
                AggregationResultGroupedByPeriod(
                    result = stepsResult(count = 100L),
                    startTime = LocalDateTime.of(2026, 3, 7, 0, 0),
                    endTime = LocalDateTime.of(2026, 3, 8, 0, 0),
                ),
                AggregationResultGroupedByPeriod(
                    result = stepsResult(count = null),
                    startTime = LocalDateTime.of(2026, 3, 8, 0, 0),
                    endTime = LocalDateTime.of(2026, 3, 9, 0, 0),
                ),
            )
        }

        // when
        val buckets = client.aggregate(
            metric = StepsRecord.COUNT_TOTAL,
            timeRange = TWO_DAYS,
            interval = AggregationInterval.daily,
            source = null,
        )

        // then
        assertThat(buckets).containsExactly(
            AggregationBucket(
                start = localInstant(day = 7),
                end = localInstant(day = 8),
                value = 100L,
            ),
            AggregationBucket(
                start = localInstant(day = 8),
                end = localInstant(day = 9),
                value = null,
            ),
        ).inOrder()
        assertThat(requests.single()).isEqualTo(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDateTime.of(2026, 3, 7, 0, 0),
                    LocalDateTime.of(2026, 3, 9, 0, 0),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
    }

    @Test
    fun `it should aggregate a metric per fixed interval from a source`() = runTest {
        // given
        val requests = mutableListOf<AggregateGroupByDurationRequest>()
        fakeHealthConnectClient.overrides.aggregateGroupByDuration = MutableStub { request: AggregateGroupByDurationRequest ->
            requests.add(request)
            listOf(
                AggregationResultGroupedByDuration(
                    result = stepsResult(count = 42L),
                    startTime = TWO_DAYS.start,
                    endTime = TWO_DAYS.start.plus(Duration.ofHours(1)),
                    zoneOffset = ZoneOffset.UTC,
                ),
            )
        }

        // when
        val buckets = client.aggregate(
            metric = StepsRecord.COUNT_TOTAL,
            timeRange = TWO_DAYS,
            interval = AggregationInterval.hourly,
            source = "com.example.watch",
        )

        // then
        assertThat(buckets.single()).isEqualTo(
            AggregationBucket(
                start = TWO_DAYS.start,
                end = TWO_DAYS.start.plus(Duration.ofHours(1)),
                value = 42L,
            )
        )
        assertThat(requests.single()).isEqualTo(
            AggregateGroupByDurationRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(TWO_DAYS.start, TWO_DAYS.end),
                timeRangeSlicer = Duration.ofHours(1),
                dataOriginFilter = setOf(DataOrigin("com.example.watch")),
            )
        )
    }

    @Test
    fun `it should aggregate nothing when reading is not permitted`() = runTest {
        // given
        fakeHealthConnectClient.overrides.aggregate = MutableStub { _: AggregateRequest -> throw SecurityException() }
        fakeHealthConnectClient.overrides.aggregateGroupByPeriod =
            MutableStub { _: AggregateGroupByPeriodRequest -> throw SecurityException() }

        // when
        val total = client.aggregate(metric = StepsRecord.COUNT_TOTAL, timeRange = TWO_DAYS, source = null)
        val buckets = client.aggregate(
            metric = StepsRecord.COUNT_TOTAL,
            timeRange = TWO_DAYS,
            interval = AggregationInterval.daily,
            source = null,
        )

        // then
        assertThat(total).isNull()
        assertThat(buckets).isEmpty()
    }

    @Test
    fun `it should refuse to split a time range without an end`() = runTest {
        // when
        val error = runCatching {
            client.aggregate(
                metric = StepsRecord.COUNT_TOTAL,
                timeRange = HealthQueryTimeRange.startingAt(TWO_DAYS.start),
                interval = AggregationInterval.daily,
                source = null,
            )
        }.exceptionOrNull()

        // then
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `it should read the newest records first when sorted by descending start time`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)
        val requests = mutableListOf<ReadRecordsRequest<*>>()
        fakeHealthConnectClient.overrides.readRecords = MutableStub { request: ReadRecordsRequest<*> ->
            requests.add(request)
            ReadRecordsResponse(records = emptyList<StepsRecord>(), pageToken = null)
        }

        // when
        client.query(
            type = RecordType.steps,
            timeRange = TWO_DAYS,
            source = null,
            sortedBy = QuerySort.BY_START_TIME_DESC,
            limit = 1,
            predicate = null,
        )

        // then
        assertThat(requests.single()).isEqualTo(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(TWO_DAYS.start, TWO_DAYS.end),
                ascendingOrder = false,
                pageSize = 1,
            )
        )
    }

    @Test
    fun `it should need only the configured permissions when Health Connect lacks history and background reads`() = runTest {
        // given
        client.addHealthDataCollector(collector(continueInBackground = true, scope = backgroundScope))

        // when
        val needed = client.neededPermissions()

        // then
        assertThat(needed).containsExactly(STEPS_READ)
    }

    @Test
    fun `it should need reading history where Health Connect supports it`() = runTest {
        // given
        features.setAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)

        // when
        val needed = client.neededPermissions()

        // then
        assertThat(needed).containsExactly(STEPS_READ, HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
    }

    @Test
    fun `it should need reading in the background when a collector continues in the background`() = runTest {
        // given
        features.setAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        client.addHealthDataCollector(collector(continueInBackground = true, scope = backgroundScope))

        // when
        val needed = client.neededPermissions()

        // then
        assertThat(needed).containsExactly(STEPS_READ, HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    @Test
    fun `it should not need reading in the background without a background collector`() = runTest {
        // given
        features.setAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
        client.addHealthDataCollector(collector(continueInBackground = false, scope = backgroundScope))

        // when
        val needed = client.neededPermissions()

        // then
        assertThat(needed).containsExactly(STEPS_READ)
    }

    @Test
    fun `it should not need permissions that are granted`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)

        // when
        val needed = client.neededPermissions()

        // then
        assertThat(needed).isEmpty()
    }

    @Test
    fun `it should report success without launching when nothing is needed`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)
        val launched = mutableListOf<Set<String>>()

        // when
        val result = client.requestPermissionsIfNeeded { launched += it }

        // then
        assertThat(result).isTrue()
        assertThat(launched).isEmpty()
    }

    @Test
    fun `it should launch the needed permissions and wait for the result`() = runTest {
        // given
        val launched = mutableListOf<Set<String>>()

        // when
        val result = async { client.requestPermissionsIfNeeded { launched += it } }
        runCurrent()

        // then
        assertThat(launched).containsExactly(setOf(STEPS_READ))
        assertThat(result.isCompleted).isFalse()
        result.cancel()
    }

    @Test
    fun `it should report success once every needed permission is granted`() = runTest {
        // given
        val result = async { client.requestPermissionsIfNeeded { } }
        runCurrent()
        permissionController.grantPermission(STEPS_READ)

        // when
        client.onPermissionsGranted(granted = setOf(STEPS_READ))

        // then
        assertThat(result.await()).isTrue()
        assertThat(client.isFullyAuthorizedState.value).isTrue()
    }

    @Test
    fun `it should report failure when a needed permission is denied`() = runTest {
        // given
        val result = async { client.requestPermissionsIfNeeded { } }
        runCurrent()

        // when
        client.onPermissionsGranted(granted = emptySet())

        // then
        assertThat(result.await()).isFalse()
        assertThat(client.isFullyAuthorizedState.value).isFalse()
    }

    @Test
    fun `it should report failure when the request ends without a result`() = runTest {
        // given
        val result = async { client.requestPermissionsIfNeeded { } }
        runCurrent()

        // when
        client.onPermissionRequestAbandoned()

        // then
        assertThat(result.await()).isFalse()
    }

    @Test
    fun `it should report success when the request ends without a result after everything was granted`() = runTest {
        // given
        val result = async { client.requestPermissionsIfNeeded { } }
        runCurrent()
        permissionController.grantPermission(STEPS_READ)

        // when
        client.onPermissionRequestAbandoned()

        // then
        assertThat(result.await()).isTrue()
    }

    @Test
    fun `it should need reading a record type collected by the runtime configuration`() = runTest {
        // given
        client.updateRuntimeConfiguration(components = setOf(collectRecord(RecordType.heartRate)))

        // when
        val needed = client.neededPermissions()

        // then
        assertThat(needed).containsExactly(STEPS_READ, RecordType.heartRate.readPermission)
    }

    @Test
    fun `it should require the runtime configuration on top of the registered one`() = runTest {
        // when
        client.updateRuntimeConfiguration(components = setOf(RequestReadAccess(RecordType.weight)))

        // then
        assertThat(client.dataAccessRequirements.read).containsExactly(RecordType.steps, RecordType.weight)
    }

    @Test
    fun `it should drop the previous runtime configuration when it is replaced`() = runTest {
        // given
        client.updateRuntimeConfiguration(components = setOf(collectRecord(RecordType.heartRate)))

        // when
        client.updateRuntimeConfiguration(components = setOf(RequestReadAccess(RecordType.weight)))

        // then
        assertThat(client.dataAccessRequirements.read).containsExactly(RecordType.steps, RecordType.weight)
    }

    @Test
    fun `record queries drain every page even when a page is empty`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)
        val requestedPages = mutableListOf<String?>()
        fakeHealthConnectClient.overrides.readRecords = MutableStub { request: ReadRecordsRequest<*> ->
            requestedPages += request.pageToken
            ReadRecordsResponse(records = emptyList<StepsRecord>(), pageToken = if (request.pageToken == null) "second" else null)
        }

        // when
        client.query(type = RecordType.steps, timeRange = TWO_DAYS)

        // then
        assertThat(requestedPages).containsExactly(null, "second").inOrder()
    }

    @Test
    fun `changes drain all pages before returning their next token`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)
        permissionController.grantPermission(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        val requestedTokens = mutableListOf<String>()
        fakeHealthConnectClient.overrides.getChanges = MutableStub { token: String ->
            requestedTokens += token
            ChangesResponse(
                changes = emptyList(),
                nextChangesToken = if (token == "first") "second" else "third",
                hasMore = token == "first",
                changesTokenExpired = false,
            )
        }

        // when
        val changes = client.changes(type = RecordType.steps, token = "first").getOrThrow()

        // then
        assertThat(requestedTokens).containsExactly("first", "second").inOrder()
        assertThat(changes.nextToken).isEqualTo("third")
        assertThat(changes.isReset).isFalse()
    }

    @Test
    fun `expired changes require a new full snapshot and never confirm deletions`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)
        permissionController.grantPermission(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        fakeHealthConnectClient.overrides.getChanges = MutableStub { _: String ->
            ChangesResponse(changes = emptyList(), nextChangesToken = "expired", hasMore = false, changesTokenExpired = true)
        }

        // when
        val changes = client.changes(type = RecordType.steps, token = "old").getOrThrow()

        // then
        assertThat(changes.isReset).isTrue()
        assertThat(changes.deletedIds).isEmpty()
        assertThat(changes.nextToken).isNotEqualTo("old")
    }

    @Test
    fun `missing read permission is a failed change read`() = runTest {
        // when
        val result = client.changes(type = RecordType.steps, token = null)

        // then
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `read access reports type history and background permissions separately`() = runTest {
        // given
        permissionController.grantPermission(STEPS_READ)
        permissionController.grantPermission(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)

        // when
        val access = client.readAccess(type = RecordType.steps)

        // then
        assertThat(access.records).isTrue()
        assertThat(access.history).isTrue()
        assertThat(access.background).isFalse()
    }

    @Suppress("UNCHECKED_CAST")
    private fun stepsResult(count: Long?) = AggregationResult(
        metrics = count?.let { mapOf(StepsRecord.COUNT_TOTAL to it) as Map<AggregateMetric<Any>, Any> }.orEmpty(),
    )

    private fun localInstant(day: Int): Instant = LocalDateTime.of(2026, 3, day, 0, 0).atZone(ZONE).toInstant()

    private fun collectRecord(recordType: AnyRecordType) = CollectRecord(
        recordType = recordType,
        start = CollectionMode.Automatic(pollingInterval = 1.minutes),
        continueInBackground = true,
    )

    private fun collector(continueInBackground: Boolean, scope: CoroutineScope) = HealthDataCollector(
        recordType = RecordType.steps,
        deliverySetting = HealthDataCollectorDeliverySetting(
            collectionMode = CollectionMode.Manual,
            continueInBackground = continueInBackground,
        ),
        timeRange = CollectionTimeRange.NewRecords,
        predicate = null,
        tokenStore = ChangesTokenStore(storage = FakeLocalStorage()),
        scope = scope,
        healthConstraint = IgnoringHealthConstraint(),
        client = healthConnectClient,
    )

    private class FeaturedHealthConnectClient(
        delegate: HealthConnectClient,
        override val features: HealthConnectFeatures,
    ) : HealthConnectClient by delegate

    private class FakeHealthConnectFeatures : HealthConnectFeatures {
        private val available = mutableSetOf<Int>()

        fun setAvailable(feature: Int) {
            available += feature
        }

        override fun getFeatureStatus(feature: Int): Int = if (feature in available) {
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } else {
            HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
        }
    }

    private class IgnoringHealthConstraint : HealthConstraint {
        override suspend fun <T : Record> handleNewRecords(addedRecords: Set<T>, type: RecordType<out T>) = Unit

        override suspend fun <T : Record> handleDeletedRecords(deletedRecordIds: Set<String>, type: RecordType<out T>) = Unit

        override suspend fun <T : Record> onFullyResyncRequired(type: RecordType<out T>) = Unit
    }

    private companion object {
        val STEPS_READ = RecordType.steps.readPermission
        val ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

        /**
         * Two local days across the start of daylight saving time, so the second day is 23 hours long.
         */
        val TWO_DAYS = HealthQueryTimeRange(
            start = LocalDateTime.of(2026, 3, 7, 0, 0).atZone(ZONE).toInstant(),
            end = LocalDateTime.of(2026, 3, 9, 0, 0).atZone(ZONE).toInstant(),
        )
    }
}
