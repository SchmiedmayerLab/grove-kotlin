//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.account.internal

import org.grovealliance.account.AccountDetails
import org.grovealliance.account.AccountDetailsCodecConfig
import org.grovealliance.account.AccountKey
import org.grovealliance.account.AccountKeys
import org.grovealliance.account.AnyAccountKey
import org.grovealliance.account.accountLogger
import org.grovealliance.account.holdsUnrepresentableInstant
import org.grovealliance.account.keys
import org.grovealliance.core.Module
import org.grovealliance.core.dependency
import org.grovealliance.foundation.JsonSerializer

@Suppress("UNCHECKED_CAST")
internal class AccountDetailsCodec : Module {
    private val codecConfig by dependency<AccountDetailsCodecConfig>()
    private val logger by accountLogger()

    fun encode(
        accountId: String,
        details: AccountDetails,
    ): StoredAccountDetails {
        val keyValues = buildMap {
            details.accountKeyTypes.keys().forEach { key ->
                if (key.identifier == AccountKeys.accountId.identifier) return@forEach
                val typedKey = key as AccountKey<Any>
                val value = details.getAnyOrNull(typedKey::class) ?: return@forEach
                if (typedKey.holdsUnrepresentableInstant(value)) return@forEach

                // One field that cannot be encoded must not cost the rest of the cached details.
                val encoded = runCatching {
                    JsonSerializer.encodeToElement(value, typedKey.serializer)
                }.getOrElse { throwable ->
                    logger.e(throwable) { "Dropping '${typedKey.identifier}', which could not be encoded." }
                    return@forEach
                }

                put(
                    key = codecConfig.encodingIdentifier(typedKey),
                    value = encoded,
                )
            }
        }

        return StoredAccountDetails(
            accountId = accountId,
            accountKeyValues = keyValues,
        )
    }

    fun decode(stored: StoredAccountDetails, keys: Set<AnyAccountKey>): AccountDetails {
        val details = AccountDetails()

        stored.accountKeyValues.forEach { (keyId, jsonElement) ->
            val key = codecConfig.resolveDecodingKey(storedIdentifier = keyId, requestedKeys = keys) ?: return@forEach
            val typedKey = key as AccountKey<Any>

            val value = JsonSerializer.decodeFromElementOrNull(jsonElement, typedKey.serializer)
                ?: return@forEach

            details.setAny(typedKey::class, value)
        }

        details[AccountKeys.accountId::class] = stored.accountId
        return details
    }
}
