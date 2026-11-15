//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

/**
 * What the license information screen asks its host to do.
 */
internal sealed interface LicenseInformationEvent {

    /**
     * The participant left the screen.
     */
    data object Dismissed : LicenseInformationEvent

    /**
     * The participant asked to open [url].
     */
    data class UrlOpened(val url: String) : LicenseInformationEvent
}
