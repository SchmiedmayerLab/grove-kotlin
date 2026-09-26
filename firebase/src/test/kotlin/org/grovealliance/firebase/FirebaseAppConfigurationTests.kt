//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.firebase

import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.grovealliance.core.dependency
import org.grovealliance.testing.core.testGroveApplication
import org.junit.Test

/**
 * Guards the deferred-initialization contract. Actually initializing a Firebase app needs a real
 * Android runtime, so the resume path is covered by the app's instrumented tests; what matters here
 * is that a module registered without options stays unconfigured and keeps its waiters suspended
 * rather than letting them through to a Firebase API that does not exist yet.
 */
class FirebaseAppConfigurationTests {

    @Test
    fun `it should not report configured when registered without options`() {
        // given
        testGroveApplication {
            firebaseApp()
        }

        // when
        val configuration = dependency<FirebaseAppConfiguration>().value

        // then
        assertThat(configuration.isConfigured.value).isFalse()
    }

    @Test
    fun `it should report a failure when the app cannot be initialized`() {
        // given
        testGroveApplication {
            firebaseApp()
        }
        val configuration = dependency<FirebaseAppConfiguration>().value

        // when
        val result = configuration.configure(options = OPTIONS)

        // then
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `it should stay unconfigured when initialization failed`() {
        // given
        testGroveApplication {
            firebaseApp()
        }
        val configuration = dependency<FirebaseAppConfiguration>().value

        // when
        configuration.configure(options = OPTIONS)

        // then
        assertThat(configuration.isConfigured.value).isFalse()
    }

    @Test
    fun `it should not report configured when registered with options it cannot initialize`() {
        // given
        testGroveApplication {
            firebaseApp(options = OPTIONS)
        }

        // when
        val configuration = dependency<FirebaseAppConfiguration>().value

        // then
        assertThat(configuration.isConfigured.value).isFalse()
    }

    @Test
    fun `it should not run the initialization hook when initialization failed`() {
        // given
        var ranHook = false
        testGroveApplication {
            firebaseApp(options = OPTIONS, onInitialized = { ranHook = true })
        }

        // when
        dependency<FirebaseAppConfiguration>().value.configure(options = OPTIONS)

        // then
        assertThat(ranHook).isFalse()
    }

    @Test
    fun `it should keep awaitConfigured suspended while unconfigured`() = runTest {
        // given
        testGroveApplication {
            firebaseApp()
        }
        val configuration = dependency<FirebaseAppConfiguration>().value

        // when
        val completed = withTimeoutOrNull(timeMillis = 1_000) {
            configuration.awaitConfigured()
            true
        }

        // then
        assertThat(completed).isNull()
    }

    private companion object {
        val OPTIONS: FirebaseOptions = FirebaseOptions.Builder()
            .setProjectId("grove-test")
            .setApplicationId("1:000000000000:android:0000000000000000000000")
            .setApiKey("test-api-key")
            .build()
    }
}
