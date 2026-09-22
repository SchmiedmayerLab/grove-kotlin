//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/** An absolute ASCII RFC 3986 URI naming one identifier key space. */
@JvmInline
public value class IdentifierSystem(public val value: String) {
    init {
        require(ExchangeProtocol.isAbsoluteAsciiUri(value)) {
            "An identifier system must be an absolute ASCII RFC 3986 URI."
        }
    }

    override fun toString(): String = value
}

/** The durable, strictly increasing position of one exchange event, kept as canonical decimal text. */
@JvmInline
public value class EventSequence(public val value: String) : Comparable<EventSequence> {
    init {
        require(ExchangeProtocol.positiveDecimal.matches(value)) {
            "An event sequence must be a positive canonical decimal integer starting at one."
        }
    }

    /** Numeric order over canonical decimals: a longer spelling is the larger number. */
    override fun compareTo(other: EventSequence): Int =
        compareValuesBy(this, other, { it.value.length }, { it.value })

    override fun toString(): String = value

    public companion object {
        /** The sequence of a positive number. */
        public fun of(value: Long): EventSequence {
            require(value > 0) { "An event sequence starts at one." }
            return EventSequence(value.toString())
        }
    }
}

/** A repository-assigned FHIR `Resource.id`, never a business identity. */
@JvmInline
public value class RepositoryId(public val value: String) {
    init {
        require(ExchangeProtocol.fhirId.matches(value)) { "A repository id must use the FHIR id lexical form." }
    }

    override fun toString(): String = value
}
