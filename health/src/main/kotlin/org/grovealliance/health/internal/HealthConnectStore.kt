//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.grovealliance.health.healthLogger

/**
 * Opens the store listing of the Health Connect app from the application [context].
 */
internal class HealthConnectStore(private val context: Context) {
    private val logger by healthLogger()

    /**
     * Opens the listing in the Play Store app, falling back to the listing on the web.
     *
     * The Play Store link returns the user to Health Connect's onboarding once it is installed.
     *
     * @return `true` if either listing was opened, `false` if no app could open them.
     */
    fun openListing(): Boolean {
        val playStore = Intent(Intent.ACTION_VIEW).apply {
            setPackage(PLAY_STORE_PACKAGE)
            data = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=$ONBOARDING_URL")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"),
        )
        return start(playStore) || start(web)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        logger.w(e) { "No app can open ${intent.data}" }
        false
    }

    private companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val ONBOARDING_URL = "healthconnect%3A%2F%2Fonboarding"
    }
}
