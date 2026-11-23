//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * A record's retry identity: the entry key of its lifecycle Provenance and the Provenance's [fullUrl].
 *
 * A conversion's is the keyed `record-version` over its source record and record content, so an
 * exact retry repeats it and any change to the record is a new version. A retraction's is the keyed
 * `record-retraction` over its source record alone. Only a holder of the identity key can recompute
 * either; a receiver compares them.
 */
@ConsistentCopyVisibility
data class RecordVersion private constructor(
    val identifier: RoledIdentifier,
    val fullUrl: String,
) {

    companion object {

        /**
         * The record version [identifier] keys, checking its shape only, as its value is keyed.
         *
         * @return [ExchangeIdentityError.InvalidIdentifierRole] unless [identifier] is a record version or
         * retraction, or [ExchangeIdentityError.InvalidRecordVersion] unless its value is a canonical
         * opaque identity
         */
        @InternalGroveFhirApi
        fun create(identifier: RoledIdentifier): Result<RecordVersion> = when {
            identifier.role != GroveIdentifierRole.RECORD_VERSION && identifier.role != GroveIdentifierRole.RECORD_RETRACTION ->
                Result.failure(ExchangeIdentityError.InvalidIdentifierRole(role = identifier.role.code))
            !OpaqueIdentityScope.isCanonicalOpaqueValue(value = identifier.identifier.value) ->
                Result.failure(ExchangeIdentityError.InvalidRecordVersion(value = identifier.identifier.value))
            else -> Result.success(
                RecordVersion(
                    identifier = identifier,
                    fullUrl = identifier.fullUrl,
                ),
            )
        }
    }
}
