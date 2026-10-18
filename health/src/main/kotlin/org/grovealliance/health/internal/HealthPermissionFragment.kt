//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.PermissionController
import org.grovealliance.core.dependency
import org.grovealliance.health.Health
import org.grovealliance.health.healthLogger

/**
 * A [Fragment] that handles requesting health permissions
 *
 * This is a simple invisible fragment used internally by the [Health] module to request health permissions and supply permission granted
 * results back to the [Health] module.
 */
internal class HealthPermissionFragment : Fragment() {
    private val logger by healthLogger()
    private val health by dependency<HealthImpl>()
    private var isLaunched = false
    private var hasEnded = false

    private val launcher: ActivityResultLauncher<Set<String>> = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        logger.i { "Health permissions granted result: $granted" }
        hasEnded = true
        health.onPermissionsGranted(granted = granted)
        detach()
    }

    val permissions
        get() = requireArguments().getStringArrayList(PERMISSIONS_KEY)?.toSet().orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isLaunched = savedInstanceState?.getBoolean(LAUNCHED_KEY) ?: false
    }

    override fun onStart() {
        super.onStart()
        if (isLaunched || hasEnded) return
        val permissions = permissions
        if (permissions.isEmpty()) {
            logger.w { "Health permission fragment was launched without permissions to request" }
            abandon()
            return
        }
        logger.i { "Launching permission request for $permissions" }
        isLaunched = true
        launcher.launch(permissions)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(LAUNCHED_KEY, isLaunched)
    }

    override fun onDestroy() {
        super.onDestroy()
        // A configuration change re-creates this fragment, which then still receives the result.
        if (!hasEnded && activity?.isChangingConfigurations != true) {
            logger.w { "Health permission request for $permissions ended without a result" }
            hasEnded = true
            health.onPermissionRequestAbandoned()
        }
    }

    private fun abandon() {
        hasEnded = true
        health.onPermissionRequestAbandoned()
        detach()
    }

    private fun detach() {
        logger.i { "Detaching permission fragment for $permissions" }
        parentFragmentManager
            .beginTransaction()
            .remove(this)
            .commitAllowingStateLoss()
    }

    companion object {
        private const val TAG = "HealthPermissionFragment"
        private const val PERMISSIONS_KEY = "health.permissions.key"
        private const val LAUNCHED_KEY = "health.permissions.launched"

        fun startPermissionFlow(
            activity: FragmentActivity,
            permissions: Set<String>,
        ) = with(activity.supportFragmentManager) {
            val newPermissions = permissions.toMutableSet()
            val existingFragment = findFragmentByTag(TAG) as? HealthPermissionFragment
            existingFragment?.let {
                newPermissions.addAll(it.permissions)
                // The merged request replaces this one, so its removal doesn't end the request.
                it.hasEnded = true
                beginTransaction()
                    .remove(it)
                    .commitNowAllowingStateLoss()
            }
            val fragment = HealthPermissionFragment().apply {
                arguments = bundleOf(PERMISSIONS_KEY to ArrayList(newPermissions))
            }
            beginTransaction()
                .add(fragment, TAG)
                .commitNowAllowingStateLoss()
        }
    }
}
