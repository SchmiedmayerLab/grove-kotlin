//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The source-record identity of one native record, from which its output and artifact identities
 * are minted.
 *
 * Their preimages extend the record's components with their own. A provider-owned record mints
 * under the provider identity kinds, in the same `source-*` roles. [toString] names the opaque
 * identifier only, never the native record id.
 */
class SourceRecordIdentity private constructor(
    val identifier: RoledIdentifier,
    private val scope: OpaqueIdentityScope,
    private val components: List<String>,
    private val isProviderRecord: Boolean,
) {

    override fun toString(): String = "SourceRecordIdentity(identifier=${identifier.identifier.value})"

    /**
     * The `source-output` identifier of one output this record converts to: its [role], a lowercase
     * code token, and the [discriminator] that tells it apart from the record's other outputs in that role.
     */
    fun output(role: String, discriminator: String): Result<RoledIdentifier> = scope.output(
        record = components,
        kind = if (isProviderRecord) OpaqueIdentityKind.PROVIDER_OUTPUT else OpaqueIdentityKind.SOURCE_OUTPUT,
        role = role,
        discriminator = discriminator,
    )

    /**
     * The `source-artifact` identifier of part [partIndex] of the record's native artifact in [formatCode].
     */
    @InternalGroveFhirApi
    fun artifact(
        formatCode: String,
        partIndex: CanonicalNonnegativeDecimal,
    ): Result<RoledIdentifier> = scope.artifact(
        record = components,
        kind = if (isProviderRecord) OpaqueIdentityKind.PROVIDER_ARTIFACT else OpaqueIdentityKind.SOURCE_ARTIFACT,
        formatCode = formatCode,
        partIndex = partIndex,
    )

    internal companion object {

        fun create(
            scope: OpaqueIdentityScope,
            components: List<String>,
            isProviderRecord: Boolean,
        ): Result<SourceRecordIdentity> = scope.identifier(
            kind = if (isProviderRecord) OpaqueIdentityKind.PROVIDER_RECORD else OpaqueIdentityKind.SOURCE_RECORD,
            components = components,
        ).map { identifier ->
            SourceRecordIdentity(
                identifier = identifier,
                scope = scope,
                components = components,
                isProviderRecord = isProviderRecord,
            )
        }
    }
}
