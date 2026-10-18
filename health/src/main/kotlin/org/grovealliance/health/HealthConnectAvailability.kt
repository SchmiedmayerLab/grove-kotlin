//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

/**
 * Whether Health Connect can be used on this device.
 *
 * Below Android 14, Health Connect is a separate app that may be missing or outdated; from Android 14
 * on it is part of the system.
 */
enum class HealthConnectAvailability {
    /**
     * Health Connect is ready, and every [Health] operation reaches it.
     */
    Available,

    /**
     * Health Connect must be installed or updated before it can be used; see
     * [Health.installOrUpdateHealthConnect].
     */
    UpdateRequired,

    /**
     * Health Connect isn't supported on this device or in the current user profile.
     */
    Unavailable,
}
