//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ColorPaletteResourcesTest {

    private val lightColors = colorResources(directory = "values")
    private val darkColors = colorResources(directory = "values-night")

    @Test
    fun `cardinal red light colors match the palette`() {
        // when
        val resources = paletteColors(colors = lightColors, prefix = "cardinal_red")

        // then
        assertThat(resources).isEqualTo(roles(scheme = ColorPalette.CardinalRed.light))
    }

    @Test
    fun `cardinal red dark colors match the palette`() {
        // when
        val resources = paletteColors(colors = darkColors, prefix = "cardinal_red")

        // then
        assertThat(resources).isEqualTo(roles(scheme = ColorPalette.CardinalRed.dark))
    }

    @Test
    fun `accent blue light colors match the palette`() {
        // when
        val resources = paletteColors(colors = lightColors, prefix = "accent_blue")

        // then
        assertThat(resources).isEqualTo(roles(scheme = ColorPalette.AccentBlue.light))
    }

    @Test
    fun `accent blue dark colors match the palette`() {
        // when
        val resources = paletteColors(colors = darkColors, prefix = "accent_blue")

        // then
        assertThat(resources).isEqualTo(roles(scheme = ColorPalette.AccentBlue.dark))
    }

    private fun colorResources(directory: String): Map<String, Int> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/res/$directory/colors.xml"))
        val colors = document.getElementsByTagName("color")
        return (0 until colors.length)
            .map { colors.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent.trim().removePrefix("#").toLong(radix = 16).toInt() }
    }

    private fun paletteColors(colors: Map<String, Int>, prefix: String): Map<String, Int> =
        colors.filterKeys { it.startsWith("${prefix}_") }
            .mapKeys { it.key.removePrefix("${prefix}_") }

    @Suppress("detekt:LongMethod")
    private fun roles(scheme: ColorScheme): Map<String, Int> = mapOf(
        "primary" to scheme.primary,
        "on_primary" to scheme.onPrimary,
        "primary_container" to scheme.primaryContainer,
        "on_primary_container" to scheme.onPrimaryContainer,
        "inverse_primary" to scheme.inversePrimary,
        "secondary" to scheme.secondary,
        "on_secondary" to scheme.onSecondary,
        "secondary_container" to scheme.secondaryContainer,
        "on_secondary_container" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary,
        "on_tertiary" to scheme.onTertiary,
        "tertiary_container" to scheme.tertiaryContainer,
        "on_tertiary_container" to scheme.onTertiaryContainer,
        "error" to scheme.error,
        "on_error" to scheme.onError,
        "error_container" to scheme.errorContainer,
        "on_error_container" to scheme.onErrorContainer,
        "background" to scheme.background,
        "on_background" to scheme.onBackground,
        "surface" to scheme.surface,
        "on_surface" to scheme.onSurface,
        "surface_variant" to scheme.surfaceVariant,
        "on_surface_variant" to scheme.onSurfaceVariant,
        "surface_tint" to scheme.surfaceTint,
        "inverse_surface" to scheme.inverseSurface,
        "inverse_on_surface" to scheme.inverseOnSurface,
        "outline" to scheme.outline,
        "outline_variant" to scheme.outlineVariant,
        "scrim" to scheme.scrim,
        "surface_bright" to scheme.surfaceBright,
        "surface_dim" to scheme.surfaceDim,
        "surface_container_lowest" to scheme.surfaceContainerLowest,
        "surface_container_low" to scheme.surfaceContainerLow,
        "surface_container" to scheme.surfaceContainer,
        "surface_container_high" to scheme.surfaceContainerHigh,
        "surface_container_highest" to scheme.surfaceContainerHighest,
    ).mapValues { it.value.toArgb() }
}
