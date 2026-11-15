//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.ThemePreviews

/**
 * A row of a card built from lazy-list items: its [content], identified by [key] within the card.
 */
data class CardRow<out T : ComposableContent>(
    val key: String,
    val content: T,
)

/**
 * Adds [rows] to the list as one item each, each a [GroveCard] segment, so together they read as
 * a single card of [shape]: the first row carries its top corners and the last its bottom corners.
 * An inset divider separates consecutive rows when [showDividers] is set.
 *
 * Item keys combine [key] with each row's key, so [key] must be unique within the list.
 */
fun LazyListScope.cardSection(
    key: String,
    rows: List<CardRow<*>>,
    shape: ComposeValue<Shape> = { CardDefaults.shape },
    showDividers: Boolean = true,
) {
    cardRows(
        key = key,
        rows = rows,
        shape = shape,
        showDividers = showDividers,
        isSpacedFromPrevious = false,
    )
}

/**
 * Adds [rows] as the segments of one card, as [cardSection] does, set apart from the item before
 * them when [isSpacedFromPrevious].
 */
internal fun LazyListScope.cardRows(
    key: String,
    rows: List<CardRow<*>>,
    shape: ComposeValue<Shape>,
    showDividers: Boolean,
    isSpacedFromPrevious: Boolean,
) {
    rows.forEachIndexed { index, row ->
        item(key = "$key-${row.key}") {
            GroveCard(
                modifier = Modifier
                    .animateItem()
                    .padding(top = if (isSpacedFromPrevious && index == 0) Spacings.medium else 0.dp)
                    .fillMaxWidth(),
                shape = cardSegmentShape(
                    shape = shape(),
                    index = index,
                    count = rows.size,
                ),
            ) {
                if (showDividers && index > 0) {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacings.medium),
                    )
                }
                row.content.Content(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun cardSegmentShape(
    shape: Shape,
    index: Int,
    count: Int,
): Shape =
    remember(shape, index, count) {
        val square = CornerSize(0.dp)
        when {
            count == 1 || shape !is CornerBasedShape -> shape
            index == 0 -> shape.copy(bottomStart = square, bottomEnd = square)
            index == count - 1 -> shape.copy(topStart = square, topEnd = square)
            else -> RectangleShape
        }
    }

private class CardSectionPreviewProvider : PreviewParameterProvider<GroveSection> {
    override val values = sequenceOf(
        groveSection(key = "preview") {
            card(key = "items") {
                listOf("Name", "Email", "Password").forEach { label ->
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
        },
    )
}

@ThemePreviews
@Composable
private fun Preview(
    @PreviewParameter(CardSectionPreviewProvider::class) content: GroveSection,
) {
    LazyListContentPreview(
        content = content,
        modifier = Modifier.padding(Spacings.medium),
    )
}
