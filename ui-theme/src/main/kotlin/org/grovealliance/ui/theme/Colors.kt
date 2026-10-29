//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import org.grovealliance.ui.theme.Colors.background
import org.grovealliance.ui.theme.Colors.error
import org.grovealliance.ui.theme.Colors.errorContainer
import org.grovealliance.ui.theme.Colors.inverseSurface
import org.grovealliance.ui.theme.Colors.outline
import org.grovealliance.ui.theme.Colors.primary
import org.grovealliance.ui.theme.Colors.primaryContainer
import org.grovealliance.ui.theme.Colors.secondary
import org.grovealliance.ui.theme.Colors.secondaryContainer
import org.grovealliance.ui.theme.Colors.surface
import org.grovealliance.ui.theme.Colors.surfaceContainer
import org.grovealliance.ui.theme.Colors.surfaceVariant
import org.grovealliance.ui.theme.Colors.tertiary
import org.grovealliance.ui.theme.Colors.tertiaryContainer
import org.grovealliance.ui.theme.Colors.transparent

/**
 * A facade over [MaterialTheme.colorScheme] that exposes every [androidx.compose.material3.ColorScheme]
 * color as a composable, read-only property, plus a convenience [transparent] constant.
 *
 * All properties are annotated with [@ReadOnlyComposable][ReadOnlyComposable] so they can be read
 * in snapshot-safe contexts without triggering unnecessary recompositions.
 */
object Colors {
    private val scheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    /**
     * The primary color of the theme. Used for prominent UI elements such as FABs and filled buttons.
     */
    val primary
        @Composable
        @ReadOnlyComposable
        get() = scheme.primary

    /**
     * Color used for content (icons, text) drawn on top of [primary].
     */
    val onPrimary
        @Composable
        @ReadOnlyComposable
        get() = scheme.onPrimary

    /**
     * A tonal variant of [primary], typically used for container backgrounds.
     */
    val primaryContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.primaryContainer

    /**
     * Color used for content drawn on top of [primaryContainer].
     */
    val onPrimaryContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.onPrimaryContainer

    /**
     * An accent color used on dark surfaces to provide contrast against [primary].
     */
    val inversePrimary
        @Composable
        @ReadOnlyComposable
        get() = scheme.inversePrimary

    /**
     * The secondary color of the theme. Used for less prominent UI components.
     */
    val secondary
        @Composable
        @ReadOnlyComposable
        get() = scheme.secondary

    /**
     * Color used for content drawn on top of [secondary].
     */
    val onSecondary
        @Composable
        @ReadOnlyComposable
        get() = scheme.onSecondary

    /**
     * A tonal variant of [secondary], typically used for container backgrounds.
     */
    val secondaryContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.secondaryContainer

    /**
     * Color used for content drawn on top of [secondaryContainer].
     */
    val onSecondaryContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.onSecondaryContainer

    /**
     * The tertiary color of the theme. Used for contrasting accents to balance primary and secondary.
     */
    val tertiary
        @Composable
        @ReadOnlyComposable
        get() = scheme.tertiary

    /**
     * Color used for content drawn on top of [tertiary].
     */
    val onTertiary
        @Composable
        @ReadOnlyComposable
        get() = scheme.onTertiary

    /**
     * A tonal variant of [tertiary], typically used for container backgrounds.
     */
    val tertiaryContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.tertiaryContainer

    /**
     * Color used for content drawn on top of [tertiaryContainer].
     */
    val onTertiaryContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.onTertiaryContainer

    /**
     * The background color, shown behind scrollable content.
     */
    val background
        @Composable
        @ReadOnlyComposable
        get() = scheme.background

    /**
     * Color used for content drawn on top of [background].
     */
    val onBackground
        @Composable
        @ReadOnlyComposable
        get() = scheme.onBackground

    /**
     * The surface color, used for component backgrounds such as cards and sheets.
     */
    val surface
        @Composable
        @ReadOnlyComposable
        get() = scheme.surface

    /**
     * Color used for content drawn on top of [surface].
     */
    val onSurface
        @Composable
        @ReadOnlyComposable
        get() = scheme.onSurface

    /**
     * A tonal variant of [surface], used for differentiated surface regions.
     */
    val surfaceVariant
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceVariant

    /**
     * Color used for content drawn on top of [surfaceVariant].
     */
    val onSurfaceVariant
        @Composable
        @ReadOnlyComposable
        get() = scheme.onSurfaceVariant

    /**
     * Color used to tint surface containers and other surfaces. Typically matches [primary].
     */
    val surfaceTint
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceTint

    /**
     * A surface color used on dark surfaces to provide contrast, opposite of [surface].
     */
    val inverseSurface
        @Composable
        @ReadOnlyComposable
        get() = scheme.inverseSurface

    /**
     * Color used for content drawn on top of [inverseSurface].
     */
    val inverseOnSurface
        @Composable
        @ReadOnlyComposable
        get() = scheme.inverseOnSurface

    /**
     * The error color, used to indicate errors or destructive actions.
     */
    val error
        @Composable
        @ReadOnlyComposable
        get() = scheme.error

