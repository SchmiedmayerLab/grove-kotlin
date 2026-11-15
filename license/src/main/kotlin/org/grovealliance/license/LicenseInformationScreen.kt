//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import org.grovealliance.core.viewmodel.groveViewModel
import org.grovealliance.license.internal.LicenseInformationEvent
import org.grovealliance.license.internal.LicenseInformationViewModel
import org.grovealliance.ui.ConsumeEvents

/**
 * Lists the app configured via [license] and its dependencies with their licenses; a dependency
 * with a bundled license text opens it on a page of its own.
 *
 * [onDismiss] is called when the participant leaves the list. Links, such as a library's website,
 * open with the platform handler, and nothing happens when no installed app can open them. When
 * [onOpenUrl] is given, it receives every link instead.
 */
@Composable
fun LicenseInformationScreen(
    onDismiss: () -> Unit,
    onOpenUrl: ((String) -> Unit)? = null,
) {
    val viewModel = groveViewModel<LicenseInformationViewModel>()
    val uriHandler = LocalUriHandler.current
    ConsumeEvents(viewModel.events) { event ->
        when (event) {
            LicenseInformationEvent.Dismissed -> onDismiss()
            is LicenseInformationEvent.UrlOpened -> if (onOpenUrl != null) {
                onOpenUrl(event.url)
            } else {
                try {
                    uriHandler.openUri(event.url)
                } catch (_: IllegalArgumentException) {
                    // No installed app can open the link.
                }
            }
        }
    }
    viewModel.screen.Content()
}
