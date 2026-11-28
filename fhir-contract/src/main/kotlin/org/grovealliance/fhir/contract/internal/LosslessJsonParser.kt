//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:MagicNumber", "detekt:CyclomaticComplexMethod")

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.LosslessJsonParseException
import org.grovealliance.fhir.contract.LosslessJsonParseException.Reason
import org.grovealliance.fhir.contract.LosslessJsonValue
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Reads one RFC 8259 JSON text in UTF-8 into a [LosslessJsonValue], refusing what
 * `grove-canonical-json-v0` refuses: invalid UTF-8, a byte order mark, a non-ASCII or repeated
 * member name, and an unpaired surrogate escape.
 */
@OptIn(InternalGroveFhirApi::class)
@Suppress("detekt:TooManyFunctions")
internal class LosslessJsonParser(
    private val bytes: ByteArray,
    private val lenient: Boolean = false,
) {
    private var index = 0
    private val path = ArrayDeque<String>()

    /**
     * In lenient mode, the paths of the members whose names `grove-canonical-json-v0` refuses: non-ASCII
     * or repeated names, which lenient parsing keeps (a repeated name keeps its last value).
     */
    val nonCanonicalPaths = mutableListOf<List<String>>()

    fun parse(): Result<LosslessJsonValue> = try {
        if (bytes.size >= BYTE_ORDER_MARK.size && bytes.copyOfRange(0, BYTE_ORDER_MARK.size).contentEquals(BYTE_ORDER_MARK)) {
            fail(reason = Reason.BYTE_ORDER_MARK)
        }
        val value = value()
        skipWhitespace()
        if (index < bytes.size) fail(reason = Reason.INVALID_JSON)
        Result.success(value)
    } catch (exception: LosslessJsonParseException) {
        Result.failure(exception)
    }

    private fun value(): LosslessJsonValue {
        skipWhitespace()
        if (index >= bytes.size) fail(reason = Reason.INVALID_JSON)
        return when (bytes[index].toInt().toChar()) {
            '{' -> jsonObject()
            '[' -> jsonArray()
            '"' -> LosslessJsonValue.JsonString(value = string())
            't' -> literal(text = "true", value = LosslessJsonValue.JsonBoolean(value = true))
            'f' -> literal(text = "false", value = LosslessJsonValue.JsonBoolean(value = false))
            'n' -> literal(text = "null", value = LosslessJsonValue.JsonNull)
            else -> LosslessJsonValue.JsonNumber(lexeme = number())
        }
    }

    private fun jsonObject(): LosslessJsonValue {
        index += 1
        val members = LinkedHashMap<String, LosslessJsonValue>()
        skipWhitespace()
        if (consume(character = '}')) return LosslessJsonValue.JsonObject(members = members)
        while (true) {
            skipWhitespace()
            if (index >= bytes.size || bytes[index] != '"'.code.toByte()) fail(reason = Reason.INVALID_JSON)
            val name = string()
            val isNonAscii = name.any { it.code >= 0x80 }
            if (isNonAscii && !lenient) fail(reason = Reason.NON_ASCII_MEMBER_NAME)
            skipWhitespace()
            if (!consume(character = ':')) fail(reason = Reason.INVALID_JSON)
            path.addLast(name)
            val isDuplicate = members.put(name, value()) != null
            path.removeLast()
            if (isDuplicate && !lenient) fail(reason = Reason.DUPLICATE_MEMBER)
            if (isNonAscii || isDuplicate) nonCanonicalPaths += path.toList()
            skipWhitespace()
            if (consume(character = ',')) continue
            if (!consume(character = '}')) fail(reason = Reason.INVALID_JSON)
            return LosslessJsonValue.JsonObject(members = members)
        }
    }

    private fun jsonArray(): LosslessJsonValue {
        index += 1
        val elements = mutableListOf<LosslessJsonValue>()
        skipWhitespace()
        if (consume(character = ']')) return LosslessJsonValue.JsonArray(elements = elements)
        while (true) {
            path.addLast(elements.size.toString())
            elements += value()
            path.removeLast()
            skipWhitespace()
            if (consume(character = ',')) continue
            if (!consume(character = ']')) fail(reason = Reason.INVALID_JSON)
            return LosslessJsonValue.JsonArray(elements = elements)
        }
    }

    private fun literal(text: String, value: LosslessJsonValue): LosslessJsonValue {
        val expected = text.toByteArray(Charsets.US_ASCII)
        if (bytes.size - index < expected.size || !bytes.copyOfRange(index, index + expected.size).contentEquals(expected)) {
            fail(reason = Reason.INVALID_JSON)
        }
        index += expected.size
        return value
    }

    private fun number(): String {
        val start = index
        consume(character = '-')
        digits(allowingLeadingZeroOnly = true)
        if (index < bytes.size && bytes[index] == '.'.code.toByte()) {
            index += 1
            digits(allowingLeadingZeroOnly = false)
        }
        if (index < bytes.size && (bytes[index] == 'e'.code.toByte() || bytes[index] == 'E'.code.toByte())) {
            index += 1
            if (index < bytes.size && (bytes[index] == '+'.code.toByte() || bytes[index] == '-'.code.toByte())) index += 1
            digits(allowingLeadingZeroOnly = false)
        }
        return String(bytes, start, index - start, Charsets.US_ASCII)
    }

    private fun digits(allowingLeadingZeroOnly: Boolean) {
        val start = index
        while (index < bytes.size && bytes[index] in '0'.code.toByte()..'9'.code.toByte()) index += 1
        if (index == start) fail(reason = Reason.INVALID_JSON)
        if (allowingLeadingZeroOnly && bytes[start] == '0'.code.toByte() && index - start > 1) fail(reason = Reason.INVALID_JSON)
    }

    private fun string(): String {
        index += 1
        val result = StringBuilder()
        var runStart = index
        while (index < bytes.size) {
            val byte = bytes[index].toInt() and 0xFF
            when {
                byte == '"'.code -> {
                    result.append(decodeUtf8(start = runStart, end = index))
                    index += 1
                    return result.toString()
                }
                byte == '\\'.code -> {
                    result.append(decodeUtf8(start = runStart, end = index))
                    index += 1
                    escape(into = result)
                    runStart = index
                }
                byte < 0x20 -> fail(reason = Reason.INVALID_JSON)
                else -> index += 1
            }
        }
        fail(reason = Reason.INVALID_JSON)
    }

    private fun escape(into: StringBuilder) {
        if (index >= bytes.size) fail(reason = Reason.INVALID_JSON)
        val character = bytes[index].toInt().toChar()
        index += 1
        when (character) {
            '"', '\\', '/' -> into.append(character)
            'b' -> into.append('\b')
            'f' -> into.append('\u000C')
            'n' -> into.append('\n')
            'r' -> into.append('\r')
            't' -> into.append('\t')
            'u' -> unicodeEscape(into = into)
            else -> fail(reason = Reason.INVALID_JSON)
        }
    }

    private fun unicodeEscape(into: StringBuilder) {
        val unit = codeUnit()
        when {
            unit.isLowSurrogate() -> fail(reason = Reason.UNPAIRED_SURROGATE)
            unit.isHighSurrogate() -> {
                if (!consume(character = '\\') || !consume(character = 'u')) fail(reason = Reason.UNPAIRED_SURROGATE)
                val trail = codeUnit()
                if (!trail.isLowSurrogate()) fail(reason = Reason.UNPAIRED_SURROGATE)
                into.append(unit).append(trail)
            }
            else -> into.append(unit)
        }
    }

    private fun codeUnit(): Char {
        if (bytes.size - index < 4) fail(reason = Reason.INVALID_JSON)
        val text = String(bytes, index, 4, Charsets.US_ASCII)
        if (!text.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail(reason = Reason.INVALID_JSON)
        index += 4
        return text.toInt(radix = 16).toChar()
    }

    /**
     * Decodes strictly, so malformed UTF-8 and encoded surrogates are refused rather than replaced.
     */
    private fun decodeUtf8(start: Int, end: Int): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes, start, end - start))
            .toString()
    } catch (_: CharacterCodingException) {
        fail(reason = Reason.INVALID_UTF8)
    }

    private fun consume(character: Char): Boolean {
        if (index >= bytes.size) fail(reason = Reason.INVALID_JSON)
        if (bytes[index] != character.code.toByte()) return false
        index += 1
        return true
    }

    private fun skipWhitespace() {
        while (index < bytes.size && bytes[index].toInt() in WHITESPACE) index += 1
    }

    private fun fail(reason: Reason): Nothing = throw LosslessJsonParseException(reason = reason)

    private companion object {
        val WHITESPACE = setOf(0x20, 0x09, 0x0A, 0x0D)
        val BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
