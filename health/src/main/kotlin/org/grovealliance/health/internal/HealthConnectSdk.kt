//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import org.grovealliance.health.HealthConnectAvailability
import org.grovealliance.health.healthLogger

/**
 * Entry point to the Health Connect SDK: reports its availability and creates clients.
 */
internal interface HealthConnectSdk {
    /**
     * The current availability of Health Connect.
     */
    fun availability(): HealthConnectAvailability

    /**
     * Creates a client bound to Health Connect, or returns `null` when it can't be reached.
     */
    fun createClient(): HealthConnectClient?
}

/**
 * The [HealthConnectSdk] backed by [HealthConnectClient] on the given [context].
 */
internal class DefaultHealthConnectSdk(private val context: Context) : HealthConnectSdk {
    private val logger by healthLogger()

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.Unavailable
        }

    override fun createClient(): HealthConnectClient? = runCatching {
        HealthConnectClient.getOrCreate(context)
    }.onFailure {
        logger.w(it) { "Health Connect client could not be created" }
    }.getOrNull()
}
