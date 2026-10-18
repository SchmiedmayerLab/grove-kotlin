//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.storage.local

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import org.grovealliance.core.DefaultInitializer
import org.grovealliance.core.Module
import org.grovealliance.core.logging.groveLogger
import org.grovealliance.storage.local.LocalStorageSetting.Encrypted
import org.grovealliance.storage.local.LocalStorageSetting.EncryptedUsingKeyStore
import org.grovealliance.storage.local.LocalStorageSetting.Unencrypted
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface LocalStorage : Module {

    suspend fun <T : Any> store(
        key: String,
        value: T,
        settings: LocalStorageSetting,
        serializer: SerializationStrategy<T>,
    )

    suspend fun <T : Any> store(
        key: String,
        value: T,
        settings: LocalStorageSetting,
        encoding: (T) -> ByteArray,
    )

    suspend fun <T : Any> read(
        key: String,
        settings: LocalStorageSetting,
        serializer: DeserializationStrategy<T>,
    ): T?

    suspend fun <T : Any> read(
        key: String,
        settings: LocalStorageSetting,
        decoding: (ByteArray) -> T,
    ): T?

    suspend fun delete(key: String)

    companion object : DefaultInitializer<LocalStorage> {
        override fun create(context: Context): LocalStorage {
            return LocalStorageImpl(
                context = context,
                ioDispatcher = Dispatchers.IO,
                keyStorage = KeyStorageImpl(),
            )
        }
    }
}

internal class LocalStorageImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val keyStorage: KeyStorage = KeyStorageImpl(),
) : LocalStorage {

    private val logger by groveLogger()

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
            encoding = { instance ->
                Json.encodeToString(serializer, instance).toByteArray(StandardCharsets.UTF_8)
            }
        )
    }

    override suspend fun <T : Any> store(
        key: String,
        value: T,
        settings: LocalStorageSetting,
        encoding: (T) -> ByteArray,
    ) {
        execute {
            val jsonData = encoding(value)
            val keys = keys(settings)
            val writeData = if (keys == null) {
                jsonData
            } else {
                encrypt(data = jsonData, publicKey = keys.public)
            }
            file(key).writeBytes(writeData)
        }
    }

    override suspend fun <T : Any> read(
        key: String,
        settings: LocalStorageSetting,
        serializer: DeserializationStrategy<T>,
    ): T? {
        return read(
            key = key,
            settings = settings,
            decoding = { data ->
                Json.decodeFromString(serializer, String(data, StandardCharsets.UTF_8))
            }
        )
    }

    override suspend fun <T : Any> read(
        key: String,
        settings: LocalStorageSetting,
        decoding: (ByteArray) -> T,
    ): T? = execute {
        val keys = keys(settings)
        val bytes = file(key).readBytes()
        val data = if (keys == null) {
            bytes
        } else {
            decrypt(bytes = bytes, privateKey = keys.private)
        }
        decoding(data)
    }

    override suspend fun delete(key: String) {
        execute {
            val file = file(key)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun file(key: String): File {
        val directory = File(context.filesDir, "${STORAGE_FILE_PREFIX}LocalStorage")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "$key.localstorage")
    }

    private fun keys(settings: LocalStorageSetting): KeyPair? {
        return when (settings) {
            is Unencrypted -> null
            is Encrypted -> settings.keyPair
            is EncryptedUsingKeyStore -> with(keyStorage) {
                retrieveKeyPair(ANDROID_KEYSTORE_TAG)
                    ?: create(ANDROID_KEYSTORE_TAG).getOrThrow()
            }
        }
    }

    /**
     * Encrypts [data] of any size: the data with a new AES-GCM content key, and only that content key
     * with [publicKey], whose RSA padding limits what it can encrypt to a few hundred bytes.
     */
    private fun encrypt(data: ByteArray, publicKey: PublicKey): ByteArray {
        val contentKey = KeyGenerator.getInstance(CONTENT_KEY_ALGORITHM)
            .apply { init(CONTENT_KEY_SIZE) }
            .generateKey()
        val contentCipher = Cipher.getInstance(CONTENT_CIPHER_TRANSFORMATION)
            .apply { init(Cipher.ENCRYPT_MODE, contentKey) }
        val ciphertext = contentCipher.doFinal(data)
        val encryptedContentKey = getInitializedCipher(Cipher.ENCRYPT_MODE, publicKey).doFinal(contentKey.encoded)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.write(ENVELOPE_HEADER)
            stream.writeInt(encryptedContentKey.size)
            stream.write(encryptedContentKey)
            stream.writeInt(contentCipher.iv.size)
            stream.write(contentCipher.iv)
            stream.write(ciphertext)
        }
        return output.toByteArray()
    }

    /**
     * Decrypts [bytes] written by [encrypt], or, without its header, encrypted with [privateKey]'s
     * public key directly.
     */
    private fun decrypt(bytes: ByteArray, privateKey: PrivateKey): ByteArray {
        val isEnvelope = bytes.size > ENVELOPE_HEADER.size &&
            bytes.copyOfRange(0, ENVELOPE_HEADER.size).contentEquals(ENVELOPE_HEADER)
        if (!isEnvelope) return getInitializedCipher(Cipher.DECRYPT_MODE, privateKey).doFinal(bytes)

        return DataInputStream(ByteArrayInputStream(bytes, ENVELOPE_HEADER.size, bytes.size - ENVELOPE_HEADER.size)).use { stream ->
            val encryptedContentKey = ByteArray(stream.readInt()).also { stream.readFully(it) }
            val iv = ByteArray(stream.readInt()).also { stream.readFully(it) }
            val ciphertext = stream.readBytes()
            val contentKey = SecretKeySpec(
                getInitializedCipher(Cipher.DECRYPT_MODE, privateKey).doFinal(encryptedContentKey),
                CONTENT_KEY_ALGORITHM,
            )
            Cipher.getInstance(CONTENT_CIPHER_TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, contentKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)) }
                .doFinal(ciphertext)
        }
    }

    private fun getInitializedCipher(mode: Int, key: Key): Cipher =
        Cipher.getInstance(KeyStorage.CIPHER_TRANSFORMATION).apply { init(mode, key) }

    private suspend fun <T> execute(block: suspend () -> T) = withContext(ioDispatcher) {
        runCatching { block() }
            .onFailure {
                logger.e(it) { "Error executing local storage operation" }
            }.getOrNull()
    }

    private companion object {
        const val ANDROID_KEYSTORE_TAG = "LocalStorageTag"
        const val STORAGE_FILE_PREFIX = "org.grovealliance.storage."
        const val CONTENT_KEY_ALGORITHM = "AES"
        const val CONTENT_KEY_SIZE = 256
        const val CONTENT_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        val ENVELOPE_HEADER = byteArrayOf(0x47, 0x4C, 0x53, 0x01)
    }
}
