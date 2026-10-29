//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.sample.app.dashboard

import androidx.compose.runtime.Composable
import org.grovealliance.core.viewmodel.groveViewModel

/**
 * Resolves the dashboard showcase ViewModel for the current navigation entry.
 */
@Composable
fun UiDashboard() {
    val viewModel = groveViewModel<UiDashboardViewModel>()
    viewModel.content.Content()
}
