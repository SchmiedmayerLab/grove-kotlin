//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account.firebase.internal

import com.google.common.truth.Truth.assertThat
import com.google.firebase.Timestamp
import org.grovealliance.account.AccountDetails
import org.grovealliance.account.AccountDetailsCodecConfig
import org.grovealliance.account.AccountKeys
import org.grovealliance.testing.core.testGroveApplication
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FirestoreAccountDetailsCodecTest {

    private val codec = FirestoreAccountDetailsCodec()

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
        val encoded = codec.encode(details = details)

        // then
        assertThat(encoded).doesNotContainKey("dateOfBirth")
    }

    @Test
    fun `it should keep the other details when a date of birth was never chosen`() {
        // given
        val details = AccountDetails().apply {
            setAny(AccountKeys.dateOfBirth::class, Instant.MIN)
            setAny(AccountKeys.email::class, EMAIL)
        }

        // when
        val encoded = codec.encode(details = details)

        // then
        assertThat(encoded["email"]).isEqualTo(EMAIL)
    }

    @Test
    fun `it should encode a chosen date of birth as a native timestamp`() {
        // given
        val chosen = Instant.parse("1990-04-17T00:00:00Z")
        val details = AccountDetails().apply {
            setAny(AccountKeys.dateOfBirth::class, chosen)
        }

        // when
        val encoded = codec.encode(details = details)

        // then
        assertThat(encoded["dateOfBirth"]).isEqualTo(Timestamp(chosen.epochSecond, chosen.nano))
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
        val encoded = codec.encode(details = details)

        // then
        assertThat(encoded).containsKey("email")
    }

    @Test
    fun `it should decode a native timestamp back into an instant`() {
        // given
        val chosen = Instant.parse("1990-04-17T00:00:00Z")
        val stored = mapOf<String, Any?>("dateOfBirth" to Timestamp(chosen.epochSecond, chosen.nano))

        // when
        val decoded = codec.decode(firestoreData = stored, requestedKeys = setOf(AccountKeys.dateOfBirth))

        // then
        assertThat(decoded.getAnyOrNull(AccountKeys.dateOfBirth::class)).isEqualTo(chosen)
    }

    private companion object {
        const val EMAIL = "participant@example.org"
    }
}
