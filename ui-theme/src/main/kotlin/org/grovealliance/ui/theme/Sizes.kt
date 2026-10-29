//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object Sizes {
    object Icon {
        val extraSmall: Dp
            @Composable
            @ReadOnlyComposable
            get() = 16.dp

        val small: Dp
            @Composable
            @ReadOnlyComposable
            get() = 24.dp

        val medium: Dp
            @Composable
            @ReadOnlyComposable
            get() = 48.dp

        val large: Dp
            @Composable
            @ReadOnlyComposable
            get() = 64.dp

        val extraLarge: Dp
            @Composable
            @ReadOnlyComposable
            get() = 96.dp
    }

    object Border {
        val small: Dp
            @Composable
            @ReadOnlyComposable
            get() = 1.dp

        val medium: Dp
            @Composable
            @ReadOnlyComposable
            get() = 2.dp
    }

    object Content {
        val extraLarge: Dp
            @Composable
            @ReadOnlyComposable
            get() = 128.dp

        val large: Dp
            @Composable
            @ReadOnlyComposable
            get() = 64.dp

        val small: Dp
            @Composable
            @ReadOnlyComposable
            get() = 20.dp
    }
}

/**
 * A size of the theme, named rather than resolved, so it can be stored, compared and passed
 * around outside composition and resolved with [resolve] where it is laid out.
 *
 * The tokens are grouped like [Sizes], each standing for the property of the same name. [Custom]
 * passes a fixed size through unchanged.
 */
sealed interface SizeToken {

    /**
     * The sizes of [Sizes.Icon].
     */
    sealed interface Icon : SizeToken {

        /**
         * Resolves to [Sizes.Icon.extraSmall].
         */
        data object ExtraSmall : Icon

        /**
         * Resolves to [Sizes.Icon.small].
         */
        data object Small : Icon

        /**
         * Resolves to [Sizes.Icon.medium].
         */
        data object Medium : Icon

        /**
         * Resolves to [Sizes.Icon.large].
         */
        data object Large : Icon

        /**
         * Resolves to [Sizes.Icon.extraLarge].
         */
        data object ExtraLarge : Icon
    }

    /**
     * The sizes of [Sizes.Border].
     */
    sealed interface Border : SizeToken {

        /**
         * Resolves to [Sizes.Border.small].
         */
        data object Small : Border

        /**
         * Resolves to [Sizes.Border.medium].
         */
        data object Medium : Border
    }

    /**
     * The sizes of [Sizes.Content].
     */
    sealed interface Content : SizeToken {

        /**
         * Resolves to [Sizes.Content.extraLarge].
         */
        data object ExtraLarge : Content

        /**
         * Resolves to [Sizes.Content.large].
         */
        data object Large : Content

        /**
         * Resolves to [Sizes.Content.small].
         */
        data object Small : Content
    }

    /**
     * A fixed size, resolved to itself whatever the theme.
     *
     * @param size the size itself
     */
    data class Custom(val size: Dp) : SizeToken
}

/**
 * The size this token stands for in the current theme.
 */
@Composable
@ReadOnlyComposable
fun SizeToken.resolve(): Dp = when (this) {
    SizeToken.Icon.ExtraSmall -> Sizes.Icon.extraSmall
    SizeToken.Icon.Small -> Sizes.Icon.small
    SizeToken.Icon.Medium -> Sizes.Icon.medium
    SizeToken.Icon.Large -> Sizes.Icon.large
    SizeToken.Icon.ExtraLarge -> Sizes.Icon.extraLarge
    SizeToken.Border.Small -> Sizes.Border.small
    SizeToken.Border.Medium -> Sizes.Border.medium
    SizeToken.Content.ExtraLarge -> Sizes.Content.extraLarge
    SizeToken.Content.Large -> Sizes.Content.large
    SizeToken.Content.Small -> Sizes.Content.small
    is SizeToken.Custom -> size
}
