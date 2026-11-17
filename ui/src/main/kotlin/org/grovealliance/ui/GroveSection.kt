//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.ThemePreviews
import org.grovealliance.ui.theme.medium

/**
 * A section of a lazy list: an optional [title] with an optional [subtitle] beneath it, the
 * [elements] below them and an optional [footer] below those, each added to the hosting list as
 * items of their own so long sections stay lazy.
 *
 * The section spaces its own parts, so the rows of an [Element.Card] stay joined as one card as
 * long as the hosting list does not space its items itself, as `Arrangement.spacedBy` would.
 * [GroveSectionList] hosts sections and sets them apart from each other.
 *
 * Item keys combine [key] with the keys of the elements and their rows, so [key] must be unique
 * within the list and element keys unique within the section.
 */
data class GroveSection(
    val key: String,
    val title: StringResource? = null,
    val subtitle: StringResource? = null,
    val elements: List<Element>,
    val footer: StringResource? = null,
) : LazyListContent {

    /**
     * Whether the section adds no items to a list.
     */
    val isEmpty: Boolean
        get() = title == null && subtitle == null && footer == null && elements.all { it is Element.Card && it.rows.isEmpty() }

    @Composable
    override fun items(): LazyListScope.() -> Unit = {
        if (title != null || subtitle != null) {
            item(key = "$key-title") {
                SectionHeader(
                    title = title,
                    subtitle = subtitle,
                )
            }
        }
        elements.forEachIndexed { index, element ->
            when (element) {
                is Element.Card -> cardRows(
                    key = "$key-${element.key}",
                    rows = element.rows,
                    shape = element.shape,
                    showDividers = element.showDividers,
                    isSpacedFromPrevious = index > 0,
                )
                is Element.Content -> item(key = "$key-${element.key}") {
                    element.content.Content(
                        modifier = Modifier
                            .animateItem()
                            .padding(top = if (index > 0) Spacings.medium else 0.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
        footer?.let { footer ->
            item(key = "$key-footer") { SectionFooter(footer = footer) }
        }
    }

    /**
     * A part of a [GroveSection], identified by [key] within it.
     */
    sealed interface Element {
        val key: String

        /**
         * [rows] joined into one card of [shape], each row an item of its own, separated by inset
         * dividers when [showDividers] is set.
         */
        data class Card(
            override val key: String,
            val rows: List<CardRow<*>>,
            val shape: ComposeValue<Shape> = { CardDefaults.shape },
            val showDividers: Boolean = true,
        ) : Element

        /**
         * [content] shown as it draws itself, for components bringing their own surface.
         */
        data class Content(
            override val key: String,
            val content: ComposableContent,
        ) : Element
    }
}

@Composable
private fun SectionHeader(
    title: StringResource?,
    subtitle: StringResource?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacings.medium,
                end = Spacings.medium,
                bottom = Spacings.small,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacings.tiny),
    ) {
        title?.let {
            Text(
                modifier = Modifier.semantics { heading() },
                text = it.text(),
                style = TextStyles.titleSmall.medium(),
                color = Colors.onSurfaceVariant,
            )
        }
        subtitle?.let {
            Text(
                text = it.text(),
                style = TextStyles.bodySmall,
                color = Colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionFooter(footer: StringResource) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacings.medium,
                end = Spacings.medium,
                top = Spacings.small,
            ),
        text = footer.text(),
        style = TextStyles.bodySmall,
        color = Colors.onSurfaceVariant,
    )
}

private class GroveSectionPreviewProvider : PreviewParameterProvider<GroveSection> {
    private val base = groveSection(key = "preview") {
        title("Personal Details")
        subtitle("How we reach you.")
        card(key = "details") {
            listOf("Name", "Email").forEach { label ->
                row(
                    key = label,
                    content = ItemDisplayRow(
                        leadingImage = null,
                        label = StringResource(label),
                        value = null,
                        showArrow = true,
                    ),
                )
            }
        }
        footer("Visible only to you.")
    }
    override val values = sequenceOf(
        base,
        base.copy(title = null, subtitle = null, footer = null),
    )
}

@ThemePreviews
@Composable
private fun Preview(@PreviewParameter(GroveSectionPreviewProvider::class) section: GroveSection) {
    LazyListContentPreview(
        content = section,
        modifier = Modifier.padding(Spacings.medium),
    )
}
