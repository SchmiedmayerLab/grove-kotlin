//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Why an identity or an identifier could not be formed or read: one error for every identity the
 * contract mints or validates, from the deployment's systems and key to a graph's entry keys.
 *
 * A component fault names the component by its path, `<identity-kind>.<component>` as the catalog
 * spells them.
 */
sealed class ExchangeIdentityError(message: String) : Exception(message) {

    /**
     * An identifier states no system.
     */
    data object MissingIdentifierSystem : ExchangeIdentityError("An identifier states no system.")

    /**
     * An identifier states no value, or an empty one.
     */
    data object MissingIdentifierValue : ExchangeIdentityError("An identifier states no value.")

    /**
     * [system] is not an absolute ASCII URI.
     */
    data class InvalidIdentifierSystem(val system: String) :
        ExchangeIdentityError("$system is not an absolute ASCII URI.")

    /**
     * [keyId] is empty or holds a character other than ASCII letters, digits, `-`, `.` and `_`.
     */
    data class InvalidKeyId(val keyId: String) : ExchangeIdentityError("$keyId is not a valid key id.")

    /**
     * The exchange protocol's recommended identifier systems can't be formed under [root].
     */
    data class InvalidDeploymentRoot(val root: String) :
        ExchangeIdentityError("No identifier systems can be derived under $root.")

    /**
     * The key has [actualBytes] bytes, fewer than the 32 the protocol requires.
     */
    data class KeyTooShort(val actualBytes: Int) :
        ExchangeIdentityError("The key has $actualBytes bytes; at least 32 are required.")

    /**
     * The key is the published conformance key, which no deployment may use.
     */
    data object PublishedConformanceKeyProhibited :
        ExchangeIdentityError("The published conformance key can't be used outside conformance tests.")

    /**
     * An identifier's Grove role is missing or not one of [GroveIdentifierRole]; [role] states what was found.
     */
    data class InvalidIdentifierRole(val role: String) :
        ExchangeIdentityError("$role is not a Grove identifier role.")

    /**
     * An identifier states more than one Grove role.
     */
    data object DuplicateIdentifierRole : ExchangeIdentityError("An identifier states more than one Grove role.")

    /**
     * The component at [path] is empty.
     */
    data class EmptyComponent(val path: String) : ExchangeIdentityError("$path is empty.")

    /**
     * The component at [path] holds an unpaired surrogate, so it isn't a string of Unicode scalars.
     */
    data class NonScalarComponent(val path: String) : ExchangeIdentityError("$path holds an unpaired surrogate.")

    /**
     * The part index at [path] is not a canonical unsigned decimal: it has a sign, whitespace or a leading zero.
     */
    data class NonCanonicalPartIndex(val path: String) :
        ExchangeIdentityError("$path is not a canonical unsigned decimal.")

    /**
     * The digest at [path] is not a SHA-256 digest in base64url without padding.
     */
    data class NonCanonicalDigest(val path: String) :
        ExchangeIdentityError("$path is not an unpadded base64url SHA-256 digest.")

    /**
     * The `node-role` component at [path] is not a role of its snapshot kind.
     */
    data class InvalidNodeRole(val path: String) : ExchangeIdentityError("$path is not a node role of its kind.")

    /**
     * A resource can't enter a content or record-content digest, for the [reason] given.
     */
    data class InvalidDigestContent(val reason: String) : ExchangeIdentityError(reason)

    /**
     * [value] of [field] is not a lowercase code token: a letter followed by lowercase letters, digits and hyphens.
     */
    data class InvalidCodeToken(val field: String, val value: String) :
        ExchangeIdentityError("$value of $field is not a lowercase code token.")

    /**
     * [adapterId] names a provider, whose records are minted under the provider identity kinds.
     */
    data class ProviderKindRequired(val adapterId: String) :
        ExchangeIdentityError("$adapterId is a provider; use the provider identity kinds.")

    /**
     * The identity [kind] received [actual] components instead of [expected].
     */
    data class InvalidComponentCount(val kind: String, val expected: Int, val actual: Int) :
        ExchangeIdentityError("$kind takes $expected components, not $actual.")

    /**
     * [value] is not a canonical unsigned decimal.
     */
    data class InvalidNonnegativeDecimal(val value: String) :
        ExchangeIdentityError("$value is not a canonical unsigned decimal.")

    /**
     * [value] is not a positive canonical decimal.
     */
    data class InvalidKeyEpoch(val value: String) : ExchangeIdentityError("$value is not a valid key epoch.")

    /**
     * [value] is not a canonical opaque record version or retraction key.
     */
    data class InvalidRecordVersion(val value: String) :
        ExchangeIdentityError("$value is not a canonical opaque identity.")
}
