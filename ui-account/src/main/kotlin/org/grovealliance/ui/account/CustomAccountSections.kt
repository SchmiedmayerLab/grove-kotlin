//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.account

import org.grovealliance.ui.LazyListContent

/**
 * The app-specific [sections] of the account overview, rendered after the overview's own sections
 * and before the logout button, in the order given. A
 * [GroveSection][org.grovealliance.ui.GroveSection] fits here as it is.
 *
 * The overview places sections apart from each other; spacing between the items of one section is
 * up to the section. Item keys must be unique within the overview.
 */
data class CustomAccountSections(
    val sections: List<LazyListContent>,
) {
    companion object {

        /**
         * No custom sections.
         */
        val None = CustomAccountSections(sections = emptyList())
    }
}