    /**
     * Color used for content drawn on top of [error].
     */
    val onError
        @Composable
        @ReadOnlyComposable
        get() = scheme.onError

    /**
     * A tonal variant of [error], used for error container backgrounds.
     */
    val errorContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.errorContainer

    /**
     * Color used for content drawn on top of [errorContainer].
     */
    val onErrorContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.onErrorContainer

    /**
     * The outline color, used for borders and decorative separators.
     */
    val outline
        @Composable
        @ReadOnlyComposable
        get() = scheme.outline

    /**
     * A lower-emphasis variant of [outline], used for decorative elements that require less contrast.
     */
    val outlineVariant
        @Composable
        @ReadOnlyComposable
        get() = scheme.outlineVariant

    /**
     * The scrim color, used to obscure content behind modal surfaces such as bottom sheets.
     */
    val scrim
        @Composable
        @ReadOnlyComposable
        get() = scheme.scrim

    /**
     * A bright variant of [surface], used to highlight elevated or focused surface areas.
     */
    val surfaceBright
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceBright

    /**
     * A dimmed variant of [surface], used for recessed or inactive surface areas.
     */
    val surfaceDim
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceDim

    /**
     * A surface container color at the default elevation level.
     */
    val surfaceContainer
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceContainer

    /**
     * A surface container color at a higher elevation level than [surfaceContainer].
     */
    val surfaceContainerHigh
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceContainerHigh

    /**
     * A surface container color at the highest elevation level.
     */
    val surfaceContainerHighest
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceContainerHighest

    /**
     * A surface container color at a lower elevation level than [surfaceContainer].
     */
    val surfaceContainerLow
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceContainerLow

    /**
     * A surface container color at the lowest elevation level.
     */
    val surfaceContainerLowest
        @Composable
        @ReadOnlyComposable
        get() = scheme.surfaceContainerLowest

    /**
     * Fully transparent color. Convenience constant for cases where no color fill is desired.
     */
    val transparent
        @Composable
        @ReadOnlyComposable
        get() = Color.Transparent
}

/**
 * A color of the theme, named rather than resolved, so it can be stored, compared and passed
 * around outside composition and resolved with [resolve] where it is drawn.
 *
 * Each token stands for the [Colors] property of the same name and follows the theme, including
 * light and dark mode, wherever it is resolved. [Custom] stands for a color outside the theme, with
 * an optional variant for dark mode.
 */
sealed interface ColorToken {

    /**
     * Resolves to [Colors.primary].
     */
    data object Primary : ColorToken

    /**
     * Resolves to [Colors.onPrimary].
     */
    data object OnPrimary : ColorToken

    /**
     * Resolves to [Colors.primaryContainer].
     */
    data object PrimaryContainer : ColorToken

    /**
     * Resolves to [Colors.onPrimaryContainer].
     */
    data object OnPrimaryContainer : ColorToken

    /**
     * Resolves to [Colors.inversePrimary].
     */
    data object InversePrimary : ColorToken

    /**
     * Resolves to [Colors.secondary].
     */
    data object Secondary : ColorToken

    /**
     * Resolves to [Colors.onSecondary].
     */
    data object OnSecondary : ColorToken

    /**
     * Resolves to [Colors.secondaryContainer].
     */
    data object SecondaryContainer : ColorToken

    /**
     * Resolves to [Colors.onSecondaryContainer].
     */
    data object OnSecondaryContainer : ColorToken

    /**
     * Resolves to [Colors.tertiary].
     */
    data object Tertiary : ColorToken

    /**
     * Resolves to [Colors.onTertiary].
     */
    data object OnTertiary : ColorToken

    /**
     * Resolves to [Colors.tertiaryContainer].
     */
    data object TertiaryContainer : ColorToken

    /**
     * Resolves to [Colors.onTertiaryContainer].
     */
    data object OnTertiaryContainer : ColorToken

    /**
     * Resolves to [Colors.background].
     */
    data object Background : ColorToken

    /**
     * Resolves to [Colors.onBackground].
     */
    data object OnBackground : ColorToken

    /**
     * Resolves to [Colors.surface].
     */
    data object Surface : ColorToken

    /**
     * Resolves to [Colors.onSurface].
     */
    data object OnSurface : ColorToken

    /**
     * Resolves to [Colors.surfaceVariant].
     */
    data object SurfaceVariant : ColorToken

    /**
     * Resolves to [Colors.onSurfaceVariant].
     */
    data object OnSurfaceVariant : ColorToken

    /**
     * Resolves to [Colors.surfaceTint].
     */
    data object SurfaceTint : ColorToken

    /**
     * Resolves to [Colors.inverseSurface].
     */
    data object InverseSurface : ColorToken

    /**
     * Resolves to [Colors.inverseOnSurface].
     */
    data object InverseOnSurface : ColorToken

    /**
     * Resolves to [Colors.error].
     */
    data object Error : ColorToken

    /**
     * Resolves to [Colors.onError].
     */
    data object OnError : ColorToken

    /**
     * Resolves to [Colors.errorContainer].
     */
    data object ErrorContainer : ColorToken

