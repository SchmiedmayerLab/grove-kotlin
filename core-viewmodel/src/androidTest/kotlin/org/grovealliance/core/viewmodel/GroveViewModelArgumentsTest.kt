//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.core.viewmodel

import android.os.Bundle
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroveViewModelArgumentsTest {
    @Test
    fun argumentsOverlayDefaultsAndPreserveOtherCreationExtras() {
        // given
        val ownerKey = object : CreationExtras.Key<Any> {}
        val owner = Any()
        val defaults = Bundle().apply { putString("unchanged", "default"); putString("id", "old") }
        val original = MutableCreationExtras().apply {
            set(ownerKey, owner)
            set(DEFAULT_ARGS_KEY, defaults)
        }

        // when
        val extras = original.withArguments(args = Bundle().apply { putString("id", "new") })

        // then
        assertThat(extras[ownerKey]).isSameInstanceAs(owner)
        assertThat(extras[DEFAULT_ARGS_KEY]?.getString("unchanged")).isEqualTo("default")
        assertThat(extras[DEFAULT_ARGS_KEY]?.getString("id")).isEqualTo("new")
        assertThat(defaults.getString("id")).isEqualTo("old")
    }

    @Test
    fun argumentBundlesAreCopiedAndEmptyExtrasAreSupported() {
        // given
        val arguments = Bundle().apply { putString("id", "initial") }

        // when
        val extras = CreationExtras.Empty.withArguments(args = arguments)
        arguments.putString("id", "changed")

        // then
        assertThat(extras[DEFAULT_ARGS_KEY]?.getString("id")).isEqualTo("initial")
    }
}
