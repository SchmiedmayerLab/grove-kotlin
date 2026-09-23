//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/**
 * The source-record identity of one adapter record, which its output and artifact identities extend.
 *
 * It is immutable, holds the minting scope and the record's components, and never prints the native record id.
 */
public class SourceRecordIdentity internal constructor(
    private val scope: OpaqueIdentityScope,
    private val components: List<String>,
) {
    /** The typed `source-record` identifier. */
    public val identifier: RoledIdentifier = scope.mint(OpaqueIdentityKind.SOURCE_RECORD, components)

    /** The `source-output` identifier of one output this record yields. */
    public fun output(role: String, discriminator: String): RoledIdentifier =
        scope.mint(OpaqueIdentityKind.SOURCE_OUTPUT, components + listOf(role, discriminator))

    /** The `source-artifact` identifier of one part of a recording this record carries. */
    public fun artifact(formatCode: String, partIndex: Long): RoledIdentifier =
        scope.mint(OpaqueIdentityKind.SOURCE_ARTIFACT, components + listOf(formatCode, partIndex.toString()))

    override fun toString(): String = "SourceRecordIdentity(identifier=$identifier)"
}

/**
 * The provider-record identity of one provider record, which its output and artifact identities extend.
 *
 * It is immutable, holds the minting scope and the record's components, and never prints the native record id.
 */
public class ProviderRecordIdentity internal constructor(
    private val scope: OpaqueIdentityScope,
    private val components: List<String>,
) {
    /** The typed `provider-record` identifier. */
    public val identifier: RoledIdentifier = scope.mint(OpaqueIdentityKind.PROVIDER_RECORD, components)

    /** The `provider-output` identifier of one output this record yields. */
    public fun output(role: String, discriminator: String): RoledIdentifier =
        scope.mint(OpaqueIdentityKind.PROVIDER_OUTPUT, components + listOf(role, discriminator))

    /** The `provider-artifact` identifier of one part of a recording this record carries. */
    public fun artifact(formatCode: String, partIndex: Long): RoledIdentifier =
        scope.mint(OpaqueIdentityKind.PROVIDER_ARTIFACT, components + listOf(formatCode, partIndex.toString()))

    override fun toString(): String = "ProviderRecordIdentity(identifier=$identifier)"
}