    /**
     * Resolves to [Colors.onErrorContainer].
     */
    data object OnErrorContainer : ColorToken

    /**
     * Resolves to [Colors.outline].
     */
    data object Outline : ColorToken

    /**
     * Resolves to [Colors.outlineVariant].
     */
    data object OutlineVariant : ColorToken

    /**
     * Resolves to [Colors.scrim].
     */
    data object Scrim : ColorToken

    /**
     * Resolves to [Colors.surfaceBright].
     */
    data object SurfaceBright : ColorToken

    /**
     * Resolves to [Colors.surfaceDim].
     */
    data object SurfaceDim : ColorToken

    /**
     * Resolves to [Colors.surfaceContainer].
     */
    data object SurfaceContainer : ColorToken

    /**
     * Resolves to [Colors.surfaceContainerHigh].
     */
    data object SurfaceContainerHigh : ColorToken

    /**
     * Resolves to [Colors.surfaceContainerHighest].
     */
    data object SurfaceContainerHighest : ColorToken

    /**
     * Resolves to [Colors.surfaceContainerLow].
     */
    data object SurfaceContainerLow : ColorToken

    /**
     * Resolves to [Colors.surfaceContainerLowest].
     */
    data object SurfaceContainerLowest : ColorToken

    /**
     * Resolves to [Colors.transparent].
     */
    data object Transparent : ColorToken

    /**
     * A color outside the theme, resolved to [light] in light mode and to [dark] in dark mode.
     *
     * @param light the color in light mode
     * @param dark the color in dark mode; the same as [light] when omitted
     */
    data class Custom(
        val light: Color,
        val dark: Color = light,
    ) : ColorToken
}

/**
 * The color this token stands for in the current theme.
 */
// A flat, exhaustive lookup: the compiler flags any token left unmapped, so the branch count is not
// complexity worth splitting up.
@Suppress("CyclomaticComplexMethod")
@Composable
@ReadOnlyComposable
fun ColorToken.resolve(): Color = when (this) {
    ColorToken.Primary -> Colors.primary
    ColorToken.OnPrimary -> Colors.onPrimary
    ColorToken.PrimaryContainer -> Colors.primaryContainer
    ColorToken.OnPrimaryContainer -> Colors.onPrimaryContainer
    ColorToken.InversePrimary -> Colors.inversePrimary
    ColorToken.Secondary -> Colors.secondary
    ColorToken.OnSecondary -> Colors.onSecondary
    ColorToken.SecondaryContainer -> Colors.secondaryContainer
    ColorToken.OnSecondaryContainer -> Colors.onSecondaryContainer
    ColorToken.Tertiary -> Colors.tertiary
    ColorToken.OnTertiary -> Colors.onTertiary
    ColorToken.TertiaryContainer -> Colors.tertiaryContainer
    ColorToken.OnTertiaryContainer -> Colors.onTertiaryContainer
    ColorToken.Background -> Colors.background
    ColorToken.OnBackground -> Colors.onBackground
    ColorToken.Surface -> Colors.surface
    ColorToken.OnSurface -> Colors.onSurface
    ColorToken.SurfaceVariant -> Colors.surfaceVariant
    ColorToken.OnSurfaceVariant -> Colors.onSurfaceVariant
    ColorToken.SurfaceTint -> Colors.surfaceTint
    ColorToken.InverseSurface -> Colors.inverseSurface
    ColorToken.InverseOnSurface -> Colors.inverseOnSurface
    ColorToken.Error -> Colors.error
    ColorToken.OnError -> Colors.onError
    ColorToken.ErrorContainer -> Colors.errorContainer
    ColorToken.OnErrorContainer -> Colors.onErrorContainer
    ColorToken.Outline -> Colors.outline
    ColorToken.OutlineVariant -> Colors.outlineVariant
    ColorToken.Scrim -> Colors.scrim
    ColorToken.SurfaceBright -> Colors.surfaceBright
    ColorToken.SurfaceDim -> Colors.surfaceDim
    ColorToken.SurfaceContainer -> Colors.surfaceContainer
    ColorToken.SurfaceContainerHigh -> Colors.surfaceContainerHigh
    ColorToken.SurfaceContainerHighest -> Colors.surfaceContainerHighest
    ColorToken.SurfaceContainerLow -> Colors.surfaceContainerLow
    ColorToken.SurfaceContainerLowest -> Colors.surfaceContainerLowest
    ColorToken.Transparent -> Colors.transparent
    is ColorToken.Custom -> if (isSystemInDarkTheme()) dark else light
}

/**
 * A color palette containing both light and dark [ColorScheme]s. Used to provide a complete set of
 * colors for both themes when dynamic color is not enabled.
 */
data class ColorPalette(
    val light: ColorScheme,
    val dark: ColorScheme,
) {
    companion object {
        val CardinalRed = ColorPalette(
            light = CardinalRedLightColors,
            dark = CardinalRedDarkColors,
        )

        val AccentBlue = ColorPalette(
            light = AccentBlueLightColorScheme,
            dark = AccentBlueDarkColorScheme,
        )
    }
}
