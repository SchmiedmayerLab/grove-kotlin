//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license

import org.grovealliance.ui.StringResource

/**
 * The app whose licenses are listed, shown above its dependencies.
 *
 * @property name the app's display name, also naming it in the dependencies' header
 * @property version the version of the app, when known
 * @property licenseIdentifier the SPDX identifier of the app's own license, when it has one
 * @property url where the app is published, opened when its row is tapped
 */
data class LicenseProject(
    val name: StringResource,
    val version: String?,
    val licenseIdentifier: String?,
    val url: String?,
)
