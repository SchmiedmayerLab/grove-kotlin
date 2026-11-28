//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

@file:Suppress("detekt:MagicNumber")

package org.grovealliance.fhir.contract.internal

/**
 * Decides whether text states nothing, by the Unicode `White_Space` set plus U+200B, so the same
 * text counts as blank on every platform.
 */
internal class BlankText {

    fun isBlank(value: String): Boolean = value.codePoints().allMatch { codePoint ->
        codePoint in 0x09..0x0D ||
            codePoint == 0x20 ||
            codePoint == 0x85 ||
            codePoint == 0xA0 ||
            codePoint == 0x1680 ||
            codePoint in 0x2000..0x200B ||
            codePoint in setOf(0x2028, 0x2029, 0x202F, 0x205F, 0x3000)
    }

    /**
     * [value], or `null` when it is absent or blank.
     */
    fun nonBlank(value: String?): String? = value?.takeUnless { isBlank(value = it) }
}
