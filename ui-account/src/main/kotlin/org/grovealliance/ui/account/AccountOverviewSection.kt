//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grovealliance.ui.LazyListContentPreview
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveSection
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.ThemePreviews

/**
 * A section of the account overview: the [items] in one card under an optional [title],
 * identified by [key] within the overview.
 */
@Suppress("FunctionName")
fun AccountOverviewSection(
    key: String,
    title: StringResource?,
    items: List<AnyAccountOverviewItem>,
) = groveSection(key = key) {
    title?.let { title(it) }
    card(key = ITEMS_KEY) {
        items.forEachIndexed { index, item ->
            row(key = "$index", content = item)
        }
    }
}

private const val ITEMS_KEY = "items"

@ThemePreviews
@Composable
private fun Preview() {
    val name = AccountOverviewItem(
        title = StringResource("Name"),
        valueDisplay = StringDataDisplay(),
        value = "John Smith",
        leadingImage = null,
        showArrow = true,
        onClick = {},
    )

    val genderIdentity = AccountOverviewItem(
        title = StringResource("Gender identity"),
        valueDisplay = StringDataDisplay(),
        value = "Prefer not to state",
        leadingImage = null,
        showArrow = false,
        onClick = {},
    )

    LazyListContentPreview(
        content = groveSection(key = "name") {
            title("Name")
            card(key = "items") {
                row(key = "0", content = name)
                row(key = "1", content = genderIdentity)
            }
        },
        modifier = Modifier.padding(Spacings.medium),
    )
}
