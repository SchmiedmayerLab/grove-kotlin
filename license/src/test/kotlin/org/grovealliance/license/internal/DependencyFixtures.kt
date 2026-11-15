//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import org.grovealliance.license.Dependency

/**
 * Builds [Dependency]s; by default one with no version, license or website.
 */
object DependencyFixtures {
    fun create(
        id: String = "",
        name: String = "",
        version: String? = null,
        licenseIdentifier: String? = null,
        licenseText: String? = null,
        url: String? = null,
    ) = Dependency(
        id = id,
        name = name,
        version = version,
        licenseIdentifier = licenseIdentifier,
        licenseText = licenseText,
        url = url,
    )
}
