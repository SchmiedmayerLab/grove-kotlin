//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FhirDecimalTest {

    @Test
    fun `writes the shortest round-tripping plain decimal`() {
        // given
        val values = listOf(72.0, 0.1, 1.0 / 3, 1e-7, 1e23, -2.5, 0.0)

        // when
        val lexemes = values.map { FhirDecimal.of(value = it).getOrThrow().lexeme }

        // then
        assertThat(lexemes).containsExactly("72", "0.1", "0.3333333333333333", "0.0000001", "100000000000000000000000", "-2.5", "0")
            .inOrder()
    }

    @OptIn(InternalGroveFhirApi::class)
    @Test
    fun `formats instants at millisecond precision`() {
        // given
        val instant = ExchangeInstant()

        // when
        val whole = instant.utc(instant = Instant.parse("2026-01-01T10:30:00Z"))
        val fraction = instant.at(instant = Instant.parse("2026-01-01T17:30:00.251Z"), offset = ZoneOffset.ofHours(-7))

        // then
        assertThat(whole).isEqualTo("2026-01-01T10:30:00Z")
        assertThat(fraction).isEqualTo("2026-01-01T10:30:00.251-07:00")
    }
}
