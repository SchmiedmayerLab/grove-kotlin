//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:MagicNumber")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonValue
import org.grovealliance.fhir.contract.ResourceType
import java.io.ByteArrayOutputStream

/**
 * Writes a [LosslessJsonValue] in `grove-canonical-json-v0`.
 */
@OptIn(InternalGroveFhirApi::class)
internal class CanonicalJsonWriter {

    fun write(value: LosslessJsonValue): ByteArray = ByteArrayOutputStream().also { write(value = value, output = it) }.toByteArray()

    /**
     * A collection Bundle of [entries], each already in canonical form, claiming [profile]: every other
     * member name sorts after `entry`, so the entries are spliced as they stand, never re-encoded.
     */
    fun bundle(
        profile: String,
        timestamp: String,
        identifier: Pair<String, String>?,
        entries: List<ByteArray>,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("{\"entry\":[".toByteArray(Charsets.UTF_8))
        entries.forEachIndexed { index, entry ->
            if (index > 0) output.write(','.code)
            output.write(entry)
        }
        output.write("],".toByteArray(Charsets.UTF_8))
        val header = LinkedHashMap<String, LosslessJsonValue>()
        identifier?.let { (system, value) ->
            header["identifier"] = LosslessJsonValue.JsonObject(
                members = mapOf(
                    "system" to LosslessJsonValue.JsonString(value = system),
                    "value" to LosslessJsonValue.JsonString(value = value),
                ),
            )
        }
        header["meta"] = LosslessJsonValue.JsonObject(
            members = mapOf("profile" to LosslessJsonValue.JsonArray(elements = listOf(LosslessJsonValue.JsonString(value = profile)))),
        )
        header["resourceType"] = LosslessJsonValue.JsonString(value = ResourceType.BUNDLE.value)
        header["timestamp"] = LosslessJsonValue.JsonString(value = timestamp)
        header["type"] = LosslessJsonValue.JsonString(value = "collection")
        val headerBytes = write(value = LosslessJsonValue.JsonObject(members = header))
        output.write(headerBytes, 1, headerBytes.size - 1)
        return output.toByteArray()
    }

    private fun write(value: LosslessJsonValue, output: ByteArrayOutputStream) {
        when (value) {
            is LosslessJsonValue.JsonObject -> {
                output.write('{'.code)
                value.members.entries.sortedBy { it.key }.forEachIndexed { index, (name, member) ->
                    if (index > 0) output.write(','.code)
                    writeString(string = name, output = output)
                    output.write(':'.code)
                    write(value = member, output = output)
                }
                output.write('}'.code)
            }
            is LosslessJsonValue.JsonArray -> {
                output.write('['.code)
                value.elements.forEachIndexed { index, element ->
                    if (index > 0) output.write(','.code)
                    write(value = element, output = output)
                }
                output.write(']'.code)
            }
            is LosslessJsonValue.JsonString -> writeString(string = value.value, output = output)
            is LosslessJsonValue.JsonNumber -> output.write(value.lexeme.toByteArray(Charsets.UTF_8))
            is LosslessJsonValue.JsonBoolean -> output.write((if (value.value) "true" else "false").toByteArray(Charsets.UTF_8))
            LosslessJsonValue.JsonNull -> output.write("null".toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * Escapes byte by byte: every byte of a multi-byte scalar is above 0x7F, so only ASCII is ever escaped.
     */
    private fun writeString(string: String, output: ByteArrayOutputStream) {
        output.write('"'.code)
        string.toByteArray(Charsets.UTF_8).forEach { byte ->
            val code = byte.toInt() and 0xFF
            when (code) {
                '"'.code, '\\'.code -> output.write(byteArrayOf('\\'.code.toByte(), byte))
                0x08 -> output.write("\\b".toByteArray(Charsets.UTF_8))
                0x09 -> output.write("\\t".toByteArray(Charsets.UTF_8))
                0x0A -> output.write("\\n".toByteArray(Charsets.UTF_8))
                0x0C -> output.write("\\f".toByteArray(Charsets.UTF_8))
                0x0D -> output.write("\\r".toByteArray(Charsets.UTF_8))
                in 0x00..0x1F -> output.write("\\u%04x".format(code).toByteArray(Charsets.UTF_8))
                else -> output.write(code)
            }
        }
        output.write('"'.code)
    }
}
