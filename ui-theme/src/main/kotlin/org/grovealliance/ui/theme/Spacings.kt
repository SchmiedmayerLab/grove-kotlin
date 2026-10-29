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

object Spacings {
    val zero: Dp
        @Composable
        @ReadOnlyComposable
        get() = 0.dp

    val tiny: Dp
        @Composable
        @ReadOnlyComposable
        get() = 2.dp

    val extraSmall: Dp
        @Composable
        @ReadOnlyComposable
        get() = 4.dp

    val small: Dp
        @Composable
        @ReadOnlyComposable
        get() = 8.dp

    val medium: Dp
        @Composable
        @ReadOnlyComposable
        get() = 16.dp

    val large: Dp
        @Composable
        @ReadOnlyComposable
        get() = 24.dp
}

/**
 * A spacing of the theme, named rather than resolved, so it can be stored, compared and passed
 * around outside composition and resolved with [resolve] where it is laid out.
 *
 * Each token stands for the [Spacings] property of the same name. [Custom] passes a fixed spacing
 * through unchanged.
 */
sealed interface SpacingToken {

    /**
     * Resolves to [Spacings.zero].
     */
    data object Zero : SpacingToken

    /**
     * Resolves to [Spacings.tiny].
     */
    data object Tiny : SpacingToken

    /**
     * Resolves to [Spacings.extraSmall].
     */
    data object ExtraSmall : SpacingToken

    /**
     * Resolves to [Spacings.small].
     */
    data object Small : SpacingToken

    /**
     * Resolves to [Spacings.medium].
     */
    data object Medium : SpacingToken

    /**
     * Resolves to [Spacings.large].
     */
    data object Large : SpacingToken

    /**
     * A fixed spacing, resolved to itself whatever the theme.
     *
     * @param spacing the spacing itself
     */
    data class Custom(val spacing: Dp) : SpacingToken
}

/**
 * The spacing this token stands for in the current theme.
 */
@Composable
@ReadOnlyComposable
fun SpacingToken.resolve(): Dp = when (this) {
    SpacingToken.Zero -> Spacings.zero
    SpacingToken.Tiny -> Spacings.tiny
    SpacingToken.ExtraSmall -> Spacings.extraSmall
    SpacingToken.Small -> Spacings.small
    SpacingToken.Medium -> Spacings.medium
    SpacingToken.Large -> Spacings.large
    is SpacingToken.Custom -> spacing
}
