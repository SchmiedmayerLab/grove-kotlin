//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.questionnaire

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import androidx.appcompat.R as AppCompatR
import org.grovealliance.ui.theme.R as ThemeR

class QuestionnaireThemeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun `takes its colors from the cardinal red host theme`() {
        // when
        val questionnaire = questionnaireContext(hostTheme = ThemeR.style.Theme_Grove_CardinalRed)

        // then
        assertThat(questionnaire.color(attribute = AppCompatR.attr.colorPrimary))
            .isEqualTo(context.getColor(ThemeR.color.cardinal_red_primary))
        assertThat(questionnaire.color(attribute = android.R.attr.colorBackground))
            .isEqualTo(context.getColor(ThemeR.color.cardinal_red_background))
    }

    @Test
    fun `takes its colors from the accent blue host theme`() {
        // when
        val questionnaire = questionnaireContext(hostTheme = ThemeR.style.Theme_Grove_AccentBlue)

        // then
        assertThat(questionnaire.color(attribute = AppCompatR.attr.colorPrimary))
            .isEqualTo(context.getColor(ThemeR.color.accent_blue_primary))
        assertThat(questionnaire.color(attribute = android.R.attr.colorBackground))
            .isEqualTo(context.getColor(ThemeR.color.accent_blue_background))
    }

    private fun questionnaireContext(@StyleRes hostTheme: Int): Context = ContextThemeWrapper(
        ContextThemeWrapper(context, hostTheme),
        R.style.Theme_Grove_Questionnaire,
    )

    private fun Context.color(@AttrRes attribute: Int): Int {
        val attributes = obtainStyledAttributes(intArrayOf(attribute))
        return try {
            attributes.getColor(0, 0)
        } finally {
            attributes.recycle()
        }
    }
}
