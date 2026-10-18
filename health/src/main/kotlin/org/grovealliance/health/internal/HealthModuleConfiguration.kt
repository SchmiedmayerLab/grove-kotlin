//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.internal

import org.grovealliance.health.HealthDataAccessRequirements

/**
 * What the app configured the [org.grovealliance.health.Health] module with.
 *
 * @property components The configured access requests and collections.
 * @property privacyConfig How the permission rationale is presented.
 */
internal data class HealthModuleConfiguration(
    val components: Set<HealthConfigurationComponent>,
    val privacyConfig: PrivacyConfig,
) {
    /**
     * The combined read and write access of all [components].
     */
    val dataAccessRequirements: HealthDataAccessRequirements = components.dataAccessRequirements()
}
