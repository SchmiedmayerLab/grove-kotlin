//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.account

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.GroveSection
import org.grovealliance.ui.ImageResource
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveSection
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Sizes
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.ThemePreviews

/**
 * Layout displaying an icon and a single section of tappable account overview rows.
 *
 * @param icon Icon shown at the top of the layout.
 * @param section Rows to display.
 */
data class AccountSectionOverviewLayout(
    val icon: ImageResource,
    val section: GroveSection,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        val sectionItems = section.items()
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(Spacings.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = ICON_KEY) {
                icon.Content(
                    modifier = Modifier
                        .padding(bottom = Spacings.medium)
                        .size(Sizes.Icon.medium),
                )
            }
            sectionItems()
        }
    }

    private companion object {
        const val ICON_KEY = "account-section-overview-icon"
    }
}

@ThemePreviews
@Composable
private fun Preview() {
    val nameRow = AccountOverviewItem(
        title = StringResource("Full Name"),
        valueDisplay = StringDataDisplay(),
        value = "Leland Stanford",
        leadingImage = null,
        showArrow = true,
        onClick = {},
    )
    val emailRow = AccountOverviewItem(
        title = StringResource("Email Address"),
        valueDisplay = StringDataDisplay(),
        value = "lelandstanford@stanford.edu",
        leadingImage = null,
        showArrow = true,
        onClick = {},
    )
    val layout = AccountSectionOverviewLayout(
        section = groveSection(key = "name") {
            card(key = "items") {
                row(key = "0", content = nameRow)
                row(key = "1", content = emailRow)
            }
        },
        icon = ImageResource(Icons.Default.AccountCircle),
    )
    GroveTheme {
        layout.Content(modifier = Modifier.fillMaxSize())
    }
}
