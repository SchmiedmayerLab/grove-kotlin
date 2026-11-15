//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license

/**
 * A library the app depends on.
 *
 * A dependency with a [licenseText] opens it; one without opens its [url]; one with neither can't
 * be opened.
 *
 * @property id identifies the dependency among all others
 * @property name the library's display name
 * @property version the version the app uses, when known
 * @property licenseIdentifier the SPDX identifier of the library's license, when it declares one
 * @property licenseText the full text of the library's licenses, when bundled with the app
 * @property url where the library is published, preferring its source repository
 */
data class Dependency(
    val id: String,
    val name: String,
    val version: String?,
    val licenseIdentifier: String?,
    val licenseText: String?,
    val url: String?,
)
