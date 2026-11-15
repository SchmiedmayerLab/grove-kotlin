//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import org.grovealliance.license.LicenseProject
import org.grovealliance.ui.StringResource

/**
 * Builds [LicenseProject]s; by default one with an empty name and no version, license or website.
 */
object LicenseProjectFixtures {
    fun create(
        name: StringResource = StringResource(""),
        version: String? = null,
        licenseIdentifier: String? = null,
        url: String? = null,
    ) = LicenseProject(
        name = name,
        version = version,
        licenseIdentifier = licenseIdentifier,
        url = url,
    )
}
