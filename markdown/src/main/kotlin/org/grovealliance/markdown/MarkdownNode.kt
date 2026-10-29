//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.markdown

/**
 * A typed structural element parsed from the raw contents of a [MarkdownBlock.Markdown] block.
 *
 * Produced by [parseNodes] and surfaced via the [MarkdownBlock.Markdown.nodes] extension.
 */
sealed interface MarkdownNode {

    /**
     * An ATX-style (`# Heading`) or setext-style (underlined) heading.
     *
     * [level] is in the range 1–6, matching the number of leading `#` characters or the
     * underline type (`=` → level 1, `-` → level 2).
     */
    data class Heading(val level: Int, val text: String) : MarkdownNode

    /**
     * A single list item introduced by the given [style] marker.
     *
     * [nestingLevel] is derived from leading spaces in groups of four.
     */
    data class ListItem(val style: ListStyle, val text: String, val nestingLevel: Int = 0) : MarkdownNode

    /**
     * A non-empty run of text that is neither a heading nor a list item.
     */
    data class Paragraph(val text: String) : MarkdownNode

    /**
     * A GitHub-flavored table: a header row, a delimiter row setting each column's alignment, and
     * any number of body rows.
     *
     * Every row in [rows] holds exactly one cell per column: missing cells are empty and cells
     * beyond the header's are dropped. Cell texts keep their inline markdown.
     */
    data class Table(
        val columns: List<Column>,
        val rows: List<List<String>>,
    ) : MarkdownNode {

        /**
         * One column, with its [header] cell and the [alignment] of all its cells.
         */
        data class Column(
            val header: String,
            val alignment: Alignment,
        )

        /**
         * How the cells of a column are aligned horizontally.
         */
        enum class Alignment {
            /**
             * Set by `:---` or by a delimiter without colons.
             */
            Leading,

            /**
             * Set by `:---:`.
             */
            Center,

            /**
             * Set by `---:`.
             */
            Trailing,
        }
    }

    /**
     * The marker convention used to introduce a [ListItem].
     */
    enum class ListStyle {
        /**
         * Introduced by `- `.
         */
        Dash,

        /**
         * Introduced by `* `.
         */
        Star,

        /**
         * Introduced by `+ `.
         */
        Plus,

        /**
         * Introduced by a decimal integer followed by `.` or `)` (e.g. `1.`, `2)`).
         */
        Numbered,

        /**
         * Introduced by a single lowercase letter followed by `.` or `)` (e.g. `a.`, `b)`).
         */
        Lettered,
    }
}

/**
 * Parses [text] into an ordered list of [MarkdownNode]s, detecting headings (ATX and setext),
 * unordered bullet lists (`-`, `*`, `+`), numbered and lettered ordered lists, and paragraph text.
 *
 * Blank lines are skipped. Lines that match no recognised pattern are emitted as
 * [MarkdownNode.Paragraph].
 */
fun parseNodes(text: String): List<MarkdownNode> {
    val lines = text.lines()
    val result = mutableListOf<MarkdownNode>()
    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()
        if (trimmed.isEmpty()) {
            index++
            continue
        }
        val (node, advance) = parseNodeAt(lines, index, trimmed)
        result.add(node)
        index += advance
    }
    return result
}

/**
 * The links detected within this node's text content.
 *
 * Delegates to [detectLinks] on the node's text field. Headings, list items, and paragraphs
 * may all contain inline links.
 */
fun MarkdownNode.links(): List<DetectedLink> = detectLinks(
    when (this) {
        is MarkdownNode.Heading -> text
        is MarkdownNode.ListItem -> text
        is MarkdownNode.Paragraph -> text
        is MarkdownNode.Table -> (listOf(columns.map { it.header }) + rows).joinToString(separator = "\n") {
            it.joinToString(separator = " ")
        }
    }
)

// --- Private helpers ---

private const val MAX_HEADING_LEVEL = 6

// ATX heading: 1–6 hashes, optional space + body text, optional trailing hashes.
private val ATX_HEADING_REGEX = Regex("""^(#{1,6})(?:\s+(.*?))?(?:\s+#+)?\s*$""")

// Numbered list: one or more digits followed by `.` or `)` and at least one space.
private val NUMBERED_LIST_REGEX = Regex("""^\d+[.)]\s+(.+)""")

// Lettered list: a single lowercase letter followed by `.` or `)` and at least one space.
private val LETTERED_LIST_REGEX = Regex("""^[a-z][.)]\s+(.+)""")

/**
 * Returns the node for [trimmed] at position [index] in [lines], together with how many lines to advance.
 */
private fun parseNodeAt(lines: List<String>, index: Int, trimmed: String): Pair<MarkdownNode, Int> =
    tryParseTable(lines, index)
        // List items are resolved before the setext look-ahead so that lines starting with `- ` are
        // never misread as paragraph text preceding a setext underline.
        ?: (tryParseListItem(lines[index]) ?: tryParseAtxHeading(trimmed))?.let { Pair(it, 1) }
        ?: tryParseSetextHeading(lines, index, trimmed)?.let { Pair(it, 2) }
        ?: Pair(MarkdownNode.Paragraph(trimmed), 1)

