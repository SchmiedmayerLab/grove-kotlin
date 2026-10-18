//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.storage.local

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * A [LocalStorage] that keeps values in memory for the lifetime of the instance.
 *
 * [LocalStorageSetting]s are accepted but have no effect: values are never encrypted.
 */
class FakeLocalStorage : LocalStorage {
    private val storage = ConcurrentHashMap<String, ByteArray>()

    override suspend fun <T : Any> store(
        key: String,
        value: T,
        settings: LocalStorageSetting,
        serializer: SerializationStrategy<T>,
    ) {
        store(
            key = key,
            value = value,
            settings = settings,
            encoding = { Json.encodeToString(serializer, it).toByteArray(StandardCharsets.UTF_8) },
        )
    }

    override suspend fun <T : Any> store(
        key: String,
        value: T,
        settings: LocalStorageSetting,
        encoding: (T) -> ByteArray,
    ) {
        storage[key] = encoding(value)
    }

    override suspend fun <T : Any> read(
        key: String,
        settings: LocalStorageSetting,
        serializer: DeserializationStrategy<T>,
    ): T? = read(
        key = key,
        settings = settings,
        decoding = { Json.decodeFromString(serializer, String(it, StandardCharsets.UTF_8)) },
    )

    override suspend fun <T : Any> read(
        key: String,
        settings: LocalStorageSetting,
        decoding: (ByteArray) -> T,
    ): T? = storage[key]?.let(decoding)

    override suspend fun delete(key: String) {
        storage.remove(key)
    }
}
