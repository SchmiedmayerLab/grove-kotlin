//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.sample.app.dashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grovealliance.sample.app.NavigationEvent
import org.grovealliance.sample.app.Navigator
import org.grovealliance.sample.app.R
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.dashboard.DashboardSection
import org.grovealliance.ui.groveAppBar
import org.grovealliance.ui.mutableScaffoldState

/**
 * Owns deterministic showcase data, the selected sleep range and tap feedback.
 */
class UiDashboardViewModel(
    private val navigator: Navigator,
) : ViewModel() {
    private val chartSamples = DashboardChartSamples()
    private val elementSamples = DashboardElementSamples()
    private val scaffoldState = mutableScaffoldState(
        appBar = groveAppBar {
            title(R.string.ui_dashboard_title)
            back { navigator.navigateTo(NavigationEvent.PopBackStack) }
        },
    )
    private val sleepPicker = MutableStateFlow(chartSamples.sleepPicker(selectedIndex = 1, onSelected = ::selectSleepRange))
    private val sleepChart = MutableStateFlow(chartSamples.sleepChart(selectedIndex = 1))

    val content = UiDashboardScreen(
        scaffoldState = scaffoldState.asScaffoldState(),
        sections = listOf(
            DashboardSection(
                title = StringResource("Interactive charts"),
                content = chartSamples.sleepCard(picker = sleepPicker.asStateFlow(), chart = sleepChart.asStateFlow()),
                footnote = StringResource("Demo data · October 2026. Touch and drag to inspect values, or change the time range."),
            ),
        ) + chartSamples.sections() + elementSamples.sections(onClick = ::showFeedback),
    )

    private fun selectSleepRange(index: Int) {
        sleepPicker.value = chartSamples.sleepPicker(selectedIndex = index, onSelected = ::selectSleepRange)
        sleepChart.value = chartSamples.sleepChart(selectedIndex = index)
    }

    private fun showFeedback(message: StringResource) {
        scaffoldState.showToast(message = message)
    }
}
