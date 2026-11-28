//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BusinessIdentifierTest {
    private val catalog = ExchangeProtocolCatalog()

    @Test
    fun `derives every full URL vector`() {
        // given
        val vectors = catalog.vectors(name = "fullUrls")

        // when / then
        vectors.forEach { vector ->
            val identifier = BusinessIdentifier.create(
                system = IdentifierSystem.create(rawValue = vector.text(name = "system")).getOrThrow(),
                value = vector.text(name = "value"),
            ).getOrThrow()
            assertThat(identifier.fullUrl).isEqualTo(vector.text(name = "fullUrl"))
        }
    }

    @Test
    fun `refuses systems that are not absolute ASCII URIs`() {
        // given
        val invalid = listOf(
            "",
            "no-scheme",
            "https:",
            "https://exämple.org",
            "https://example.org/%zz",
            "https://[zz]/x",
            "1https://x.org",
        )

        // when
        val results = invalid.map { IdentifierSystem.create(rawValue = it).isSuccess }

        // then
        assertThat(results).doesNotContain(true)
    }

    @Test
    fun `accepts bracketed IPv6 hosts and percent escapes`() {
        // given
        val valid = listOf(
            "https://[2001:db8::1]/fhir",
            "https://xn--fsq.example/%E8%AD%98",
            "urn:uuid:1f5c58aa-6ec6-4e79-a682-829a9debd3f5",
        )

        // when
        val results = valid.map { IdentifierSystem.create(rawValue = it).isSuccess }

        // then
        assertThat(results).doesNotContain(false)
    }

    @Test
    fun `refuses an empty value`() {
        // given
        val system = IdentifierSystem.create(rawValue = "https://example.org").getOrThrow()

        // when
        val result = BusinessIdentifier.create(system = system, value = "")

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(ExchangeIdentityError.MissingIdentifierValue)
    }

    @Test
    fun `orders key epochs by value and refuses non-canonical ones`() {
        // given
        val texts = listOf("10", "9", "0", "01", "-1")

        // when
        val epochs = texts.map { KeyEpoch.parse(text = it).getOrNull() }

        // then
        assertThat(epochs.drop(2)).containsExactly(null, null, null)
        assertThat(epochs[0]!! > epochs[1]!!).isTrue()
    }
}
