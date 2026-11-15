//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.account

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.grovealliance.testing.screenshot.ScreenshotTest
import org.grovealliance.ui.AsyncTextButton
import org.grovealliance.ui.ImageResource
import org.grovealliance.ui.LazyListContent
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveSection
import org.grovealliance.ui.groveSections
import org.grovealliance.ui.theme.Colors
import org.junit.Test
import java.time.Instant

class AccountOverviewLayoutScreenshotTest : ScreenshotTest() {

    @Test
    fun `AccountOverviewLayout screenshot`() {
        // when / then
        screenshotLayout(leadingCustomSections = emptyList())
    }

    @Test
    fun `AccountOverviewLayout with an empty custom section screenshot`() {
        // given
        val empty = object : LazyListContent {
            @Composable
            override fun items(): LazyListScope.() -> Unit = {}
        }

        // when / then
        screenshotLayout(leadingCustomSections = listOf(empty))
    }

    @Suppress("LongMethod")
    private fun screenshotLayout(leadingCustomSections: List<LazyListContent>) {
        val initialSection = groveSection(key = "account") {
            card(key = "items") {
                row(
                    key = "0",
                    content = AccountOverviewItem(
                        title = StringResource("Name, E-Mail Address"),
                        valueDisplay = StringDataDisplay(),
                        value = "",
                        leadingImage = ImageResource(Icons.Default.AccountBox),
                        showArrow = true,
                        onClick = {},
                    ),
                )
                row(
                    key = "1",
                    content = AccountOverviewItem(
                        title = StringResource("Sign-In & Security"),
                        valueDisplay = StringDataDisplay(),
                        value = "",
                        leadingImage = ImageResource(Icons.Default.Security),
                        showArrow = true,
                        onClick = {},
                    ),
                )
            }
        }

        val personalDetailsSection = groveSection(key = "personal-details") {
            title("Personal Details")
            card(key = "items") {
                row(
                    key = "0",
                    content = AccountOverviewItem(
                        title = StringResource("Gender identity"),
                        valueDisplay = StringDataDisplay(),
                        value = "Prefer not to state",
                        leadingImage = null,
                        showArrow = false,
                        onClick = {},
                    ),
                )
                row(
                    key = "1",
                    content = AccountOverviewItem(
                        title = StringResource("Date of Birth"),
                        valueDisplay = ValueTextDisplay<Instant> {
                            StringResource("Mar 9, 1824")
                        },
                        value = Instant.now(),
                        leadingImage = null,
                        showArrow = false,
                        onClick = {},
                    ),
                )
            }
        }

        val licenses = groveSection(key = "licenses") {
            title("Licenses")
            card(key = "items") {
                row(
                    key = "0",
                    content = AccountOverviewItem(
                        title = StringResource("License information"),
                        valueDisplay = StringDataDisplay(),
                        value = "",
                        leadingImage = null,
                        showArrow = true,
                        onClick = {},
                    ),
                )
            }
        }

        val layout = AccountOverviewLayout(
            title = StringResource("Account overview"),
            header = AccountProfileHeader(
                initials = "LS",
                name = "Leland Stanford",
                description = "lelandstanford@stanford.edu",
            ),
            sections = groveSections {
                section(initialSection)
                section(personalDetailsSection)
            },
            customSections = CustomAccountSections(
                sections = leadingCustomSections + licenses,
            ),
            logout = AsyncTextButton(
                title = StringResource("Logout"),
                containerColor = { Colors.error },
                action = {}
            ),
        )
        screenshot {
            layout.Content(modifier = Modifier.fillMaxSize())
        }
    }
}
