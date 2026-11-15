//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.grovealliance.account.internal.screen.AccountOverviewAction
import org.grovealliance.account.internal.screen.AccountOverviewEvent
import org.grovealliance.account.internal.screen.AccountOverviewViewModel
import org.grovealliance.core.viewmodel.groveViewModel
import org.grovealliance.ui.ConsumeEvents
import org.grovealliance.ui.GroveScaffold
import org.grovealliance.ui.account.CustomAccountSections

/**
 * Renders the account overview screen for the currently signed-in user.
 *
 * ```kotlin
 * AccountOverviewScreen(
 *     onDismiss = { navController.popBackStack() },
 * )
 * ```
 *
 * @param customSections App-specific sections rendered after the account's own sections, in their
 * given order.
 * @param onDismiss Called when the user dismisses the screen.
 */
@Composable
fun AccountOverviewScreen(
    customSections: CustomAccountSections = CustomAccountSections.None,
    onDismiss: () -> Unit,
) {
    val viewModel = groveViewModel<AccountOverviewViewModel>()
    val screen = viewModel.screen

    ConsumeEvents(eventFlow = viewModel.events) { event ->
        when (event) {
            AccountOverviewEvent.Dismissed -> onDismiss()
        }
    }

    LaunchedEffect(customSections) {
        screen.actionSink.push(action = AccountOverviewAction.SetCustomSections(sections = customSections))
    }

    GroveScaffold(state = screen.scaffoldState) {
        val screenState by screen.state.collectAsStateWithLifecycle()
        screenState.layout.Content(modifier = Modifier.fillMaxSize())
    }
}