private fun tryParseAtxHeading(trimmed: String): MarkdownNode.Heading? {
    val match = ATX_HEADING_REGEX.matchEntire(trimmed) ?: return null
    val level = match.groupValues[1].length.coerceIn(1, MAX_HEADING_LEVEL)
    val headingText = match.groupValues[2].trimEnd('#').trim()
    return MarkdownNode.Heading(level, headingText)
}

private fun tryParseSetextHeading(lines: List<String>, index: Int, trimmed: String): MarkdownNode.Heading? {
    if (index + 1 >= lines.size) return null
    val next = lines[index + 1].trim()
    return when {
        next.isEmpty() -> null
        next.all { it == '=' } -> MarkdownNode.Heading(1, trimmed)
        next.all { it == '-' } -> MarkdownNode.Heading(2, trimmed)
        else -> null
    }
}

private fun tryParseListItem(line: String): MarkdownNode.ListItem? {
    val nestingLevel = line.takeWhile { it == ' ' }.length / LIST_INDENT_SPACES
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("- ") ->
            MarkdownNode.ListItem(MarkdownNode.ListStyle.Dash, trimmed.removePrefix("- ").trim(), nestingLevel)
        trimmed.startsWith("* ") ->
            MarkdownNode.ListItem(MarkdownNode.ListStyle.Star, trimmed.removePrefix("* ").trim(), nestingLevel)
        trimmed.startsWith("+ ") ->
            MarkdownNode.ListItem(MarkdownNode.ListStyle.Plus, trimmed.removePrefix("+ ").trim(), nestingLevel)
        else -> {
            val numbered = NUMBERED_LIST_REGEX.matchEntire(trimmed)
            if (numbered != null) {
                MarkdownNode.ListItem(MarkdownNode.ListStyle.Numbered, numbered.groupValues[1].trim(), nestingLevel)
            } else {
                val lettered = LETTERED_LIST_REGEX.matchEntire(trimmed)
                lettered?.let {
                    MarkdownNode.ListItem(MarkdownNode.ListStyle.Lettered, it.groupValues[1].trim(), nestingLevel)
                }
            }
        }
    }
}

/**
 * The table starting at [index] with its line count, when that line is a header row followed by a
 * delimiter row with as many cells. The body ends at the first blank line, heading or list item.
 */
private fun tryParseTable(lines: List<String>, index: Int): Pair<MarkdownNode.Table, Int>? {
    val headerLine = lines[index]
    if (index + 1 >= lines.size || '|' !in headerLine) return null
    val headers = tableCells(headerLine)
    val alignments = tableCells(lines[index + 1]).map(::tableAlignment)
    if (alignments.size != headers.size || alignments.any { it == null }) return null
    val columns = headers.zip(alignments) { header, alignment ->
        MarkdownNode.Table.Column(
            header = header,
            alignment = alignment ?: MarkdownNode.Table.Alignment.Leading,
        )
    }
    val rows = lines.drop(index + 2)
        .takeWhile { line -> line.isNotBlank() && tryParseListItem(line) == null && tryParseAtxHeading(line.trim()) == null }
        .map { line -> tableCells(line).take(columns.size).let { it + List(columns.size - it.size) { "" } } }
    return Pair(
        MarkdownNode.Table(
            columns = columns,
            rows = rows,
        ),
        2 + rows.size,
    )
}

/**
 * The cells of a table row: split at unescaped pipes, without the optional outer pipes, each
 * trimmed and with escaped pipes restored.
 */
private fun tableCells(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith('|')) row = row.drop(1)
    if (row.endsWith('|') && !row.endsWith("\\|")) row = row.dropLast(1)
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < row.length) {
        val char = row[index]
        when {
            char == '\\' && row.getOrNull(index + 1) == '|' -> {
                cell.append('|')
                index++
            }
            char == '|' -> {
                cells.add(cell.toString().trim())
                cell.clear()
            }
            else -> cell.append(char)
        }
        index++
    }
    cells.add(cell.toString().trim())
    return cells
}

/**
 * The alignment a delimiter cell such as `:---:` sets, or `null` when [cell] is no delimiter.
 */
private fun tableAlignment(cell: String): MarkdownNode.Table.Alignment? {
    if (!TABLE_DELIMITER_REGEX.matches(cell)) return null
    return when {
        cell.startsWith(':') && cell.endsWith(':') -> MarkdownNode.Table.Alignment.Center
        cell.endsWith(':') -> MarkdownNode.Table.Alignment.Trailing
        else -> MarkdownNode.Table.Alignment.Leading
    }
}

private val TABLE_DELIMITER_REGEX = Regex(":?-+:?")

private const val LIST_INDENT_SPACES = 4
