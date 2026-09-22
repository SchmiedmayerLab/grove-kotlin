//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import javax.crypto.Mac
import javax.crypto.SecretKey

/** The resource kind a device-snapshot identity names; participation roles never enter identity. */
public enum class DeviceSnapshotRole(public val code: String) {
    APPLICATION("application"),
    HOST("host"),
    RECORDING_DEVICE("recording-device"),
}

/**
 * Mints every opaque identity of one deployment scope, key id and key epoch.
 *
 * The scope holds the HMAC key, the deployment's twelve identifier systems and its epoch; rotating
 * the key or the epoch creates a new identity space and never rewrites an emitted identifier in place.
 */
public class OpaqueIdentityScope private constructor(
    public val systems: DeploymentIdentifierSystems,
    public val keyId: String,
    public val epoch: EventSequence,
    private val key: SecretKey,
    allowConformanceKey: Boolean,
) {
    public constructor(
        systems: DeploymentIdentifierSystems,
        keyId: String,
        epoch: EventSequence,
        key: SecretKey,
    ) : this(systems, keyId, epoch, key, false)

    init {
        require(ExchangeProtocol.token.matches(keyId)) { "A key id uses only URI-safe unreserved characters." }
        val encoded = key.encoded
        // A Keystore-backed key exposes no material; only an exportable key can be measured here.
        if (encoded != null) {
            require(encoded.size >= ExchangeContract.OPAQUE_IDENTITY_MINIMUM_KEY_BYTES) {
                "An opaque identity key must contain at least " +
                    "${ExchangeContract.OPAQUE_IDENTITY_MINIMUM_KEY_BYTES} bytes of secret material."
            }
            require(allowConformanceKey || !encoded.contentEquals(PUBLIC_CONFORMANCE_KEY)) {
                "The published Grove conformance key is prohibited in production identity configuration."
            }
        }
    }

    /** One opaque identity of any kind from its typed components in catalog order. */
    public fun mint(kind: OpaqueIdentityKind, components: List<String>): RoledIdentifier {
        require(components.size == kind.componentCount) {
            "${kind.code} requires exactly ${kind.componentCount} ordered components."
        }
        components.forEachIndexed { index, component ->
            val field = "${kind.code}.${kind.components[index]}"
            if (!ExchangeProtocol.isScalarText(component)) {
                throw ExchangeIdentityException(ExchangeIdentityError.NonScalarText(field))
            }
            if (component.isEmpty()) throw ExchangeIdentityException(ExchangeIdentityError.EmptyComponent(field))
        }
        requireDomainSeparation(kind, components.first())
        val preimage = ExchangeProtocol.frameFields(
            listOf(ExchangeContract.OPAQUE_IDENTITY_DOMAIN, kind.code) + components,
        )
        val digest = Mac.getInstance(HMAC_SHA_256).run {
            init(key)
            doFinal(preimage)
        }
        val value = "${ExchangeContract.OPAQUE_IDENTITY_PREFIX}:$keyId:${epoch.value}:${ExchangeProtocol.base64Url(digest)}"
        return RoledIdentifier(BusinessIdentifier(systems.opaque[kind], value), kind.identifierRole)
    }

    public fun sourceRecord(
        adapterId: String,
        sourceType: String,
        repositoryScope: BusinessIdentifier,
        nativeRecordId: String,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.SOURCE_RECORD,
        listOf(adapterId, sourceType, repositoryScope.system.value, repositoryScope.value, nativeRecordId),
    )

    public fun sourceOutput(
        adapterId: String,
        sourceType: String,
        repositoryScope: BusinessIdentifier,
        nativeRecordId: String,
        outputRole: String,
        outputDiscriminator: String,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.SOURCE_OUTPUT,
        listOf(
            adapterId,
            sourceType,
            repositoryScope.system.value,
            repositoryScope.value,
            nativeRecordId,
            outputRole,
            outputDiscriminator,
        ),
    )

    public fun writerRecord(writerApplication: BusinessIdentifier, writerRecordId: String): RoledIdentifier = mint(
        OpaqueIdentityKind.WRITER_RECORD,
        listOf(writerApplication.system.value, writerApplication.value, writerRecordId),
    )

    public fun providerRecord(
        providerCode: String,
        sourceType: String,
        providerScope: BusinessIdentifier,
        nativeRecordId: String,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.PROVIDER_RECORD,
        listOf(providerCode, sourceType, providerScope.system.value, providerScope.value, nativeRecordId),
    )

    public fun providerOutput(
        providerCode: String,
        sourceType: String,
        providerScope: BusinessIdentifier,
        nativeRecordId: String,
        outputRole: String,
        outputDiscriminator: String,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.PROVIDER_OUTPUT,
        listOf(
            providerCode,
            sourceType,
            providerScope.system.value,
            providerScope.value,
            nativeRecordId,
            outputRole,
            outputDiscriminator,
        ),
    )

    public fun sourceArtifact(
        adapterId: String,
        sourceType: String,
        repositoryScope: BusinessIdentifier,
        nativeRecordId: String,
        formatCode: String,
        partIndex: Long,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.SOURCE_ARTIFACT,
        listOf(
            adapterId,
            sourceType,
            repositoryScope.system.value,
            repositoryScope.value,
            nativeRecordId,
            formatCode,
            partIndex.toString(),
        ),
    )

    public fun providerArtifact(
        providerCode: String,
        sourceType: String,
        providerScope: BusinessIdentifier,
        nativeRecordId: String,
        formatCode: String,
        partIndex: Long,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.PROVIDER_ARTIFACT,
        listOf(
            providerCode,
            sourceType,
            providerScope.system.value,
            providerScope.value,
            nativeRecordId,
            formatCode,
            partIndex.toString(),
        ),
    )

    public fun sourceContext(
        adapterId: String,
        contextType: String,
        repositoryScope: BusinessIdentifier,
        nativeContextId: String,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.SOURCE_CONTEXT,
        listOf(adapterId, contextType, repositoryScope.system.value, repositoryScope.value, nativeContextId),
    )

    /** A physical device instance requires a governed per-unit token; model and manufacturer are not identity. */
    public fun recordingDevice(
        adapterId: String,
        subject: BusinessIdentifier,
        stableUnitToken: String,
    ): RoledIdentifier =
        mint(
            OpaqueIdentityKind.RECORDING_DEVICE,
            listOf(adapterId, subject.system.value, subject.value, stableUnitToken),
        )

    /** Event-bound immutable application, host or recorder facts; never a mutable long-lived identity. */
    public fun deviceSnapshot(
        event: ExchangeEventIdentifier,
        role: DeviceSnapshotRole,
        sourceDeviceToken: String,
    ): RoledIdentifier = mint(
        OpaqueIdentityKind.DEVICE_SNAPSHOT,
        listOf(event.system.value, event.identifier.identifier.value, role.code, sourceDeviceToken),
    )

    override fun toString(): String = "OpaqueIdentityScope(keyId=$keyId, epoch=$epoch)"

    private fun requireDomainSeparation(kind: OpaqueIdentityKind, firstComponent: String) {
        when (kind) {
            OpaqueIdentityKind.PROVIDER_RECORD,
            OpaqueIdentityKind.PROVIDER_OUTPUT,
            OpaqueIdentityKind.PROVIDER_ARTIFACT,
            -> require(firstComponent in ExchangeContract.providerCodes) {
                "${kind.code} requires an exact catalog provider code as its first component."
            }

            OpaqueIdentityKind.SOURCE_RECORD,
            OpaqueIdentityKind.SOURCE_OUTPUT,
            OpaqueIdentityKind.SOURCE_ARTIFACT,
            -> require(firstComponent !in ExchangeContract.providerCodes) {
                "Provider coordinates require the matching provider-record, provider-output or provider-artifact kind."
            }

            OpaqueIdentityKind.WRITER_RECORD,
            OpaqueIdentityKind.SOURCE_CONTEXT,
            OpaqueIdentityKind.RECORDING_DEVICE,
            OpaqueIdentityKind.DEVICE_SNAPSHOT,
            -> Unit
        }
    }

    public companion object {
        private const val HMAC_SHA_256 = "HmacSHA256"
        private val PUBLIC_CONFORMANCE_KEY =
            ByteArray(ExchangeContract.OPAQUE_IDENTITY_MINIMUM_KEY_BYTES) { it.toByte() }

        /** A scope that admits the published conformance key so the normative vectors can execute. */
        @ConformanceTestingApi
        public fun forConformanceTesting(
            systems: DeploymentIdentifierSystems,
            keyId: String,
            epoch: EventSequence,
            key: SecretKey,
        ): OpaqueIdentityScope = OpaqueIdentityScope(systems, keyId, epoch, key, true)
    }
}
