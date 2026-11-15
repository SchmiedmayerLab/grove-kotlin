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
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import org.grovealliance.testing.screenshot.ScreenshotTest
import org.grovealliance.ui.theme.GroveShapes
import org.grovealliance.ui.theme.Spacings
import org.junit.Test

class CardRowScreenshotTest : ScreenshotTest() {

    @Test
    fun `cardSection single row screenshot`() {
        // when / then
        screenshot(section = section(labels = listOf("Name")))
    }

    @Test
    fun `cardSection two rows screenshot`() {
        // when / then
        screenshot(section = section(labels = listOf("Name", "Email")))
    }

    @Test
    fun `cardSection three rows screenshot`() {
        // when / then
        screenshot(section = section(labels = listOf("Name", "Email", "Password")))
    }

    @Test
    fun `cardSection without dividers screenshot`() {
        // given
        val section = section(
            labels = listOf("Name", "Email", "Password"),
            showDividers = false,
        )

        // when / then
        screenshot(section = section)
    }

    @Test
    fun `cardSection with custom shape screenshot`() {
        // given
        val section = section(
            labels = listOf("Name", "Email", "Password"),
            shape = { GroveShapes.small },
        )

        // when / then
        screenshot(section = section)
    }

    private fun screenshot(section: LazyListContent) {
        screenshot {
            LazyListContentPreview(
                content = section,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacings.medium),
            )
        }
    }

    private fun section(
        labels: List<String>,
        shape: ComposeValue<Shape> = { CardDefaults.shape },
        showDividers: Boolean = true,
    ): LazyListContent {
        val rows = labels.map { label ->
            CardRow(
                key = label,
                content = ItemDisplayRow(
                    leadingImage = null,
                    label = StringResource(label),
                    value = null,
                    showArrow = true,
                ),
            )
        }
        return object : LazyListContent {
            @Composable
            override fun items(): LazyListScope.() -> Unit = {
                cardSection(
                    key = "section",
                    rows = rows,
                    shape = shape,
                    showDividers = showDividers,
                )
            }
        }
    }
}
