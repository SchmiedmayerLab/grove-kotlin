//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.ThemePreviews

/**
 * The [sections] in one lazy list, in their given order, set apart from each other. Sections
 * adding no items take no space.
 */
data class GroveSectionList(
    val sections: List<GroveSection>,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        val shown = sections.filterNot { it.isEmpty }
        val sectionItems = shown.map { it.items() }
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(Spacings.medium),
        ) {
            sectionItems.forEachIndexed { index, items ->
                if (index > 0) {
                    item(key = "${shown[index].key}-gap") { VerticalSpacer(height = Spacings.large) }
                }
                items()
            }
        }
    }
}

private class GroveSectionListPreviewProvider : PreviewParameterProvider<GroveSectionList> {
    private val row = CardRow(
        key = "row",
        content = ItemDisplayRow(
            leadingImage = null,
            label = StringResource("Version"),
            value = StringResource("1.0.0"),
            showArrow = false,
        ),
    )
    override val values = sequenceOf(
        groveSectionList {
            section(key = "app") {
                title("App")
                card(key = "info") { row(row) }
            }
            section(key = "support") {
                title("Support")
                card(key = "info") { row(row) }
                footer("Reach out any time.")
            }
        },
    )
}

@ThemePreviews
@Composable
private fun Preview(@PreviewParameter(GroveSectionListPreviewProvider::class) list: GroveSectionList) {
    GroveTheme { list.Content() }
}
