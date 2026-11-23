//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.testing.FakeHealthConnectClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.grovealliance.health.CollectionMode
import org.grovealliance.health.CollectionTimeRange
import org.grovealliance.health.HealthConstraint
import org.grovealliance.health.RecordType
import org.grovealliance.storage.local.FakeLocalStorage
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class HealthDataCollectorTest {
    private val client = FakeHealthConnectClient()
    private val constraint = RecordingHealthConstraint()

    @Test
    fun `delivers changes again after the handler failed on them`() = runTest {
        // given
        insertAfterToken()
        constraint.setFailure(error = IllegalStateException("not ready"))
        collector(scope = this).startDataCollection()
        runCurrent()
        constraint.setFailure(error = null)

        // when
        advanceTimeBy(INTERVAL)
        runCurrent()

        // then
        assertThat(constraint.deliveries).isEqualTo(2)
        assertThat(constraint.handled).hasSize(1)
        stopAll()
    }

    @Test
    fun `does not deliver handled changes again`() = runTest {
        // given
        insertAfterToken()
        collector(scope = this).startDataCollection()
        runCurrent()

        // when
        advanceTimeBy(INTERVAL)
        runCurrent()

        // then
        assertThat(constraint.deliveries).isEqualTo(1)
        stopAll()
    }

    @Test
    fun `delivers manually collected changes again after the handler failed on them`() = runTest {
        // given
        insertAfterToken()
        constraint.setFailure(error = IllegalStateException("not ready"))
        collector(scope = this, mode = CollectionMode.Manual).startDataCollection()
        runCurrent()
        constraint.setFailure(error = null)

        // when
        collector(scope = this, mode = CollectionMode.Manual).startDataCollection()
        runCurrent()

        // then
        assertThat(constraint.deliveries).isEqualTo(2)
        assertThat(constraint.handled).hasSize(1)
    }

    private val tokenStore = ChangesTokenStore(storage = FakeLocalStorage())
    private val collectors = mutableListOf<HealthDataCollector>()

    /**
     * Stores a changes token for steps, then inserts one steps record after it.
     */
    private suspend fun insertAfterToken() {
        tokenStore.storeToken(
            recordType = RecordType.steps,
            token = client.getChangesToken(request = ChangesTokenRequest(recordTypes = setOf(StepsRecord::class))),
        )
        client.insertRecords(records = listOf(steps()))
    }

    private fun collector(
        scope: TestScope,
        mode: CollectionMode = CollectionMode.Automatic(pollingInterval = INTERVAL),
    ) = HealthDataCollector(
        recordType = RecordType.steps,
        deliverySetting = HealthDataCollectorDeliverySetting(
            collectionMode = mode,
            continueInBackground = false,
        ),
        timeRange = CollectionTimeRange.NewRecords,
        predicate = null,
        tokenStore = tokenStore,
        scope = scope.backgroundScope,
        healthConstraint = constraint,
        client = client,
    ).also { collectors += it }

    private fun stopAll() = collectors.forEach { it.stopDataCollection() }

    private fun steps() = StepsRecord(
        startTime = Instant.parse("2026-01-01T08:00:00Z"),
        startZoneOffset = ZoneOffset.UTC,
        endTime = Instant.parse("2026-01-01T09:00:00Z"),
        endZoneOffset = ZoneOffset.UTC,
        count = 1_200,
        metadata = Metadata.manualEntry(),
    )

    /**
     * A [HealthConstraint] that counts deliveries of new records and fails them with the error set
     * through [setFailure].
     */
    private class RecordingHealthConstraint : HealthConstraint {
        private var failure: Throwable? = null

        var deliveries = 0
            private set

        val handled = mutableListOf<Record>()

        fun setFailure(error: Throwable?) {
            failure = error
        }

        override suspend fun <T : Record> handleNewRecords(addedRecords: Set<T>, type: RecordType<out T>) {
            deliveries += 1
            failure?.let { throw it }
            handled += addedRecords
        }

        override suspend fun <T : Record> handleDeletedRecords(deletedRecordIds: Set<String>, type: RecordType<out T>) = Unit

        override suspend fun <T : Record> onFullyResyncRequired(type: RecordType<out T>) = Unit
    }

    private companion object {
        val INTERVAL = 1.minutes
    }
}
