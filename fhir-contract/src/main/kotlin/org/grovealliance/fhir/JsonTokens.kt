//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/**
 * A lossless JSON token tree: members compare without order, strings after unescaping, numbers as
 * their exact lexeme. Two graphs are the same content when their trees are equal; `72` and `72.0`
 * are not.
 */
internal sealed interface JsonToken {
    data class Object(val members: Map<String, JsonToken>) : JsonToken

    data class Array(val items: List<JsonToken>) : JsonToken

    data class Text(val value: String) : JsonToken

    data class Number(val lexeme: String) : JsonToken

    data class Literal(val value: String) : JsonToken

    companion object {
        fun parse(json: String): JsonToken {
            val parser = JsonTokenParser(json)
            val token = parser.value()
            parser.end()
            return token
        }
    }
}

internal class JsonTokenParser(private val text: String) {
    private var index = 0

    fun value(): JsonToken {
        skipWhitespace()
        return when (peek()) {
            '{' -> obj()
            '[' -> array()
            '"' -> JsonToken.Text(string())
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            else -> number()
        }
    }

    fun end() {
        skipWhitespace()
        if (index != text.length) fail("trailing content")
    }

    private fun obj(): JsonToken.Object {
        expect('{')
        val members = linkedMapOf<String, JsonToken>()
        skipWhitespace()
        if (peek() == '}') {
            index++
            return JsonToken.Object(members)
        }
        while (true) {
            skipWhitespace()
            val name = string()
            skipWhitespace()
            expect(':')
            val member = value()
            if (members.put(name, member) != null) fail("duplicate member $name")
            skipWhitespace()
            when (next()) {
                ',' -> continue
                '}' -> return JsonToken.Object(members)
                else -> fail("expected , or }")
            }
        }
    }

    private fun array(): JsonToken.Array {
        expect('[')
        val items = mutableListOf<JsonToken>()
        skipWhitespace()
        if (peek() == ']') {
            index++
            return JsonToken.Array(items)
        }
        while (true) {
            items.add(value())
            skipWhitespace()
            when (next()) {
                ',' -> continue
                ']' -> return JsonToken.Array(items)
                else -> fail("expected , or ]")
            }
        }
    }

    private fun string(): String {
        expect('"')
        val builder = StringBuilder()
        while (true) {
            val character = next()
            when {
                character == '"' -> return builder.toString()
                character == '\\' -> builder.append(escape())
                character < ' ' -> fail("control character in string")
                else -> builder.append(character)
            }
        }
    }

    private fun escape(): String = when (val marker = next()) {
        '"' -> "\""
        '\\' -> "\\"
        '/' -> "/"
        'b' -> "\b"
        'f' -> ""
        'n' -> "\n"
        'r' -> "\r"
        't' -> "\t"
        'u' -> {
            if (index + HEX_DIGITS > text.length) fail("truncated escape")
            val code = text.substring(index, index + HEX_DIGITS).toIntOrNull(HEX_RADIX) ?: fail("malformed escape")
            index += HEX_DIGITS
            code.toChar().toString()
        }
        else -> fail("unknown escape \\$marker")
    }

    private fun number(): JsonToken.Number {
        val start = index
        if (peek() == '-') index++
        digits(allowEmpty = false)
        if (index < text.length && text[index] == '.') {
            index++
            digits(allowEmpty = false)
        }
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            index++
            if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
            digits(allowEmpty = false)
        }
        val lexeme = text.substring(start, index)
        if (!NUMBER.matches(lexeme)) fail("malformed number")
        return JsonToken.Number(lexeme)
    }

    private fun digits(allowEmpty: Boolean) {
        val start = index
        while (index < text.length && text[index].isAsciiDigit()) index++
        if (!allowEmpty && index == start) fail("expected digit")
    }

    private fun literal(expected: String): JsonToken.Literal {
        if (!text.startsWith(expected, index)) fail("expected $expected")
        index += expected.length
        return JsonToken.Literal(expected)
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index] in WHITESPACE) index++
    }

    private fun peek(): Char = if (index < text.length) text[index] else fail("unexpected end")

    private fun next(): Char = peek().also { index++ }

    private fun expect(character: Char) {
        if (next() != character) fail("expected $character")
    }

    private fun fail(reason: String): Nothing = throw IllegalArgumentException("Malformed JSON at $index: $reason")

    private companion object {
        const val HEX_DIGITS = 4
        const val HEX_RADIX = 16
        val WHITESPACE = setOf(' ', '\t', '\n', '\r')
        val NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    }
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
