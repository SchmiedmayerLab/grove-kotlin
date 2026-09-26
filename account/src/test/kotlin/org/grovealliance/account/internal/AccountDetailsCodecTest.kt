//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account.internal

import com.google.common.truth.Truth.assertThat
import org.grovealliance.account.AccountDetails
import org.grovealliance.account.AccountDetailsCodecConfig
import org.grovealliance.account.AccountKeys
import org.grovealliance.testing.core.testGroveApplication
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AccountDetailsCodecTest {

    private val codec = AccountDetailsCodec()

    @Before
    fun setup() {
        testGroveApplication {
            singleton { AccountDetailsCodecConfig() }
        }
    }

    @Test
    fun `it should leave out a date of birth that was never chosen`() {
        // given
        val details = AccountDetails().apply {
            setAny(AccountKeys.dateOfBirth::class, Instant.MIN)
        }

        // when
        val encoded = codec.encode(accountId = ACCOUNT_ID, details = details)

        // then
        assertThat(encoded.accountKeyValues).doesNotContainKey("dateOfBirth")
    }

    @Test
    fun `it should keep the other details when a date of birth was never chosen`() {
        // given
        val details = AccountDetails().apply {
            setAny(AccountKeys.dateOfBirth::class, Instant.MIN)
            setAny(AccountKeys.email::class, EMAIL)
        }

        // when
        val encoded = codec.encode(accountId = ACCOUNT_ID, details = details)

        // then
        assertThat(encoded.accountKeyValues).containsKey("email")
    }

    @Test
    fun `it should encode a date of birth that was chosen`() {
        // given
        val details = AccountDetails().apply {
            setAny(AccountKeys.dateOfBirth::class, Instant.parse("1990-04-17T00:00:00Z"))
        }

        // when
        val encoded = codec.encode(accountId = ACCOUNT_ID, details = details)

        // then
        assertThat(encoded.accountKeyValues).containsKey("dateOfBirth")
    }

    @Test
    fun `it should keep an empty string, which is a value rather than an absence`() {
        // given
        val details = AccountDetails().apply {
            setAny(AccountKeys.email::class, "")
        }

        // when
        val encoded = codec.encode(accountId = ACCOUNT_ID, details = details)

        // then
        assertThat(encoded.accountKeyValues).containsKey("email")
    }

    @Test
    fun `it should drop a field it cannot encode and keep the rest`() {
        // given
        // Instant.MAX is not the unset sentinel, so it reaches the encoder and fails there.
        val details = AccountDetails().apply {
            setAny(AccountKeys.dateOfBirth::class, Instant.MAX)
            setAny(AccountKeys.email::class, EMAIL)
        }

        // when
        val encoded = codec.encode(accountId = ACCOUNT_ID, details = details)

        // then
        assertThat(encoded.accountKeyValues).containsKey("email")
    }

    @Test
    fun `it should round-trip the details it encoded`() {
        // given
        val details = AccountDetails().apply {
            setAny(AccountKeys.email::class, EMAIL)
        }

        // when
        val decoded = codec.decode(
            stored = codec.encode(accountId = ACCOUNT_ID, details = details),
            keys = setOf(AccountKeys.email),
        )

        // then
        assertThat(decoded.getAnyOrNull(AccountKeys.email::class)).isEqualTo(EMAIL)
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val EMAIL = "participant@example.org"
    }
}
