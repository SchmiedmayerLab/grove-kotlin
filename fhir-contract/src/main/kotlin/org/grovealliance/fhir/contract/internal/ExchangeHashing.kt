//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * The byte operations every exchange identity is built from: the length framing of preimages, the
 * HMAC and hash functions, and base64url without padding.
 */
internal class ExchangeHashing {

    /**
     * Every field preceded by its unsigned 32-bit big-endian byte count, so no delimiter is special.
     */
    fun lengthFramed(fields: List<ByteArray>): ByteArray {
        val buffer = ByteBuffer.allocate(fields.sumOf { LENGTH_BYTES + it.size })
        fields.forEach { field ->
            buffer.putInt(field.size)
            buffer.put(field)
        }
        return buffer.array()
    }

    /**
     * [lengthFramed] over the UTF-8 bytes of [fields].
     */
    fun lengthFramedUtf8(
        fields: List<String>,
    ): ByteArray = lengthFramed(fields = fields.map { it.toByteArray(Charsets.UTF_8) })

    /**
     * HMAC-SHA-256 of [data] under [key].
     */
    fun hmacSha256(key: SecretKey, data: ByteArray): ByteArray = Mac.getInstance(HMAC_SHA256).run {
        init(key)
        doFinal(data)
    }

    /**
     * SHA-256 of [data].
     */
    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /**
     * SHA-1 of [parts], one after the other.
     */
    fun sha1(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").run {
        parts.forEach { update(it) }
        digest()
    }

    /**
     * [bytes] in base64url (RFC 4648 section 5) without padding.
     */
    fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val LENGTH_BYTES = 4
        const val HMAC_SHA256 = "HmacSHA256"
    }
}
