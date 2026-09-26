//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class AccountKeyTest {

    @Test
    fun `it should report an instant key holding its empty sentinel as unrepresentable`() {
        // when
        val result = AccountKeys.dateOfBirth.holdsUnrepresentableInstant(value = Instant.MIN)

        // then
        assertThat(result).isTrue()
    }

    @Test
    fun `it should report a chosen instant as representable`() {
        // given
        val chosen = Instant.parse("1990-04-17T00:00:00Z")

        // when
        val result = AccountKeys.dateOfBirth.holdsUnrepresentableInstant(value = chosen)

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `it should not report a non-instant empty sentinel as unrepresentable`() {
        // when
        val result = AccountKeys.email.holdsUnrepresentableInstant(value = "")

        // then
        assertThat(result).isFalse()
    }

    @Test
    fun `it should not report a default initial value as unrepresentable`() {
        // when
        val result = AccountKeys.genderIdentity.holdsUnrepresentableInstant(
            value = GenderIdentity.PREFER_NOT_TO_STATE,
        )

        // then
        assertThat(result).isFalse()
    }
}
