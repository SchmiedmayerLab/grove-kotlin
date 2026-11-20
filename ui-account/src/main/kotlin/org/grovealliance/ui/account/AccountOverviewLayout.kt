//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.account

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.grovealliance.ui.AsyncTextButton
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.GroveSection
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.VerticalSpacer
import org.grovealliance.ui.groveSection
import org.grovealliance.ui.groveSections
import org.grovealliance.ui.theme.Colors
import org.grovealliance.ui.theme.GroveTheme
import org.grovealliance.ui.theme.Spacings
import org.grovealliance.ui.theme.ThemePreviews

/**
 * Composable content rendering the overview screen as a single lazy list: the [header], the
 * [sections], the custom sections of [customContent] in their given order, the [logout] button and
 * the custom footer, with the same gap between each of them.
 *
 * @param title title of the screen
 * @param header header of the screen
 * @param sections list of sections to be rendered
 * @param customContent app-specific sections rendered between the sections and the logout button,
 * and an app-specific footer rendered after the logout button
 * @param logout button to logout
 */
data class AccountOverviewLayout(
    val title: StringResource,
    val header: AccountProfileHeader,
    val sections: List<GroveSection>,
    val customContent: CustomAccountContent,
    val logout: AsyncTextButton,
) : ComposableContent {
    @Composable
    override fun Content(modifier: Modifier) {
        val sectionItems = (sections + customContent.sections).map { it.items() }
        val footerItems = customContent.footer?.items()
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(Spacings.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = HEADER_KEY) { header.Content() }
            sectionItems.forEachIndexed { index, items ->
                GapBeforeFirstItem(
                    scope = this,
                    gap = { gap(key = "$SECTION_KEY-$index") },
                ).items()
            }
            gap(key = LOGOUT_KEY)
            item(key = LOGOUT_KEY) { logout.Content(modifier = Modifier.fillMaxWidth()) }
            footerItems?.let { items ->
                GapBeforeFirstItem(
                    scope = this,
                    gap = { gap(key = FOOTER_KEY) },
                ).items()
            }
        }
    }

    private fun LazyListScope.gap(key: String) {
        item(key = "$GAP_KEY-$key") { VerticalSpacer(height = Spacings.large) }
    }

    /**
     * Adds items to [scope], preceded by [gap] once the first of them is added, so content adding
     * no items adds no gap either.
     */
    private class GapBeforeFirstItem(
        private val scope: LazyListScope,
        private val gap: LazyListScope.() -> Unit,
    ) : LazyListScope by scope {
        private var hasItems = false

        override fun item(
            key: Any?,
            contentType: Any?,
            content: @Composable LazyItemScope.() -> Unit,
        ) {
            addGapOnce()
            scope.item(
                key = key,
                contentType = contentType,
                content = content,
            )
        }

        override fun items(
            count: Int,
            key: ((index: Int) -> Any)?,
            contentType: (index: Int) -> Any?,
            itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
        ) {
            if (count > 0) addGapOnce()
            scope.items(
                count = count,
                key = key,
                contentType = contentType,
                itemContent = itemContent,
            )
        }

        override fun stickyHeader(
            key: Any?,
            contentType: Any?,
            content: @Composable LazyItemScope.(index: Int) -> Unit,
        ) {
            addGapOnce()
            scope.stickyHeader(
                key = key,
                contentType = contentType,
                content = content,
            )
        }

        private fun addGapOnce() {
            if (hasItems) return
            hasItems = true
            scope.gap()
        }
    }

    private companion object {
        const val HEADER_KEY = "account-overview-header"
        const val SECTION_KEY = "account-overview-section"
        const val LOGOUT_KEY = "account-overview-logout"
        const val FOOTER_KEY = "account-overview-footer"
        const val GAP_KEY = "account-overview-gap"
    }
}

@ThemePreviews
@Composable
private fun Preview() {
    val name = AccountOverviewItem(
        title = StringResource("Name"),
        valueDisplay = StringDataDisplay(),
        value = "John Smith",
        leadingImage = null,
        showArrow = true,
        onClick = {},
    )

    val genderIdentity = AccountOverviewItem(
        title = StringResource("Gender identity"),
        valueDisplay = StringDataDisplay(),
        value = "Prefer not to state",
        leadingImage = null,
        showArrow = false,
        onClick = {},
    )

    val section = groveSection(key = "name") {
        title("Name")
        card(key = "items") {
            row(key = "0", content = name)
            row(key = "1", content = genderIdentity)
        }
    }
    val layout = AccountOverviewLayout(
        title = StringResource("Account overview"),
        header = AccountProfileHeader(
            initials = "LS",
            name = "Leland Stanford",
            description = "lelandstanford@stanford.edu",
        ),
        sections = groveSections { section(section) },
        customContent = CustomAccountContent.None,
        logout = AsyncTextButton(
            title = StringResource("Logout"),
            containerColor = { Colors.error },
            action = {}
        ),
    )

    GroveTheme {
        layout.Content(modifier = Modifier.fillMaxSize())
    }
}
