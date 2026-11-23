//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class LosslessJsonValueTest {
    private val catalog = ExchangeProtocolCatalog()

    @Test
    fun `writes every canonical JSON vector`() {
        // given
        val vectors = catalog.vectors(name = "canonicalJson")

        // when / then
        vectors.forEach { vector ->
            val canonical = parse(text = vector.text(name = "input")).canonicalBytes()
            assertThat(String(canonical, Charsets.UTF_8)).isEqualTo(vector.text(name = "canonical"))
            assertThat(MessageDigest.getInstance("SHA-256").digest(canonical).joinToString(separator = "") { "%02x".format(it) })
                .isEqualTo(vector.text(name = "sha256"))
        }
    }

    @Test
    fun `refuses every invalid canonical JSON vector with its reason`() {
        // given
        val vectors = catalog.vectors(name = "invalidCanonicalJson")

        // when
        val reasons = vectors.map { vector ->
            val bytes = vector.text(name = "inputHex").chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()
            (LosslessJsonValue.parse(bytes = bytes).exceptionOrNull() as? LosslessJsonParseException)?.reason?.code
        }

        // then
        assertThat(reasons).isEqualTo(vectors.map { it.text(name = "expectedError") })
    }

    @Test
    fun `digests every record content vector`() {
        // given
        val vectors = catalog.vectors(name = "recordContents")

        // when / then
        vectors.forEach { vector ->
            val digest = LosslessJsonValue.recordContentDigest(
                provenance = parse(text = vector.text(name = "provenance")),
                outputs = vector.texts(name = "outputs").map { parse(text = it) },
            ).getOrThrow()
            assertThat(digest).isEqualTo(vector.text(name = "recordContentDigest"))
        }
    }

    @Test
    fun `keeps number lexemes distinct`() {
        // given
        val integer = parse(text = """{"value":72}""")

        // when
        val decimal = parse(text = """{"value":72.0}""")

        // then
        assertThat(integer).isNotEqualTo(decimal)
    }

    @Test
    fun `digests a device without its id and its own snapshot key`() {
        // given
        val device = parse(
            text = """{"resourceType":"Device","id":"a","status":"active","identifier":[{"system":"https://x.org","value":"v",""" +
                """"type":{"coding":[{"system":"${GroveIdentifierRole.CODE_SYSTEM}","code":"device-snapshot"}]}}]}""",
        )

        // when
        val digest = device.contentDigest().getOrThrow()

        // then
        val bare = parse(text = """{"resourceType":"Device","status":"active"}""").canonicalBytes()
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bare))
        assertThat(digest).isEqualTo(expected)
    }

    @Test
    fun `refuses a content digest for a resource no snapshot keys`() {
        // given
        val observation = parse(text = """{"resourceType":"Observation","status":"final"}""")

        // when
        val result = observation.contentDigest()

        // then
        assertThat(result.exceptionOrNull()).isEqualTo(
            ExchangeIdentityError.InvalidDigestContent(reason = "Observation carries no content-snapshot key."),
        )
    }

    private fun parse(text: String) = LosslessJsonValue.parse(bytes = text.toByteArray(Charsets.UTF_8)).getOrThrow()
}
