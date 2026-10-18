//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.grovealliance.core.coroutines.Concurrency
import org.grovealliance.core.lifecycle.AppLifecycle
import org.grovealliance.core.time.TimeProvider
import org.grovealliance.health.AggregationBucket
import org.grovealliance.health.AggregationInterval
import org.grovealliance.health.AnyRecordType
import org.grovealliance.health.CollectionMode
import org.grovealliance.health.Health
import org.grovealliance.health.HealthConstraint
import org.grovealliance.health.HealthDataAccessRequirements
import org.grovealliance.health.HealthQueryTimeRange
import org.grovealliance.health.QueryResult
import org.grovealliance.health.QuerySort
import org.grovealliance.health.RecordType
import org.grovealliance.health.healthLogger
import org.grovealliance.storage.local.LocalStorage
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * The default implementation of [HealthClient] using Health Connect.
 */
@Suppress("TooManyFunctions")
internal class DefaultHealthClient @Suppress("detekt:LongParameterList") constructor(
    private val configurations: Set<HealthConfigurationComponent>,
    private val standard: HealthConstraint?,
    concurrency: Concurrency,
    val healthConnectClient: HealthConnectClient,
    private val appLifecycle: AppLifecycle,
    private val timeProvider: TimeProvider,
    localStorage: LocalStorage,
) : HealthClient {

    private val mainDispatcher = concurrency.mainImmediateDispatcher()
    private val mainScope = concurrency.mainImmediateCoroutineScope()
    val ioScope = concurrency.ioCoroutineScope()
    private val permissionController = healthConnectClient.permissionController
    private val configuredRequirements = configurations.dataAccessRequirements()

    private val logger by healthLogger()
    private val registeredDataCollectors = CopyOnWriteArrayList<HealthDataCollector>()

    private val runtimeConfigurationMutex = Mutex()

    @Volatile
    private var runtimeComponents: Set<HealthConfigurationComponent> = emptySet()

    override val dataAccessRequirements: HealthDataAccessRequirements
        get() = configuredRequirements + runtimeComponents.dataAccessRequirements()
    private val pendingPermissionRequests = CopyOnWriteArrayList<CompletableDeferred<Unit>>()

    val changesTokenStore = ChangesTokenStore(storage = localStorage)

    override var configState: Health.ConfigState = Health.ConfigState.Pending
        private set

    private val _isFullyAuthorizedState = MutableStateFlow(false)
    override val isFullyAuthorizedState = _isFullyAuthorizedState.asStateFlow()

    override fun configure() {
        ioScope.launch {
            logger.i { "Started configuration of health client" }
            configState = Health.ConfigState.Ongoing
            updateIsFullyAuthorizedState()
            configurations.forEach {
                it.configure(client = this@DefaultHealthClient, standard = standard)
            }
            setupAppLifecycle()
            configState = Health.ConfigState.Completed
            logger.i { "Completed configuration of health client" }
        }
    }

    override fun onPermissionsGranted(granted: Set<String>) {
        logger.i { "Health permissions granted: $granted" }
        mainScope.launch {
            endPendingPermissionRequests()
            registeredDataCollectors.forEach { collector ->
                if (granted.contains(collector.recordType.readPermission)) {
                    startAutomaticDataCollectionIfPossible(collector)
                }
            }
        }
    }

    override fun onPermissionRequestAbandoned() {
        logger.i { "Health permission request ended without a result" }
        mainScope.launch {
            endPendingPermissionRequests()
        }
    }

    override suspend fun requestPermissionsIfNeeded(activity: FragmentActivity): Boolean =
        requestPermissionsIfNeeded { permissions -> startPermissionFlow(activity = activity, permissions = permissions) }

    /**
     * Launches the permission flow for every needed permission through [launch], and suspends until the
     * request ends in [onPermissionsGranted] or [onPermissionRequestAbandoned].
     *
     * @return `true` if every needed permission is granted afterwards, `false` otherwise.
     */
    internal suspend fun requestPermissionsIfNeeded(launch: (Set<String>) -> Unit): Boolean {
        val neededPermissions = neededPermissions()
        if (neededPermissions.isEmpty()) return true
        val result = CompletableDeferred<Unit>()
        pendingPermissionRequests.add(result)
        try {
            withContext(mainDispatcher) { launch(neededPermissions) }
            result.await()
        } finally {
            pendingPermissionRequests.remove(result)
        }
        return neededPermissions().isEmpty()
    }

    override suspend fun updateRuntimeConfiguration(components: Set<HealthConfigurationComponent>) {
        runtimeConfigurationMutex.withLock {
            val previous = runtimeComponents
            runtimeComponents = components
            val stillCollected = (configurations + components).collectedRecordTypes()
            (previous.collectedRecordTypes() - stillCollected).forEach { type ->
                logger.i { "Stopped collecting ${type.identifier}: no longer configured" }
                resetCollectors(type)
            }
            components.forEach { it.configure(client = this, standard = standard) }
            updateIsFullyAuthorizedState()
        }
    }

    override fun requestReadPermission(type: AnyRecordType, activity: FragmentActivity) {
        mainScope.launch {
            if (isAuthorizedToRead(type).not()) {
                startPermissionFlow(activity = activity, permissions = setOf(type.readPermission))
            }
        }
    }

    override fun requestWritePermission(type: AnyRecordType, activity: FragmentActivity) {
        mainScope.launch {
            if (isAuthorizedToWrite(type).not()) {
                startPermissionFlow(activity = activity, permissions = setOf(type.writePermission))
            }
        }
    }

    override fun resetRecordCollection(type: AnyRecordType) {
        ioScope.launch {
            resetCollectors(type)
        }
    }

    private suspend fun resetCollectors(type: AnyRecordType) {
        val collectorsToReset = registeredDataCollectors.filter { it.recordType == type }
        changesTokenStore.deleteToken(type)
        collectorsToReset.forEach { collector ->
            collector.stopDataCollection()
        }
        registeredDataCollectors.removeAll(collectorsToReset)
    }

    private fun Set<HealthConfigurationComponent>.collectedRecordTypes(): Set<AnyRecordType> =
        filterIsInstance<CollectRecord>().map { it.recordType }.toSet()

    override suspend fun insert(record: Record): Boolean {
        val type = RecordType.from(record)
        if (isAuthorizedToWrite(type).not()) {
            logger.w { "Not authorized to write data of type: ${type.identifier}" }
            return false
        }
        return runCatching {
            healthConnectClient.insertRecords(listOf(record))
        }.onSuccess {
            logger.i { "Successfully inserted record $record (${it.recordIdsList})" }
        }.onFailure { e ->
            logger.e(e) { "Failed to insert record" }
        }.isSuccess
    }

    override suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
        sortedBy: QuerySort,
        limit: Int,
        predicate: ((T) -> Boolean)?,
    ): List<T> {
        if (isAuthorizedToRead(type).not()) {
            logger.w { "Not authorized to read data of type: ${type.identifier}" }
            return emptyList()
        }
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = type.type,
                timeRangeFilter = TimeRangeFilter.between(timeRange.start, timeRange.end),
                dataOriginFilter = dataOrigins(source),
                ascendingOrder = sortedBy != QuerySort.BY_START_TIME_DESC,
                pageSize = limit,
            )
        )

        val filtered = response.records.filter { predicate?.invoke(it) ?: true }
        return when (sortedBy) {
            QuerySort.NONE -> filtered
            QuerySort.BY_START_TIME_ASC -> filtered.sortedBy { it.startTime() }
            QuerySort.BY_START_TIME_DESC -> filtered.sortedByDescending { it.startTime() }
        }
    }

    override suspend fun <T : Record> query(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): QueryResult<T> {
        if (!isAuthorizedToRead(type) || !isAllowedToReadInCurrentLifecycle()) {
            logger.w { "Not authorized to read data for ${type.identifier}" }
            return QueryResult(added = emptyList(), deletedIds = emptyList(), nextAnchor = anchor)
        }
        val token = anchor ?: getChangesToken(type)
        val response = healthConnectClient.getChanges(token)

        if (response.changesTokenExpired) {
            val newToken = getChangesToken(type)
            val changes = healthConnectClient.getChanges(newToken)
            return createQueryResult(
                response = changes,
                type = type,
                timeRange = timeRange,
                predicate = predicate,
            )
        }

        return createQueryResult(
            response = response,
            type = type,
            timeRange = timeRange,
            predicate = predicate,
        )
    }

    override fun <T : Record> continuousQuery(
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        interval: Duration,
        anchor: String?,
        predicate: ((T) -> Boolean)?,
    ): Flow<QueryResult<T>> = flow {
        if (!isAuthorizedToRead(type) || !isAllowedToReadInCurrentLifecycle()) {
            logger.w { "Not authorized to read ${type.identifier}" }
            emit(
                QueryResult(
                    added = emptyList(),
                    deletedIds = emptyList(),
                    nextAnchor = null
                )
            )
            return@flow
        }

        var currentAnchor = anchor ?: getChangesToken(type)

        while (currentCoroutineContext().isActive) {
            val result = query(
                type = type,
                timeRange = timeRange,
                anchor = currentAnchor,
                predicate = predicate,
            )

            emit(
                QueryResult(
                    added = result.added,
                    deletedIds = result.deletedIds,
                    nextAnchor = result.nextAnchor
                )
            )

            result.nextAnchor?.let { currentAnchor = it }
            delay(interval)
        }
    }

    override suspend fun <T : Record> oldestSampleDate(type: RecordType<T>): Instant? {
        if (!isAuthorizedToRead(type)) {
            logger.w { "Not authorized to read data for ${type.identifier}" }
            return null
        }

        val response = healthConnectClient.readRecords(
            request = ReadRecordsRequest(
                recordType = type.type,
                timeRangeFilter = TimeRangeFilter.after(Instant.MIN),
                pageSize = 1
            )
        )

        return response.records.firstOrNull()?.startTime()
    }

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        source: String?,
    ): T? = readingIfPermitted {
        healthConnectClient.aggregate(
            AggregateRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(timeRange.start, timeRange.end),
                dataOriginFilter = dataOrigins(source),
            )
        )[metric]
    }

    override suspend fun <T : Any> aggregate(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval,
        source: String?,
    ): List<AggregationBucket<T>> {
        require(timeRange.start != Instant.MIN && !timeRange.isOpenEnded) { "timeRange must have a start and an end" }
        return readingIfPermitted {
            when (interval) {
                is AggregationInterval.Fixed -> aggregateByDuration(metric, timeRange, interval, source)
                is AggregationInterval.Calendar -> aggregateByPeriod(metric, timeRange, interval, source)
            }
        }.orEmpty()
    }

    private suspend fun <T : Any> aggregateByDuration(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval.Fixed,
        source: String?,
    ): List<AggregationBucket<T>> = healthConnectClient.aggregateGroupByDuration(
        AggregateGroupByDurationRequest(
            metrics = setOf(metric),
            timeRangeFilter = TimeRangeFilter.between(timeRange.start, timeRange.end),
            timeRangeSlicer = interval.duration.toJavaDuration(),
            dataOriginFilter = dataOrigins(source),
        )
    ).map { group ->
        AggregationBucket(
            start = group.startTime,
            end = group.endTime,
            value = group.result[metric],
        )
    }

    private suspend fun <T : Any> aggregateByPeriod(
        metric: AggregateMetric<T>,
        timeRange: HealthQueryTimeRange,
        interval: AggregationInterval.Calendar,
        source: String?,
    ): List<AggregationBucket<T>> {
        val zone = timeProvider.currentZone()
        return healthConnectClient.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(
                    LocalDateTime.ofInstant(timeRange.start, zone),
                    LocalDateTime.ofInstant(timeRange.end, zone),
                ),
                timeRangeSlicer = interval.period,
                dataOriginFilter = dataOrigins(source),
            )
        ).map { group ->
            AggregationBucket(
                start = group.startTime.atZone(zone).toInstant(),
                end = group.endTime.atZone(zone).toInstant(),
                value = group.result[metric],
            )
        }
    }

    /**
     * Runs [read], or returns `null` when Health Connect rejects it for lacking a permission, such as
     * reading the record type, reading history or reading in the background.
     */
    private suspend fun <R> readingIfPermitted(read: suspend () -> R): R? = try {
        read()
    } catch (error: SecurityException) {
        logger.w(error) { "Not permitted to read: ${error.message}" }
        null
    }

    private fun dataOrigins(source: String?): Set<DataOrigin> = source?.let { setOf(DataOrigin(it)) } ?: emptySet()

    override suspend fun isAuthorizedToRead(type: AnyRecordType): Boolean {
        return getGrantedPermissions().contains(type.readPermission)
    }

    override suspend fun isAuthorizedToWrite(type: AnyRecordType): Boolean {
        return getGrantedPermissions().contains(type.writePermission)
    }

    /**
     * The permissions still to be granted: every read and write permission in [dataAccessRequirements],
     * plus reading history and, when a collector continues in the background, reading in the
     * background. The last two are only included where the installed Health Connect supports them,
     * since they can't be granted otherwise.
     */
    internal suspend fun neededPermissions(): Set<String> {
        val permissions = buildSet {
            with(dataAccessRequirements) {
                addAll(read.map { it.readPermission })
                addAll(write.map { it.writePermission })
            }
            if (isFeatureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)) {
                add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
            }
            if (registeredDataCollectors.any { it.deliverySetting.continueInBackground } &&
                isFeatureAvailable(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)
            ) {
                add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
            }
        }
        return (permissions - getGrantedPermissions()).toSet()
    }

    private fun isFeatureAvailable(feature: Int): Boolean = runCatching {
        healthConnectClient.features.getFeatureStatus(feature) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrElse {
        logger.e(it) { "Failed to read the status of Health Connect feature $feature" }
        false
    }

    override suspend fun addHealthDataCollector(collector: HealthDataCollector) {
        val existingIndex = registeredDataCollectors.indexOfFirst { it.recordType == collector.recordType }
        val action: CollectorAction = if (existingIndex == -1) {
            CollectorAction.Add
        } else {
            val existing = registeredDataCollectors[existingIndex]
            val existingSetting = existing.deliverySetting
            val newSetting = collector.deliverySetting
            when {
                existingSetting == newSetting -> CollectorAction.Ignore
                existingSetting.continueInBackground && !newSetting.continueInBackground -> CollectorAction.Ignore
                !existingSetting.continueInBackground && newSetting.continueInBackground -> CollectorAction.Replace(existingIndex)
                else -> CollectorAction.Ignore
            }
        }

        when (action) {
            is CollectorAction.Add -> {
                registeredDataCollectors.add(collector)
                startAutomaticDataCollectionIfPossible(collector)
                logger.i { "Added HealthDataCollector for ${collector.recordType.identifier}" }
            }

            is CollectorAction.Replace -> {
                val existing = registeredDataCollectors.getOrNull(action.index)
                existing?.stopDataCollection()
                registeredDataCollectors[action.index] = collector
                startAutomaticDataCollectionIfPossible(collector)
                logger.i { "Replaced HealthDataCollector for ${collector.recordType.identifier}" }
            }

            is CollectorAction.Ignore -> {
                logger.i { "Ignored adding HealthDataCollector for ${collector.recordType.identifier}" }
            }
        }
    }

    override suspend fun triggerDataSourceCollection() {
        registeredDataCollectors.forEach { collector ->
            if (collector.deliverySetting.collectionMode is CollectionMode.Manual && isAuthorizedToRead(collector.recordType)) {
                collector.startDataCollection()
            }
        }
    }

    private suspend fun startAutomaticDataCollectionIfPossible(collector: HealthDataCollector) {
        if (collector.isActive.not() &&
            collector.deliverySetting.collectionMode is CollectionMode.Automatic &&
            isAuthorizedToRead(collector.recordType)
        ) {
            collector.startDataCollection()
            logger.i { "Started automatic data collection for ${collector.recordType.identifier}" }
        }
    }

    private fun startPermissionFlow(activity: FragmentActivity, permissions: Set<String>) {
        HealthPermissionFragment.startPermissionFlow(activity = activity, permissions = permissions)
    }

    private suspend fun endPendingPermissionRequests() {
        updateIsFullyAuthorizedState()
        pendingPermissionRequests.forEach { it.complete(Unit) }
    }

    private suspend fun updateIsFullyAuthorizedState() {
        _isFullyAuthorizedState.update { neededPermissions().isEmpty() }
    }

    private fun setupAppLifecycle() {
        ioScope.launch {
            appLifecycle.state.collect { state ->
                when (state) {
                    AppLifecycle.State.BACKGROUND -> {
                        val hasBackgroundPermission = hasBackgroundPermission()
                        registeredDataCollectors.forEach { collector ->
                            val continuesInBackground = collector.deliverySetting.continueInBackground
                            if (continuesInBackground.not() || hasBackgroundPermission.not()) {
                                collector.stopDataCollection()
                                logger.i { "Stopped collection for ${collector.recordType.identifier} due to to background" }
                            }
                        }
                    }

                    AppLifecycle.State.FOREGROUND -> {
                        registeredDataCollectors.forEach { collector ->
                            logger.i { "Attempting to start collection for ${collector.recordType.identifier} due to foreground" }
                            startAutomaticDataCollectionIfPossible(collector)
                        }
                    }
                }
            }
        }
    }

    private suspend fun hasBackgroundPermission(): Boolean {
        return getGrantedPermissions().contains(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
    }

    private suspend fun isAllowedToReadInCurrentLifecycle(): Boolean {
        return appLifecycle.isInForeground || hasBackgroundPermission()
    }

    private suspend fun getGrantedPermissions(): Set<String> = runCatching {
        permissionController.getGrantedPermissions()
    }.getOrElse {
        logger.e(it) { "Failed to get granted permissions" }
        emptySet()
    }

    private suspend fun getChangesToken(type: AnyRecordType): String {
        return healthConnectClient.getChangesToken(ChangesTokenRequest(setOf(type.type)))
    }

    private fun <T : Record> createQueryResult(
        response: ChangesResponse,
        type: RecordType<T>,
        timeRange: HealthQueryTimeRange,
        predicate: ((T) -> Boolean)?,
    ): QueryResult<T> {
        val added = mutableListOf<T>()
        val deleted = mutableListOf<String>()

        response.changes.forEach { change ->
            when (change) {
                is UpsertionChange -> {
                    val rec = change.record
                    if (type.type.isInstance(rec)) {
                        @Suppress("UNCHECKED_CAST")
                        val typed = rec as T
                        if (matchesAnchoredQueryFilter(typed, timeRange, predicate)) {
                            added += typed
                        }
                    }
                }

                is DeletionChange ->
                    deleted += change.recordId
            }
        }

        return QueryResult(
            added = added,
            deletedIds = deleted,
            nextAnchor = response.nextChangesToken
        )
    }

    private fun <T : Record> matchesAnchoredQueryFilter(
        record: T,
        timeRange: HealthQueryTimeRange,
        predicate: ((T) -> Boolean)?,
    ): Boolean {
        if (predicate?.invoke(record) == false) return false
        val start = record.startTime() ?: return true
        return start >= timeRange.start && start <= timeRange.end
    }

    private sealed interface CollectorAction {
        data object Add : CollectorAction
        data object Ignore : CollectorAction
        data class Replace(val index: Int) : CollectorAction
    }
}
