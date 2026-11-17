//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import org.grovealliance.testing.screenshot.ScreenshotTest
import org.grovealliance.ui.theme.Spacings
import org.junit.Test

class GroveSectionScreenshotTest : ScreenshotTest() {
    private val details = groveCard(key = "details") {
        listOf("Name", "Email", "Phone").forEach { label ->
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
    private val base = groveSection(key = "personal") {
        title("Personal Details")
        element(details)
        footer("Only visible to you.")
    }

    @Test
    fun `GroveSection with title and footer`() {
        // when / then
        screenshot(section = base)
    }

    @Test
    fun `GroveSection with subtitle`() {
        // given
        val section = base.copy(subtitle = StringResource("How we reach you."))

        // when / then
        screenshot(section = section)
    }

    @Test
    fun `GroveSection with subtitle only`() {
        // given
        val section = base.copy(
            title = null,
            subtitle = StringResource("How we reach you."),
            footer = null,
        )

        // when / then
        screenshot(section = section)
    }

    @Test
    fun `GroveSection without title and footer`() {
        // given
        val section = base.copy(
            title = null,
            footer = null,
        )

        // when / then
        screenshot(section = section)
    }

    @Test
    fun `GroveSection with several elements`() {
        // given
        val section = groveSection(key = "personal") {
            title("Personal Details")
            element(details)
            card(key = "version") {
                row(
                    key = "version",
                    content = ItemDisplayRow(
                        leadingImage = null,
                        label = StringResource("Version"),
                        value = StringResource("1.0.0"),
                        showArrow = false,
                    ),
                )
            }
            content(
                key = "note",
                content = LoadingLayout(
                    message = StringResource("Loading..."),
                    style = LoadingLayoutStyle.Normal,
                ),
            )
            footer("Only visible to you.")
        }

        // when / then
        screenshot(section = section)
    }

    @Test
    fun `GroveSectionList`() {
        // given
        val list = groveSectionList {
            section(base)
            section(key = "empty") {}
            section(
                base.copy(
                    key = "contact",
                    title = StringResource("Contact"),
                    footer = null,
                ),
            )
        }

        // when / then
        screenshot { list.Content(modifier = Modifier.fillMaxSize()) }
    }

    private fun screenshot(section: GroveSection) {
        screenshot {
            LazyListContentPreview(
                content = section,
                modifier = Modifier.padding(Spacings.medium),
            )
        }
    }
}
