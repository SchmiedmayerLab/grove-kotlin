//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health

import org.grovealliance.core.GroveDsl
import org.grovealliance.health.internal.PrivacyConfig
import org.grovealliance.ui.ComposableBlock
import org.grovealliance.ui.ComposableContent
import org.grovealliance.ui.StringResource

/**
 * Scope for configuring the screen Health Connect opens to explain how the app uses health data.
 *
 * Without any configuration, a default explanation is shown. Each call replaces the previous one.
 */
@GroveDsl
class PrivacyConfigurationScope {
    internal var config: PrivacyConfig = PrivacyConfig.Default

    /**
     * Shows [title] and [description] on the screen.
     */
    fun explanationText(title: StringResource, description: StringResource) {
        config = PrivacyConfig.Text(title = title, description = description)
    }

    /**
     * Renders [block] as the whole screen.
     */
    fun composable(block: ComposableBlock) {
        config = PrivacyConfig.Composable(block)
    }

    /**
     * Renders [content] as the whole screen.
     */
    fun content(content: ComposableContent) {
        config = PrivacyConfig.Content(content)
    }
}
