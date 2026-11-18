//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.grovealliance.ui.test.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `resolves a literal`() {
        // when
        val resource = StringResource("Hello")

        // then
        assertThat(resource.get(context)).isEqualTo("Hello")
    }

    @Test
    fun `quantity selects the singular form`() {
        // when
        val resource = StringResource.quantity(R.plurals.test_task_count, 1)

        // then
        assertThat(resource.get(context)).isEqualTo("1 task")
    }

    @Test
    fun `quantity selects the plural form`() {
        // when
        val resource = StringResource.quantity(R.plurals.test_task_count, 5)

        // then
        assertThat(resource.get(context)).isEqualTo("5 tasks")
    }

    @Test
    fun `quantity supplies the count as the only argument by default`() {
        // when
        val resource = StringResource.quantity(R.plurals.test_task_count, 12)

        // then
        assertThat(resource.get(context)).isEqualTo("12 tasks")
    }

    @Test
    fun `quantity uses explicit arguments when given`() {
        // when
        val resource = StringResource.quantity(R.plurals.test_named_count, 3, "Ada")

        // then
        assertThat(resource.get(context)).isEqualTo("Ada has many")
    }

    @Test
    fun `composes two resources`() {
        // when
        val resource = StringResource("a") + StringResource("b")

        // then
        assertThat(resource.get(context)).isEqualTo("ab")
    }

    @Test
    fun `resolves a resource implemented outside the module`() {
        // when
        val resource = RepeatedStringResource(text = StringResource("ab"))

        // then
        assertThat(resource.get(context)).isEqualTo("abab")
    }

    @Test
    fun `composes a resource implemented outside the module`() {
        // when
        val resource = StringResource("x") + RepeatedStringResource(text = StringResource("ab"))

        // then
        assertThat(resource.get(context)).isEqualTo("xabab")
    }

    /**
     * A [StringResource] implemented in the test, resolving to [text] twice.
     */
    private data class RepeatedStringResource(private val text: StringResource) : StringResource {
        override fun get(context: Context): String = text.get(context).repeat(2)
    }
}
