//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.export.internal

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.grovealliance.health.RecordType
import org.grovealliance.health.export.BulkExportSessionId
import org.grovealliance.health.export.ExportBatch
import org.grovealliance.storage.local.FakeLocalStorage
import org.junit.Test
import java.time.Instant

class ExportCheckpointStoreTest {
    private val id = BulkExportSessionId(value = "historical")
    private val store = ExportCheckpointStoreImpl(
        storage = FakeLocalStorage(),
        mapper = ExportCheckpointMapperImpl(),
    )
    private val steps = ExportBatch(
        recordType = RecordType.steps,
        start = Instant.parse("2026-01-01T00:00:00Z"),
        end = Instant.parse("2026-02-01T00:00:00Z"),
        outcome = null,
    )

    @Test
    fun `restores the saved batches with failed ones pending again`() = runTest {
        // given
        store.save(
            id = id,
            pending = listOf(steps, steps.copy(outcome = Result.failure(IllegalStateException("Reading failed.")))),
            completed = listOf(steps.copy(recordType = RecordType.weight, outcome = Result.success(Unit))),
        ).getOrThrow()

        // when
        val restored = store.load(id = id, recordTypes = setOf(RecordType.steps, RecordType.weight)).getOrThrow()

        // then
        assertThat(restored).isEqualTo(
            RestoredBatches(
                pending = listOf(steps, steps),
                completed = listOf(steps.copy(recordType = RecordType.weight, outcome = Result.success(Unit))),
            ),
        )
    }

    @Test
    fun `drops the batches of record types no longer exported`() = runTest {
        // given
        store.save(
            id = id,
            pending = listOf(steps, steps.copy(recordType = RecordType.weight)),
            completed = emptyList(),
        ).getOrThrow()

        // when
        val restored = store.load(id = id, recordTypes = setOf(RecordType.steps)).getOrThrow()

        // then
        assertThat(restored?.pending).containsExactly(steps)
    }

    @Test
    fun `has no checkpoint before saving and after deleting`() = runTest {
        // given
        store.save(
            id = id,
            pending = listOf(steps),
            completed = emptyList(),
        ).getOrThrow()

        // when
        store.delete(id = id).getOrThrow()

        // then
        assertThat(store.load(id = id, recordTypes = setOf(RecordType.steps)).getOrThrow()).isNull()
    }
}
