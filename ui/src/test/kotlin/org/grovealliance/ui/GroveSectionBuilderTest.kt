//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.google.common.truth.Truth.assertThat
import org.grovealliance.resources.Strings
import org.junit.Test

class GroveSectionBuilderTest {
    private val content = object : ComposableContent {
        @Composable
        override fun Content(modifier: Modifier) = Unit
    }

    @Test
    fun `mixes nested and existing sections elements and rows in display order`() {
        // given
        val existingRow = CardRow(key = "existing", content = content)
        val existingCard = groveCard(key = "existing") { row(existingRow) }
        val existingSection = groveSection(key = "existing") { element(existingCard) }

        // when
        val list = groveSectionList {
            section(key = "built") {
                title("Profile")
                card(key = "built") {
                    row(key = "built", content = content)
                    row(existingRow)
                    rows(listOf(existingRow.copy(key = "last")))
                }
                content(key = "note", content = content)
                elements(listOf(existingCard))
                footer("Only visible to you.")
            }
            section(existingSection)
            sections(groveSections { section(key = "last") {} })
        }

        // then
        assertThat(list.sections.map { it.key }).containsExactly("built", "existing", "last").inOrder()
        assertThat(list.sections[1]).isSameInstanceAs(existingSection)
        val section = list.sections.first()
        assertThat(section.title).isEqualTo(StringResource("Profile"))
        assertThat(section.footer).isEqualTo(StringResource("Only visible to you."))
        assertThat(section.elements.map { it.key }).containsExactly("built", "note", "existing").inOrder()
        val card = section.elements.first() as GroveSection.Element.Card
        assertThat(card.rows.map { it.key }).containsExactly("built", "existing", "last").inOrder()
        assertThat(card.rows[1]).isSameInstanceAs(existingRow)
        assertThat(card.rows.first().content).isSameInstanceAs(content)
        assertThat((section.elements[1] as GroveSection.Element.Content).content).isSameInstanceAs(content)
    }

    @Test
    fun `preserves resource text and card configuration without evaluating composable values`() {
        // given
        val shape: ComposeValue<Shape> = { error("The builder must not evaluate the shape") }

        // when
        val section = groveSection(key = "app") {
            title("Replaced title")
            title(Strings.account)
            footer(Strings.viewstate_default_error_message)
            footer(StringResource("Replaced footer"))
            card(key = "details", shape = shape, showDividers = false) {
                row(key = "details", content = content)
            }
        }

        // then
        assertThat(section.title).isEqualTo(StringResource(Strings.account))
        assertThat(section.footer).isEqualTo(StringResource("Replaced footer"))
        val card = section.elements.single() as GroveSection.Element.Card
        assertThat(card.shape).isSameInstanceAs(shape)
        assertThat(card.showDividers).isFalse()
    }

    @Test
    fun `built collections are independent of later builder mutations`() {
        // given
        lateinit var sectionsScope: GroveSectionsBuilderScope
        lateinit var sectionScope: GroveSectionBuilderScope
        lateinit var cardScope: GroveCardBuilderScope
        val list = groveSectionList {
            sectionsScope = this
            section(key = "profile") {
                sectionScope = this
                card(key = "details") {
                    cardScope = this
                    row(key = "name", content = content)
                }
            }
        }

        // when
        sectionsScope.section(key = "later") {}
        sectionScope.content(key = "later", content = content)
        cardScope.row(key = "later", content = content)

        // then
        val section = list.sections.single()
        val card = section.elements.single() as GroveSection.Element.Card
        assertThat(section.key).isEqualTo("profile")
        assertThat(card.rows.single().key).isEqualTo("name")
        assertThat(card.showDividers).isTrue()
        assertThat(section.title).isNull()
        assertThat(section.footer).isNull()
    }
}
