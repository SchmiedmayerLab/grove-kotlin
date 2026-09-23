//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/**
 * Why a source-derived identity could not be minted or read.
 *
 * Configuration faults such as a malformed identifier system or a short key are not identity errors;
 * they fail at construction with an [IllegalArgumentException].
 */
public sealed interface ExchangeIdentityError {
    /** The registry diagnostic this error reports. */
    public val diagnostic: ProducerDiagnostic

    /** A source text carries an unpaired UTF-16 surrogate and cannot enter a preimage. */
    public data class NonScalarText(public val path: String) : ExchangeIdentityError {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_TEXT_NOT_UNICODE_SCALAR.at(path)
    }

    /** A source field the identity kind requires is absent or empty. */
    public data class EmptyComponent(public val path: String) : ExchangeIdentityError {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_REQUIRED_METADATA_MISSING.at(path)
    }

    /** A part index is not a canonical unsigned decimal: it carries a sign, whitespace or a leading zero. */
    public data class NonCanonicalPartIndex(public val path: String) : ExchangeIdentityError {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNCLASSIFIED.at(path)
    }

    /** A stored identifier does not have the canonical event or opaque identity form. */
    public data class MalformedIdentifier(public val path: String) : ExchangeIdentityError {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_EXCHANGE_EVENT_IDENTITY.at(path)
    }
}

/** Carries an [ExchangeIdentityError] across a minting boundary that has no result type. */
public class ExchangeIdentityException(public val error: ExchangeIdentityError) :
    IllegalArgumentException(error.diagnostic.toString())
