//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.grovealliance.ui.AsyncTextButton
import org.grovealliance.ui.CardRow
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.GroveErrorLayout
import org.grovealliance.ui.GroveScaffold
import org.grovealliance.ui.GroveScaffoldState
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveAppBar
import org.grovealliance.ui.groveSectionList
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.ThemePreviews

/**
 * The license information screen: the layout of its current [state] beneath its app bar.
 */
internal data class LicenseInformationScreenContent(
    val scaffoldState: GroveScaffoldState,
    val state: StateFlow<LicenseInformationScreenState>,
) : ComposableContent {

    @Composable
    override fun Content(modifier: Modifier) {
        val state by state.collectAsStateWithLifecycle()
        GroveScaffold(state = scaffoldState) {
            state.layout.Content(modifier = modifier.fillMaxSize())
        }
    }
}

private class LicenseInformationScreenContentPreviewProvider : PreviewParameterProvider<LicenseInformationScreenContent> {
    private val row = CardRow(
        key = "org.example:library",
        content = LicenseRow(
            name = StringResource("Example Library"),
            license = StringResource("Apache-2.0"),
            version = StringResource("Version: 1.2.3"),
            onClick = {},
        ),
    )
    private val base = LicenseInformationScreenContent(
        scaffoldState = GroveScaffoldState(
            appBar = groveAppBar {
                back {}
                title(StringResource("License Information"))
            },
        ),
        state = MutableStateFlow(
            LicenseInformationScreenState.Content(
                layout = LicenseInformationLayout(
                    list = groveSectionList {
                        section(key = "project") {
                            card(key = "project") {
                                row(
                                    row.copy(
                                        key = "project",
                                        content = row.content.copy(
                                            name = StringResource("Example App"),
                                            license = StringResource("MIT"),
                                        ),
                                    ),
                                )
                            }
                        }
                        section(key = "dependencies") {
                            title("Dependencies used by Example App")
                            card(key = "dependencies") { row(row) }
                            footer("Please refer to the individual repository links for packages without license labels.")
                        }
                    },
                    detail = MutableStateFlow(null),
                    onBack = {},
                ),
            ),
        ),
    )
    override val values = sequenceOf(
        base,
        base.copy(state = MutableStateFlow(LicenseInformationScreenState.Loading)),
        base.copy(
            state = MutableStateFlow(
                LicenseInformationScreenState.Error(
                    layout = GroveErrorLayout(
                        title = StringResource("Licenses Unavailable"),
                        message = StringResource("The license information couldn't be loaded. Please try again."),
                        primaryButton = AsyncTextButton(
                            title = StringResource("Try Again"),
                            action = {},
                        ),
                    ),
                ),
            ),
        ),
    )
}

@ThemePreviews
@Composable
private fun Preview(
    @PreviewParameter(LicenseInformationScreenContentPreviewProvider::class) content: LicenseInformationScreenContent,
) {
    GroveTheme { content.Content() }
}
