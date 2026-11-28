//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:MagicNumber", "detekt:ReturnCount")

package org.grovealliance.fhir.contract.internal

/**
 * Checks the absolute ASCII RFC 3986 URIs that identifier systems must be.
 */
internal class UriGrammar {

    /**
     * Whether [value] is an absolute URI: a scheme, a colon and a non-empty remainder of URI
     * characters and valid percent escapes, with a well-formed bracketed host if it has one.
     */
    fun isAbsoluteUri(value: String): Boolean {
        val colon = value.indexOf(':')
        if (colon <= 0 || colon + 1 >= value.length) return false
        val scheme = value.substring(0, colon)
        val isScheme = scheme.first().isAsciiLetter() &&
            scheme.drop(1).all { it.isAsciiLetter() || it.isAsciiDigit() || it == '+' || it == '-' || it == '.' }
        return isScheme && hasUriCharacters(value = value.substring(colon + 1)) && hasValidIpLiteral(value = value, colon = colon)
    }

    private fun hasUriCharacters(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '%') {
                if (index + 2 >= value.length || !value[index + 1].isHexDigit() || !value[index + 2].isHexDigit()) return false
                index += 3
            } else {
                if (character !in URI_CHARACTERS) return false
                index += 1
            }
        }
        return true
    }

    /**
     * A bracketed host is admitted only for IPv6 or IPvFuture.
     */
    private fun hasValidIpLiteral(value: String, colon: Int): Boolean {
        if (!value.startsWith("//", startIndex = colon + 1)) return true
        val authority = value.substring(colon + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').let { hostAndPort ->
            if (hostAndPort.startsWith("[")) hostAndPort.substringBefore(']') + "]" else hostAndPort.substringBefore(':')
        }
        if (!host.startsWith("[") && !host.endsWith("]")) return true
        if (!host.startsWith("[") || !host.endsWith("]")) return false
        val literal = host.substring(1, host.length - 1)
        return isIpv6(value = literal) || isIpvFuture(value = literal)
    }

    private fun isIpvFuture(value: String): Boolean {
        val dot = value.indexOf('.')
        val hasVersionPrefix = value.startsWith("v") || value.startsWith("V")
        if (!hasVersionPrefix || dot <= 1) return false
        val address = value.substring(dot + 1)
        return value.substring(1, dot).all { it.isHexDigit() } &&
            address.isNotEmpty() &&
            address.all { it in IPV_FUTURE_CHARACTERS }
    }

    private fun isIpv6(value: String): Boolean {
        if (!value.contains(':') || value.contains(":::")) return false
        val compressedParts = value.split("::")
        if (compressedParts.size > 2) return false
        val groups = value.split(':').filter { it.isNotEmpty() }
        var groupCount = 0
        groups.forEachIndexed { index, group ->
            if (group.contains('.')) {
                if (index != groups.lastIndex || !isIpv4(value = group)) return false
                groupCount += 2
            } else {
                if (group.length !in 1..4 || !group.all { it.isHexDigit() }) return false
                groupCount += 1
            }
        }
        return if (compressedParts.size == 2) {
            groupCount < 8
        } else {
            groupCount == 8 && !value.startsWith(":") && !value.endsWith(":")
        }
    }

    private fun isIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.toIntOrNull()?.let { it in 0..255 && it.toString() == octet } ?: false
        }
    }

    private fun Char.isAsciiLetter() = this in 'A'..'Z' || this in 'a'..'z'

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun Char.isHexDigit() = isAsciiDigit() || this in 'A'..'F' || this in 'a'..'f'

    private companion object {
        const val URI_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._~:/?#[]@!$&'()*+,;=-"
        const val IPV_FUTURE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._~!$&'()*+,;=:-"
    }
}
