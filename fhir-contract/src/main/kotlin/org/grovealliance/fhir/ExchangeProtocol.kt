//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Byte-level primitives and lexical grammars of the exchange protocol, shared with every adapter. */
public object ExchangeProtocol {
    private const val PRODUCER_INSTANCE_PATTERN =
        "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"

    public val absoluteUri: Regex = Regex("[A-Za-z][A-Za-z0-9+.-]*:(?:[A-Za-z0-9._~:/?#\\[\\]@!\$&'()*+,;=-]|%[0-9A-Fa-f]{2})+")
    public val token: Regex = Regex("[A-Za-z0-9._-]+")
    public val positiveDecimal: Regex = Regex("[1-9][0-9]*")
    public val unsignedDecimal: Regex = Regex("0|[1-9][0-9]*")
    public val lowercaseCode: Regex = Regex("[a-z][a-z0-9-]*")
    public val producerInstance: Regex = Regex(PRODUCER_INSTANCE_PATTERN)
    public val fhirId: Regex = Regex("[A-Za-z0-9\\-.]{1,64}")
    public val opaqueIdentityValue: Regex = Regex(
        "${ExchangeContract.OPAQUE_IDENTITY_PREFIX}:([A-Za-z0-9._-]+):([1-9][0-9]*):([A-Za-z0-9_-]{43})",
    )
    public val eventIdentityValue: Regex = Regex(
        "${ExchangeContract.EVENT_IDENTITY_PREFIX}:($PRODUCER_INSTANCE_PATTERN):([1-9][0-9]*)",
    )
    public val entryNodeIdentityValue: Regex = Regex(
        "${ExchangeContract.ENTRY_NODE_IDENTITY_PREFIX}:([a-z][a-z0-9-]*):(0|[1-9][0-9]*):([A-Za-z0-9_-]{43})",
    )

    private val fullUrlNamespace = UUID.fromString(ExchangeContract.ENTRY_FULL_URL_NAMESPACE)

    /** Whether every UTF-16 code unit belongs to a paired surrogate or is a scalar value. */
    public fun isScalarText(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val current = value[index]
            when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= value.length || !Character.isLowSurrogate(value[index + 1])) return false
                    index += 2
                }
                Character.isLowSurrogate(current) -> return false
                else -> index += 1
            }
        }
        return true
    }

    /** FHIR identity systems use ASCII URI syntax, never an unescaped IRI or a relative reference. */
    public fun isAbsoluteAsciiUri(value: String): Boolean =
        absoluteUri.matches(value) && runCatching { URI(value).isAbsolute }.getOrDefault(false)

    /** Unsigned 32-bit big-endian UTF-8 byte length followed by the exact bytes, per field. */
    public fun frameFields(fields: Iterable<String>): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            fields.forEach { field ->
                require(isScalarText(field)) { "An exchange-protocol field contains an unpaired UTF-16 surrogate." }
                val encoded = field.toByteArray(Charsets.UTF_8)
                output.writeInt(encoded.size)
                output.write(encoded)
            }
        }
        bytes.toByteArray()
    }

    public fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    public fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    public fun sha256Hex(text: String): String =
        sha256(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** UUID version 5 over the length-framed `[system, value]` pair under the protocol namespace. */
    public fun fullUrl(system: String, value: String): String {
        val name = frameFields(listOf(system, value))
        val namespaceBytes = ByteBuffer.allocate(UUID_BYTE_COUNT)
            .putLong(fullUrlNamespace.mostSignificantBits)
            .putLong(fullUrlNamespace.leastSignificantBits)
            .array()
        val digest = MessageDigest.getInstance("SHA-1").apply {
            update(namespaceBytes)
            update(name)
        }.digest()
        digest[UUID_VERSION_BYTE_INDEX] =
            (digest[UUID_VERSION_BYTE_INDEX].toInt() and UUID_VERSION_CLEAR_MASK or UUID_VERSION_FIVE_BITS).toByte()
        digest[UUID_VARIANT_BYTE_INDEX] =
            (digest[UUID_VARIANT_BYTE_INDEX].toInt() and UUID_VARIANT_CLEAR_MASK or UUID_IETF_VARIANT_BITS).toByte()
        val buffer = ByteBuffer.wrap(digest, 0, UUID_BYTE_COUNT)
        return "urn:uuid:${UUID(buffer.long, buffer.long)}"
    }

    private const val UUID_VERSION_BYTE_INDEX = 6
    private const val UUID_VARIANT_BYTE_INDEX = 8
    private const val UUID_VERSION_CLEAR_MASK = 0x0f
    private const val UUID_VERSION_FIVE_BITS = 0x50
    private const val UUID_VARIANT_CLEAR_MASK = 0x3f
    private const val UUID_IETF_VARIANT_BITS = 0x80
    private const val UUID_BYTE_COUNT = 16
}
