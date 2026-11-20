//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.account

import org.grovealliance.ui.LazyListContent

/**
 * The app-specific content of the account overview: the [sections], rendered after the overview's
 * own sections and before the logout button in the order given, and the [footer], rendered after
 * the logout button. A [GroveSection][org.grovealliance.ui.GroveSection] fits either place as it is.
 *
 * The overview places sections and the footer apart from each other; spacing between the items of
 * one section is up to the section. Item keys must be unique within the overview.
 */
data class CustomAccountContent(
    val sections: List<LazyListContent>,
    val footer: LazyListContent?,
) {
    companion object {

        /**
         * No custom content.
         */
        val None = CustomAccountContent(
            sections = emptyList(),
            footer = null,
        )
    }
}
