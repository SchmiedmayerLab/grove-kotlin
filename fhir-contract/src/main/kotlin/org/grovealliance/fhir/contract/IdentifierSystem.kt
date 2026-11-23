//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.UriGrammar

/**
 * The namespace half of a FHIR business identifier: an absolute ASCII RFC 3986 URI naming whose
 * numbering scheme a value belongs to.
 *
 * The exact text is kept, because exchange identities are derived from its UTF-8 bytes. An IRI must
 * be given in its punycoded and percent-encoded URI form; nothing is normalized.
 */
@ConsistentCopyVisibility
data class IdentifierSystem private constructor(val rawValue: String) {

    override fun toString(): String = rawValue

    companion object {

        /**
         * The system [rawValue] names, or an [ExchangeIdentityError] when it is empty or not an
         * absolute ASCII URI.
         */
        fun create(rawValue: String): Result<IdentifierSystem> = when {
            rawValue.isEmpty() -> Result.failure(ExchangeIdentityError.MissingIdentifierSystem)
            !UriGrammar().isAbsoluteUri(value = rawValue) -> Result.failure(
                ExchangeIdentityError.InvalidIdentifierSystem(system = rawValue),
            )
            else -> Result.success(IdentifierSystem(rawValue = rawValue))
        }
    }
}
