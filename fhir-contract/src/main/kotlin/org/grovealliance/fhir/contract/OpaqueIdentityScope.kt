//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.ExchangeHashing
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * The deployment-owned, key-epoch-specific scope that mints every opaque identity: HMAC-SHA-256
 * under [key] over the length-framed protocol label, the identity kind and its components, written
 * `v0:<keyId>:<epoch>:<digest>` under the kind's system in [systems].
 *
 * Grove publishes no global namespace: the systems derive from the deployment's own root, so the
 * same clear source identity is never linkable across unrelated studies. The key never leaves the
 * scope; [toString] names the key id and epoch only.
 */
@Suppress("detekt:TooManyFunctions")
class OpaqueIdentityScope private constructor(
    val systems: DeploymentIdentifierSystems,
    val keyId: String,
    val epoch: KeyEpoch,
    private val key: SecretKey,
) {
    private val hashing = ExchangeHashing()

    override fun toString(): String = "OpaqueIdentityScope(keyId=$keyId, epoch=$epoch)"

    /**
     * The identity of one native source record, from which its output and artifact identities are minted.
     */
    fun sourceRecord(
        adapterId: String,
        sourceType: String,
        repositoryScope: BusinessIdentifier,
        nativeRecordId: String,
    ): Result<SourceRecordIdentity> = SourceRecordIdentity.create(
        scope = this,
        components = listOf(adapterId, sourceType, repositoryScope.system.rawValue, repositoryScope.value, nativeRecordId),
        isProviderRecord = false,
    )

    /**
     * The identity of one provider-owned record; its outputs and artifacts mint under the provider kinds.
     */
    @InternalGroveFhirApi
    fun providerRecord(
        providerCode: ProviderCode,
        sourceType: String,
        providerScope: BusinessIdentifier,
        nativeRecordId: String,
    ): Result<SourceRecordIdentity> = SourceRecordIdentity.create(
        scope = this,
        components = listOf(providerCode.rawValue, sourceType, providerScope.system.rawValue, providerScope.value, nativeRecordId),
        isProviderRecord = true,
    )

    /**
     * The logical record [writerApplication] assigned, per [subject], so one writer's record id never
     * links the accounts that share a key.
     */
    @InternalGroveFhirApi
    fun writerRecord(
        subject: BusinessIdentifier,
        writerApplication: BusinessIdentifier,
        writerRecordId: String,
    ): Result<RoledIdentifier> = identifier(
        kind = OpaqueIdentityKind.WRITER_RECORD,
        components = listOf(
            subject.system.rawValue,
            subject.value,
            writerApplication.system.rawValue,
            writerApplication.value,
            writerRecordId,
        ),
    )

    /**
     * The key of one version of [sourceRecord], whose content has the record-content digest [contentDigest].
     */
    @InternalGroveFhirApi
    fun recordVersion(
        sourceRecord: BusinessIdentifier,
        contentDigest: String,
    ): Result<RoledIdentifier> = identifier(
        kind = OpaqueIdentityKind.RECORD_VERSION,
        components = listOf(sourceRecord.system.rawValue, sourceRecord.value, contentDigest),
    )

    /**
     * The one retraction key of [sourceRecord].
     */
    @InternalGroveFhirApi
    fun recordRetraction(sourceRecord: BusinessIdentifier): Result<RoledIdentifier> = identifier(
        kind = OpaqueIdentityKind.RECORD_RETRACTION,
        components = listOf(sourceRecord.system.rawValue, sourceRecord.value),
    )

    /**
     * Source-owned context that more than one record references, without disclosing its native id.
     */
    @InternalGroveFhirApi
    fun sourceContext(
        adapterId: String,
        contextType: String,
        repositoryScope: BusinessIdentifier,
        nativeContextId: String,
    ): Result<RoledIdentifier> = codeToken(value = contextType, field = "source-context.context-type").mapCatchingResult {
        identifier(
            kind = OpaqueIdentityKind.SOURCE_CONTEXT,
            components = listOf(adapterId, contextType, repositoryScope.system.rawValue, repositoryScope.value, nativeContextId),
        )
    }

    /**
     * One physical acquisition unit for [subject], by the stable per-unit token [tokenScope] assigned.
     */
    @InternalGroveFhirApi
    fun recordingDevice(
        adapterId: String,
        subject: BusinessIdentifier,
        tokenScope: String,
        stableUnitToken: String,
    ): Result<RoledIdentifier> = identifier(
        kind = OpaqueIdentityKind.RECORDING_DEVICE,
        components = listOf(adapterId, subject.system.rawValue, subject.value, tokenScope, stableUnitToken),
    )

    /**
     * The key of [node] in [role] for [subject], by its final content: mint it after the body is final
     * and after every node it references, so the same content gives the same key in every Bundle.
     */
    @InternalGroveFhirApi
    fun contentSnapshot(
        node: FhirResource,
        subject: BusinessIdentifier,
        role: NodeRole,
    ): Result<RoledIdentifier> = node.toJson().contentDigest().fold(
        onSuccess = { digest ->
            contentSnapshot(
                subject = subject,
                role = role,
                contentDigest = digest,
            )
        },
        onFailure = { Result.failure(it) },
    )

    /**
     * The key of one immutable shared node in [role] for [subject], by its final content's [contentDigest].
     */
    @InternalGroveFhirApi
    fun contentSnapshot(
        subject: BusinessIdentifier,
        role: NodeRole,
        contentDigest: String,
    ): Result<RoledIdentifier> = identifier(
        kind = role.kind,
        components = listOf(subject.system.rawValue, subject.value, role.rawValue, contentDigest),
    )

    /**
     * The typed opaque identifier of [kind] over [components], after checking every component rule.
     */
    @InternalGroveFhirApi
    fun identifier(kind: OpaqueIdentityKind, components: List<String>): Result<RoledIdentifier> {
        componentError(kind = kind, components = components)?.let { return Result.failure(it) }
        val preimage = hashing.lengthFramedUtf8(fields = listOf(ExchangeContract.IDENTITY_DOMAIN, kind.rawValue) + components)
        val digest = hashing.base64Url(bytes = hashing.hmacSha256(key = key, data = preimage))
        return Result.success(
            RoledIdentifier(
                identifier = BusinessIdentifier(
                    system = systems[kind],
                    value = "v0:$keyId:$epoch:$digest",
                ),
                role = kind.identifierRole,
            ),
        )
    }

    internal fun output(
        record: List<String>,
        kind: OpaqueIdentityKind,
        role: String,
        discriminator: String,
    ): Result<RoledIdentifier> = codeToken(value = role, field = "${kind.rawValue}.output-role").mapCatchingResult {
        identifier(
            kind = kind,
            components = record + listOf(role, discriminator),
        )
    }

    internal fun artifact(
        record: List<String>,
        kind: OpaqueIdentityKind,
        formatCode: String,
        partIndex: CanonicalNonnegativeDecimal,
    ): Result<RoledIdentifier> = identifier(
        kind = kind,
        components = record + listOf(formatCode, partIndex.rawValue),
    )

    @Suppress("detekt:ReturnCount")
    private fun componentError(kind: OpaqueIdentityKind, components: List<String>): ExchangeIdentityError? {
        if (components.size != kind.componentNames.size) {
            return ExchangeIdentityError.InvalidComponentCount(
                kind = kind.rawValue,
                expected = kind.componentNames.size,
                actual = components.size,
            )
        }
        kind.componentNames.zip(components).forEach { (name, component) ->
            val path = "${kind.rawValue}.$name"
            when {
                component.isEmpty() -> return ExchangeIdentityError.EmptyComponent(path = path)
                !component.isScalarString() -> return ExchangeIdentityError.NonScalarComponent(path = path)
                name in OpaqueIdentityKind.unsignedDecimalComponents && !CanonicalNonnegativeDecimal.isCanonical(value = component) ->
                    return ExchangeIdentityError.NonCanonicalPartIndex(path = path)
                name in OpaqueIdentityKind.digestComponents && !isUnpaddedBase64UrlDigest(text = component) ->
                    return ExchangeIdentityError.NonCanonicalDigest(path = path)
                name == "node-role" && NodeRole.of(kind = kind, rawValue = component) == null ->
                    return ExchangeIdentityError.InvalidNodeRole(path = path)
            }
        }
        val isGenericSourceKind = kind in setOf(
            OpaqueIdentityKind.SOURCE_RECORD,
            OpaqueIdentityKind.SOURCE_OUTPUT,
            OpaqueIdentityKind.SOURCE_ARTIFACT,
        )
        return if (isGenericSourceKind && ProviderCode.fromRawValue(rawValue = components.first()) != null) {
            ExchangeIdentityError.ProviderKindRequired(adapterId = components.first())
        } else {
            null
        }
    }

    /**
     * Whether [value] is a lowercase code token; an empty one is left to the component checks, which
     * report it with its path.
     */
    private fun codeToken(value: String, field: String): Result<Unit> {
        val isToken = value.isEmpty() ||
            (value[0] in 'a'..'z' && value.all { it in 'a'..'z' || it in '0'..'9' || it == '-' })
        return if (isToken) Result.success(Unit) else Result.failure(ExchangeIdentityError.InvalidCodeToken(field = field, value = value))
    }

    private fun String.isScalarString(): Boolean {
        var index = 0
        while (index < length) {
            val character = this[index]
            when {
                character.isHighSurrogate() -> {
                    if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                    index += 2
                }
                character.isLowSurrogate() -> return false
                else -> index += 1
            }
        }
        return true
    }

    private inline fun <T> Result<Unit>.mapCatchingResult(block: () -> Result<T>): Result<T> =
        fold(onSuccess = { block() }, onFailure = { Result.failure(it) })

    companion object {
        private const val MINIMUM_KEY_BYTES = 32
        private const val KEY_ID_FINGERPRINT_LENGTH = 8
        private const val DIGEST_LENGTH = 43
        private const val OPAQUE_VALUE_PARTS = 4
        private const val HMAC_SHA256 = "HmacSHA256"

        private val publishedConformanceKey = ByteArray(MINIMUM_KEY_BYTES) { it.toByte() }

        /**
         * A scope over [systems] that mints under [key].
         *
         * [keyId] is wire-visible, so it is restricted to ASCII letters, digits, `-`, `.` and `_`.
         * A key whose bytes can be read must have at least 32 of them and must not be the published
         * conformance key; a key held in hardware, such as an Android Keystore HMAC key, can't be
         * inspected and is taken as it is.
         */
        fun create(
            systems: DeploymentIdentifierSystems,
            keyId: String,
            epoch: KeyEpoch,
            key: SecretKey,
        ): Result<OpaqueIdentityScope> = create(
            systems = systems,
            keyId = keyId,
            epoch = epoch,
            key = key,
            permitsPublishedConformanceKey = false,
        )

        /**
         * A scope over the systems [DeploymentIdentifierSystems.derived] forms under [root].
         */
        fun create(
            root: IdentifierSystem,
            keyId: String,
            epoch: KeyEpoch,
            key: SecretKey,
        ): Result<OpaqueIdentityScope> = DeploymentIdentifierSystems.derived(
            root = root,
            keyId = keyId,
            epoch = epoch,
        ).fold(
            onSuccess = { systems ->
                create(
                    systems = systems,
                    keyId = keyId,
                    epoch = epoch,
                    key = key,
                )
            },
            onFailure = { Result.failure(it) },
        )

        /**
         * The scope of the protocol's test vectors, under the published conformance key that every
         * other scope refuses.
         */
        @InternalGroveFhirApi
        fun forConformanceTesting(
            systems: DeploymentIdentifierSystems,
            keyId: String,
            epoch: KeyEpoch,
        ): Result<OpaqueIdentityScope> = create(
            systems = systems,
            keyId = keyId,
            epoch = epoch,
            key = SecretKeySpec(publishedConformanceKey, HMAC_SHA256),
            permitsPublishedConformanceKey = true,
        )

        /**
         * The per-subject fingerprint of [key]: HMAC-SHA-256 under it, base64url without padding, over
         * the length-framed fingerprint label, [subject]'s system and its value. It is per subject, so a
         * key several subjects share never puts one key id into the identifiers of all of them.
         */
        fun keyFingerprint(key: SecretKey, subject: BusinessIdentifier): Result<String> {
            keyLengthError(key = key)?.let { return Result.failure(it) }
            val hashing = ExchangeHashing()
            val preimage = hashing.lengthFramedUtf8(
                fields = listOf(ExchangeContract.KEY_FINGERPRINT_DOMAIN, subject.system.rawValue, subject.value),
            )
            return Result.success(hashing.base64Url(bytes = hashing.hmacSha256(key = key, data = preimage)))
        }

        /**
         * The protocol's recommended key id: [prefix], then the first 8 characters of [keyFingerprint].
         * A key id names one key for the deployment's lifetime, so a re-minted key gets a new one.
         */
        fun recommendedKeyId(prefix: String, key: SecretKey, subject: BusinessIdentifier): Result<String> =
            keyFingerprint(key = key, subject = subject).fold(
                onSuccess = { fingerprint ->
                    val keyId = prefix + fingerprint.take(KEY_ID_FINGERPRINT_LENGTH)
                    if (isValidKeyId(keyId = keyId)) {
                        Result.success(keyId)
                    } else {
                        Result.failure(ExchangeIdentityError.InvalidKeyId(keyId = keyId))
                    }
                },
                onFailure = { Result.failure(it) },
            )

        /**
         * Whether [keyId] is a non-empty token of ASCII letters, digits, `-`, `.` and `_`.
         */
        fun isValidKeyId(keyId: String): Boolean =
            keyId.isNotEmpty() && keyId.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '.' || it == '_' }

        /**
         * Whether [value] has the wire form of an opaque identity: `v0:<keyId>:<epoch>:<digest>` with a
         * valid key id, a positive canonical epoch and an unpadded base64url SHA-256 digest.
         */
        @InternalGroveFhirApi
        fun isCanonicalOpaqueValue(value: String): Boolean {
            val parts = value.split(':')
            return parts.size == OPAQUE_VALUE_PARTS &&
                parts[0] == "v0" &&
                isValidKeyId(keyId = parts[1]) &&
                KeyEpoch.parse(text = parts[2]).isSuccess &&
                isUnpaddedBase64UrlDigest(text = parts[3])
        }

        /**
         * Whether [text] has the form of a SHA-256 digest in base64url without padding: 43 characters
         * of `[A-Za-z0-9_-]`.
         */
        @InternalGroveFhirApi
        fun isUnpaddedBase64UrlDigest(text: String): Boolean =
            text.length == DIGEST_LENGTH && text.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

        private fun create(
            systems: DeploymentIdentifierSystems,
            keyId: String,
            epoch: KeyEpoch,
            key: SecretKey,
            permitsPublishedConformanceKey: Boolean,
        ): Result<OpaqueIdentityScope> {
            val error = when {
                !isValidKeyId(keyId = keyId) -> ExchangeIdentityError.InvalidKeyId(keyId = keyId)
                else -> keyLengthError(key = key)
                    ?: ExchangeIdentityError.PublishedConformanceKeyProhibited.takeIf {
                        !permitsPublishedConformanceKey && key.encoded?.contentEquals(publishedConformanceKey) == true
                    }
            }
            return error?.let { Result.failure(it) } ?: Result.success(
                OpaqueIdentityScope(
                    systems = systems,
                    keyId = keyId,
                    epoch = epoch,
                    key = key,
                ),
            )
        }

        private fun keyLengthError(key: SecretKey): ExchangeIdentityError? =
            key.encoded?.size?.takeIf { it < MINIMUM_KEY_BYTES }?.let { ExchangeIdentityError.KeyTooShort(actualBytes = it) }
    }
}
