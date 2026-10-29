//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.markdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Sizes
import org.grovealliance.ui.theme.TextStyles
import org.grovealliance.ui.theme.ThemePreviews
import org.grovealliance.ui.theme.semiBold

/**
 * Renders a [MarkdownNode.Table] as a grid of bordered cells.
 *
 * The header row is set in semibold. Each column is as wide as its widest cell; when the columns
 * do not fit the available width, they shrink towards their longest word and their text wraps.
 * Cells keep the alignment of their column and the inline emphasis and links of their text.
 */
@Composable
fun MarkdownTableView(
    table: MarkdownNode.Table,
    modifier: Modifier = Modifier,
    linkClickStrategy: LinkClickStrategy = LocalLinkClickStrategy.current,
) {
    val borderColor = Colors.outline
    val borderWidth = Sizes.Border.small
    val textStyle = TextStyles.bodyMedium
    val padding = with(LocalDensity.current) {
        CellPadding(
            horizontal = (textStyle.fontSize * HORIZONTAL_PADDING_EM).toDp(),
            vertical = (textStyle.fontSize * VERTICAL_PADDING_EM).toDp(),
        )
    }
    val rows = listOf(table.columns.map { it.header }) + table.rows
    Layout(
        modifier = modifier.drawBehind {
            drawLine(borderColor, Offset.Zero, Offset(size.width, 0f), borderWidth.toPx())
            drawLine(borderColor, Offset.Zero, Offset(0f, size.height), borderWidth.toPx())
        },
        content = {
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { columnIndex, text ->
                    Box(
                        modifier = Modifier
                            .drawBehind { drawCellEdges(color = borderColor, width = borderWidth) }
                            .padding(horizontal = padding.horizontal, vertical = padding.vertical),
                    ) {
                        Text(
                            text = rememberMarkdownAnnotatedString(text = text, linkClickStrategy = linkClickStrategy),
                            style = if (rowIndex == 0) textStyle.semiBold() else textStyle,
                            textAlign = table.columns[columnIndex].alignment.textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val columnCount = table.columns.size
        val cells = measurables.chunked(columnCount)
        val widths = columnWidths(
            cells = cells,
            columnCount = columnCount,
            available = constraints.maxWidth,
        )
        val heights = cells.map { row -> row.mapIndexed { column, cell -> cell.minIntrinsicHeight(widths[column]) }.max() }
        val placeables = cells.mapIndexed { row, rowCells ->
            rowCells.mapIndexed { column, cell -> cell.measure(Constraints.fixed(widths[column], heights[row])) }
        }
        layout(width = widths.sum(), height = heights.sum()) {
            var top = 0
            placeables.forEachIndexed { row, rowPlaceables ->
                var left = 0
                rowPlaceables.forEachIndexed { column, placeable ->
                    placeable.place(left, top)
                    left += widths[column]
                }
                top += heights[row]
            }
        }
    }
}

/**
 * The width of each column: its widest cell when all columns fit [available]. Otherwise a column
 * whose widest cell fits an equal share of the width left keeps that width, and the remaining width
 * is shared among the wider columns in proportion to their widest cell, never below their longest word.
 */
private fun columnWidths(
    cells: List<List<Measurable>>,
    columnCount: Int,
    available: Int,
): List<Int> {
    val preferred = List(columnCount) { column -> cells.maxOf { it[column].maxIntrinsicWidth(Constraints.Infinity) } }
    if (available == Constraints.Infinity || preferred.sum() <= available) return preferred
    val minimum = List(columnCount) { column -> cells.maxOf { it[column].minIntrinsicWidth(Constraints.Infinity) } }
    val widths = MutableList(columnCount) { 0 }
    var remaining = available
    var open = (0 until columnCount).toList()
    while (open.isNotEmpty()) {
        val share = remaining / open.size
        val fitting = open.filter { preferred[it] <= share }
        if (fitting.isEmpty()) break
        fitting.forEach { widths[it] = preferred[it] }
        remaining -= fitting.sumOf { preferred[it] }
        open = open - fitting.toSet()
    }
    val openPreferred = open.sumOf { preferred[it] }.coerceAtLeast(1)
    open.forEach { column ->
        widths[column] = maxOf(minimum[column], (preferred[column].toLong() * remaining / openPreferred).toInt())
    }
    return widths
}

/**
 * Draws the right and bottom edges of a cell; the table draws its own top and left edges, so that
 * every line is drawn once.
 */
private fun DrawScope.drawCellEdges(color: Color, width: Dp) {
    val stroke = width.toPx()
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), stroke)
}

private val MarkdownNode.Table.Alignment.textAlign: TextAlign
    get() = when (this) {
        MarkdownNode.Table.Alignment.Leading -> TextAlign.Start
        MarkdownNode.Table.Alignment.Center -> TextAlign.Center
        MarkdownNode.Table.Alignment.Trailing -> TextAlign.End
    }

private data class CellPadding(
    val horizontal: Dp,
    val vertical: Dp,
)

/**
 * The horizontal padding of a cell, relative to the font size of its text.
 */
private const val HORIZONTAL_PADDING_EM = 0.72f

/**
 * The vertical padding of a cell, relative to the font size of its text.
 */
private const val VERTICAL_PADDING_EM = 0.35f

@ThemePreviews
@Composable
private fun MarkdownTableViewPreview() {
    GroveTheme {
        MarkdownTableView(
            table = parseNodes(
                """
                | Category | Systolic (mmHg) | Diastolic (mmHg) |
                | :------- | :-------------: | ---------------: |
                | Normal   | <120            | <80              |
                | Elevated | 120–129         | <80              |
                """.trimIndent()
            ).single() as MarkdownNode.Table,
        )
    }
}
