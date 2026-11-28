//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:MagicNumber")

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.ExchangeHashing
import java.nio.ByteBuffer
import java.util.UUID

/**
 * A complete business identifier: the exact `Identifier.system` and `Identifier.value` pair.
 */
@ConsistentCopyVisibility
data class BusinessIdentifier internal constructor(
    val system: IdentifierSystem,
    val value: String,
) {

    /**
     * The deterministic `urn:uuid:` this identifier names: a lowercase RFC 4122 version-5 UUID over
     * the length-framed UTF-8 system and value.
     */
    val fullUrl: String
        get() {
            val hash = ExchangeHashing().sha1(NAMESPACE_BYTES, canonicalNameBytes)
            hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
            hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
            val hex = hash.take(16).joinToString(separator = "") { "%02x".format(it) }
            return "urn:uuid:${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        }

    /**
     * This identifier as a FHIR `Identifier` without a type.
     */
    val fhirIdentifier: Identifier
        get() = Identifier(
            system = system.rawValue,
            value = value,
        )

    /**
     * An identifier-only logical reference to a [resourceType] that does not travel in the same Bundle;
     * literal references are reserved for entries of the same Bundle.
     */
    fun reference(resourceType: ResourceType): Reference = Reference(
        identifier = fhirIdentifier,
        type = resourceType.value,
    )

    /**
     * The length-framed UTF-8 system and value the [fullUrl] is derived from.
     */
    internal val canonicalNameBytes: ByteArray
        get() = ExchangeHashing().lengthFramedUtf8(fields = listOf(system.rawValue, value))

    companion object {

        /**
         * The identifier [value] of [system], or [ExchangeIdentityError.MissingIdentifierValue] when
         * [value] is empty.
         */
        fun create(system: IdentifierSystem, value: String): Result<BusinessIdentifier> =
            if (value.isEmpty()) {
                Result.failure(ExchangeIdentityError.MissingIdentifierValue)
            } else {
                Result.success(
                    BusinessIdentifier(
                        system = system,
                        value = value,
                    ),
                )
            }

        private val NAMESPACE_BYTES: ByteArray = UUID.fromString(ExchangeContract.FULL_URL_NAMESPACE).let { namespace ->
            ByteBuffer.allocate(16)
                .putLong(namespace.mostSignificantBits)
                .putLong(namespace.leastSignificantBits)
                .array()
        }
    }
}
