//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.core.coroutines

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class RunCatchingAsyncTest {
    @Test
    fun `returns a suspended block's value`() = runTest {
        // when
        val result = runCatchingAsync { delay(1); "value" }

        // then
        assertThat(result.getOrThrow()).isEqualTo("value")
    }

    @Test
    fun `returns the original failure`() = runTest {
        // given
        val failure = IOException("unavailable")

        // when
        val result = runCatchingAsync<Unit> { throw failure }

        // then
        assertThat(result.exceptionOrNull()).isSameInstanceAs(failure)
    }

    @Test
    fun `propagates the original cancellation without calling onFailure`() = runTest {
        // given
        val cancellation = CancellationException("cancelled")
        var handledAsFailure = false
        var propagated: CancellationException? = null

        // when
        try {
            runCatchingAsync<Unit> { throw cancellation }.onFailure { handledAsFailure = true }
        } catch (caught: CancellationException) {
            propagated = caught
        }

        // then
        assertThat(propagated).isSameInstanceAs(cancellation)
        assertThat(handledAsFailure).isFalse()
    }

    @Test
    fun `job cancellation unwinds the suspended block and runs cleanup`() = runTest {
        // given
        val started = CompletableDeferred<Unit>()
        var handledAsFailure = false
        var cleanedUp = false
        val job = launch {
            try {
                runCatchingAsync {
                    started.complete(Unit)
                    awaitCancellation()
                }.onFailure { handledAsFailure = true }
            } finally {
                cleanedUp = true
            }
        }
        started.await()

        // when
        job.cancelAndJoin()

        // then
        assertThat(handledAsFailure).isFalse()
        assertThat(cleanedUp).isTrue()
    }
}
