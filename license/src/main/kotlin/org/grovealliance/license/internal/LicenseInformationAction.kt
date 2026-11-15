//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

/**
 * An intent raised by the license information screen.
 */
internal sealed interface LicenseInformationAction {

    /**
     * The participant left the screen.
     */
    data object Close : LicenseInformationAction

    /**
     * The participant went back from a license text to the list of dependencies.
     */
    data object Back : LicenseInformationAction

    /**
     * The participant asked to read the dependencies again after they couldn't be read.
     */
    data object Retry : LicenseInformationAction

    /**
     * The participant opened the license text of the dependency identified by [id].
     */
    data class DependencySelected(val id: String) : LicenseInformationAction

    /**
     * The participant asked to open [url].
     */
    data class UrlSelected(val url: String) : LicenseInformationAction
}
