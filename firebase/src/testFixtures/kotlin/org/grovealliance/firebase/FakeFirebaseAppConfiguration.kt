//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class FakeFirebaseAppConfiguration : FirebaseAppConfiguration {
    private val configured = MutableStateFlow(false)
    override val isConfigured: StateFlow<Boolean> = configured

    fun setConfigured() {
        configured.value = true
    }

    override fun configure(options: FirebaseOptions): Result<FirebaseApp> = Result.failure(IllegalStateException("Not available in this fake"))

    override suspend fun awaitConfigured() {
        configured.first { it }
    }
}
