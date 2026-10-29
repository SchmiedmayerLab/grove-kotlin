//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.sample.app.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.GroveScaffold
import org.grovealliance.ui.GroveScaffoldState
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.dashboard.DashboardSection
import org.grovealliance.ui.groveAppBar
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.ThemePreviews

/**
 * A scrollable showcase of dashboard charts, scores, statistics and achievements.
 *
 * @param scaffoldState the toolbar, navigation and tap feedback
 * @param sections prepared examples, in display order
 */
@Immutable
data class UiDashboardScreen(
    val scaffoldState: GroveScaffoldState,
    val sections: List<DashboardSection>,
) : ComposableContent {
    @Composable
    override fun Content(modifier: Modifier) {
        Box(modifier = modifier.fillMaxSize()) {
            GroveScaffold(state = scaffoldState) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(SECTIONS_TAG),
                    contentPadding = PaddingValues(Spacings.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacings.large),
                ) {
                    itemsIndexed(items = sections, key = { index, _ -> index }) { _, section ->
                        section.Content()
                    }
                }
            }
        }
    }

    private companion object {
        /**
         * The scroll container used by the showcase's device tests.
         */
        const val SECTIONS_TAG = "ui-dashboard-sections"
    }
}

private class UiDashboardPreviewProvider : PreviewParameterProvider<UiDashboardScreen> {
    private val samples = DashboardChartSamples()
    private val base = UiDashboardScreen(
        scaffoldState = GroveScaffoldState(appBar = groveAppBar { title("UI Dashboard") }),
        sections = listOf(
            DashboardSection(
                title = StringResource("Interactive charts"),
                content = samples.sleepCard(
                    picker = MutableStateFlow(samples.sleepPicker(selectedIndex = 1, onSelected = {})),
                    chart = MutableStateFlow(samples.sleepChart(selectedIndex = 1)),
                ),
                footnote = StringResource("Touch and drag to inspect sample data."),
            ),
        ) + samples.sections() + DashboardElementSamples().sections(onClick = {}),
    )
    override val values: Sequence<UiDashboardScreen> = sequenceOf(base)
}

@ThemePreviews
@Composable
private fun Preview(@PreviewParameter(UiDashboardPreviewProvider::class) content: UiDashboardScreen) {
    GroveTheme { content.Content() }
}
