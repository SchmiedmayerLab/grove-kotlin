//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.ThemePreviews

/**
 * Content that renders itself as items of a lazy list rather than as a single composable.
 *
 * [items] runs in composition, so the content can collect its state there, and returns the items
 * to add to the list that hosts it. Keys of the items must be unique within that list.
 *
 * Example usage:
 *
 * ```kotlin
 * data class Greetings(
 *     private val name: StateFlow<String>,
 * ) : LazyListContent {
 *
 *     @Composable
 *     override fun items(): LazyListScope.() -> Unit {
 *         val name by name.collectAsStateWithLifecycle()
 *         return {
 *             item(key = "hello") { Text(text = "Hello, $name!") }
 *             item(key = "welcome") { Text(text = "Welcome back.") }
 *         }
 *     }
 * }
 *
 * data class GreetingsScreen(
 *     private val greetings: Greetings,
 * ) : ComposableContent {
 *
 *     @Composable
 *     override fun Content(modifier: Modifier) {
 *         // Collected here, because the LazyColumn's builder is not composable.
 *         val greetingItems = greetings.items()
 *         LazyColumn(modifier = modifier) {
 *             item(key = "title") { Text(text = "Greetings") }
 *             greetingItems()
 *         }
 *     }
 * }
 * ```
 */
interface LazyListContent {

    /**
     * The items this content adds to a lazy list, built from its current state.
     */
    @Composable
    fun items(): LazyListScope.() -> Unit
}

/**
 * Previews [content] the way a lazy list hosts it: its items in a [LazyColumn], in the Grove theme.
 */
@Composable
fun LazyListContentPreview(
    content: LazyListContent,
    modifier: Modifier = Modifier,
) {
    GroveTheme {
        val items = content.items()
        LazyColumn(modifier = modifier) {
            items()
        }
    }
}

@ThemePreviews
@Composable
private fun Preview() {
    LazyListContentPreview(
        content = object : LazyListContent {
            @Composable
            override fun items(): LazyListScope.() -> Unit = {
                item(key = "hello") { Text(text = "Hello, Leland!") }
                item(key = "welcome") { Text(text = "Welcome back.") }
            }
        },
    )
}
