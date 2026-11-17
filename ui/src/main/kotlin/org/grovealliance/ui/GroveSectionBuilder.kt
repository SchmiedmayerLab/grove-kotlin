//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.annotation.StringRes
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Shape

/** Keeps nested section builders from implicitly configuring an outer scope. */
@DslMarker
annotation class GroveSectionDsl

/** Builds sections in display order, for [groveSections] and [groveSectionList]. */
@GroveSectionDsl
class GroveSectionsBuilderScope internal constructor() {
    private val sections = mutableListOf<GroveSection>()

    /** Builds and adds a section identified by [key] within the hosting list. */
    fun section(key: String, scope: GroveSectionBuilderScope.() -> Unit) {
        section(groveSection(key = key, scope = scope))
    }

    /** Adds an existing section. */
    fun section(section: GroveSection) {
        sections.add(section)
    }

    /** Adds existing sections in their given order. */
    fun sections(sections: Iterable<GroveSection>) {
        this.sections.addAll(sections)
    }

    internal fun build(): List<GroveSection> = sections.toList()
}

/** Builds a [GroveSection] with optional text and ordered card or content elements. */
@GroveSectionDsl
class GroveSectionBuilderScope internal constructor(private val key: String) {
    private var title: StringResource? = null
    private var subtitle: StringResource? = null
    private var footer: StringResource? = null
    private val elements = mutableListOf<GroveSection.Element>()

    /** Sets the section heading. */
    fun title(string: String) = title(StringResource(string))

    /** Sets the section heading. */
    fun title(@StringRes resId: Int) = title(StringResource(resId))

    /** Sets the section heading. */
    fun title(stringResource: StringResource) {
        title = stringResource
    }

    /** Sets the text beneath the section heading. */
    fun subtitle(string: String) = subtitle(StringResource(string))

    /** Sets the text beneath the section heading. */
    fun subtitle(@StringRes resId: Int) = subtitle(StringResource(resId))

    /** Sets the text beneath the section heading. */
    fun subtitle(stringResource: StringResource) {
        subtitle = stringResource
    }

    /** Sets the text below the section's elements. */
    fun footer(string: String) = footer(StringResource(string))

    /** Sets the text below the section's elements. */
    fun footer(@StringRes resId: Int) = footer(StringResource(resId))

    /** Sets the text below the section's elements. */
    fun footer(stringResource: StringResource) {
        footer = stringResource
    }

    /** Builds and adds a card identified by [key] within this section. */
    fun card(
        key: String,
        shape: ComposeValue<Shape> = { CardDefaults.shape },
        showDividers: Boolean = true,
        scope: GroveCardBuilderScope.() -> Unit,
    ) {
        element(groveCard(key = key, shape = shape, showDividers = showDividers, scope = scope))
    }

    /** Adds content that supplies its own surface, identified by [key] within this section. */
    fun content(key: String, content: ComposableContent) {
        element(GroveSection.Element.Content(key = key, content = content))
    }

    /** Adds an existing card or content element. */
    fun element(element: GroveSection.Element) {
        elements.add(element)
    }

    /** Adds existing elements in their given order. */
    fun elements(elements: Iterable<GroveSection.Element>) {
        this.elements.addAll(elements)
    }

    internal fun build() = GroveSection(
        key = key,
        title = title,
        subtitle = subtitle,
        elements = elements.toList(),
        footer = footer,
    )
}

/** Builds the rows of one card, in display order. */
@GroveSectionDsl
class GroveCardBuilderScope internal constructor(
    private val key: String,
    private val shape: ComposeValue<Shape>,
    private val showDividers: Boolean,
) {
    private val rows = mutableListOf<CardRow<*>>()

    /** Adds [content] as a row identified by [key] within this card. */
    fun row(key: String, content: ComposableContent) {
        row(CardRow(key = key, content = content))
    }

    /** Adds an existing row. */
    fun row(row: CardRow<*>) {
        rows.add(row)
    }

    /** Adds existing rows in their given order. */
    fun rows(rows: Iterable<CardRow<*>>) {
        this.rows.addAll(rows)
    }

    internal fun build() = GroveSection.Element.Card(
        key = key,
        rows = rows.toList(),
        shape = shape,
        showDividers = showDividers,
    )
}

/**
 * Builds a section. Keys must be unique within their hosting list, section, or card.
 *
 * ```kotlin
 * val section = groveSection(key = "profile") {
 *     title("Profile")
 *     card(key = "details") {
 *         row(key = "name", content = nameRow)
 *         row(key = "email", content = emailRow)
 *     }
 *     footer("Only visible to you.")
 * }
 * ```
 */
fun groveSection(key: String, scope: GroveSectionBuilderScope.() -> Unit): GroveSection =
    GroveSectionBuilderScope(key = key).apply(scope).build()

/** Builds a card element that can be reused in sections through [GroveSectionBuilderScope.element]. */
fun groveCard(
    key: String,
    shape: ComposeValue<Shape> = { CardDefaults.shape },
    showDividers: Boolean = true,
    scope: GroveCardBuilderScope.() -> Unit,
): GroveSection.Element.Card =
    GroveCardBuilderScope(key = key, shape = shape, showDividers = showDividers).apply(scope).build()

/** Builds an ordered list of sections, for layouts that supply their own lazy list. */
fun groveSections(scope: GroveSectionsBuilderScope.() -> Unit): List<GroveSection> =
    GroveSectionsBuilderScope().apply(scope).build()

/**
 * Builds a section list using the same section DSL as [groveSections].
 *
 * ```kotlin
 * val list = groveSectionList {
 *     section(key = "app") {
 *         title("App")
 *         card(key = "info") { row(key = "version", content = versionRow) }
 *     }
 *     section(supportSection)
 * }
 * ```
 */
fun groveSectionList(scope: GroveSectionsBuilderScope.() -> Unit): GroveSectionList =
    GroveSectionList(sections = groveSections(scope))
