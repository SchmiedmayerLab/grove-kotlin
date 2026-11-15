//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.GroveErrorLayout
import org.grovealliance.ui.LoadingLayout

/**
 * The loading, failed or ready state of the license information screen.
 */
internal sealed interface LicenseInformationScreenState {

    /**
     * The content shown for this state.
     */
    val layout: ComposableContent

    /**
     * The dependencies are being read.
     */
    data object Loading : LicenseInformationScreenState {
        override val layout = LoadingLayout()
    }

    /**
     * The dependencies couldn't be read; [layout] offers to try again.
     */
    data class Error(override val layout: GroveErrorLayout) : LicenseInformationScreenState

    /**
     * The dependencies were read and are listed by [layout].
     */
    data class Content(override val layout: LicenseInformationLayout) : LicenseInformationScreenState
}
