//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The identifier systems a deployment owns for its opaque identities, one per identity kind, and
 * the one its clear device identifiers travel under.
 *
 * [derived] forms them as the exchange protocol recommends; [OpaqueIdentityScope] holds them with
 * the key.
 *
 * @property deviceLocalIdentifier the system of the clear identifier a source states for a recording
 * device; its values are the source's own, so it names no key id or epoch
 */
@Suppress("detekt:LongParameterList")
@ConsistentCopyVisibility
data class DeploymentIdentifierSystems @InternalGroveFhirApi constructor(
    val sourceRecord: IdentifierSystem,
    val sourceOutput: IdentifierSystem,
    val writerRecord: IdentifierSystem,
    val providerRecord: IdentifierSystem,
    val providerOutput: IdentifierSystem,
    val sourceArtifact: IdentifierSystem,
    val providerArtifact: IdentifierSystem,
    val sourceContext: IdentifierSystem,
    val recordingDevice: IdentifierSystem,
    val deviceSnapshot: IdentifierSystem,
    val contextSnapshot: IdentifierSystem,
    val recordVersion: IdentifierSystem,
    val recordRetraction: IdentifierSystem,
    val deviceLocalIdentifier: IdentifierSystem,
) {

    /**
     * The system of the opaque identities of [kind].
     */
    @InternalGroveFhirApi
    operator fun get(kind: OpaqueIdentityKind): IdentifierSystem = when (kind) {
        OpaqueIdentityKind.SOURCE_RECORD -> sourceRecord
        OpaqueIdentityKind.SOURCE_OUTPUT -> sourceOutput
        OpaqueIdentityKind.WRITER_RECORD -> writerRecord
        OpaqueIdentityKind.PROVIDER_RECORD -> providerRecord
        OpaqueIdentityKind.PROVIDER_OUTPUT -> providerOutput
        OpaqueIdentityKind.SOURCE_ARTIFACT -> sourceArtifact
        OpaqueIdentityKind.PROVIDER_ARTIFACT -> providerArtifact
        OpaqueIdentityKind.SOURCE_CONTEXT -> sourceContext
        OpaqueIdentityKind.RECORDING_DEVICE -> recordingDevice
        OpaqueIdentityKind.DEVICE_SNAPSHOT -> deviceSnapshot
        OpaqueIdentityKind.CONTEXT_SNAPSHOT -> contextSnapshot
        OpaqueIdentityKind.RECORD_VERSION -> recordVersion
        OpaqueIdentityKind.RECORD_RETRACTION -> recordRetraction
    }

    /**
     * Every system the deployment reserves for its opaque identities, one per kind.
     */
    @InternalGroveFhirApi
    val all: List<IdentifierSystem> get() = OpaqueIdentityKind.entries.map { get(kind = it) }

    companion object {

        /**
         * The systems in the protocol's recommended form under [root]:
         * `<root>/NamingSystem/grove-<identity-kind>-v0/<keyId>/<epoch>`. A rotated key takes a new
         * key id or epoch, and with them new systems.
         *
         * @return [ExchangeIdentityError.InvalidKeyId] for a key id the systems can't carry, or
         * [ExchangeIdentityError.InvalidDeploymentRoot] for a root no system can be formed under
         */
        fun derived(
            root: IdentifierSystem,
            keyId: String,
            epoch: KeyEpoch,
        ): Result<DeploymentIdentifierSystems> {
            if (!OpaqueIdentityScope.isValidKeyId(keyId = keyId)) {
                return Result.failure(ExchangeIdentityError.InvalidKeyId(keyId = keyId))
            }
            val deploymentRoot = root.rawValue.removeSuffix("/")
            val opaqueSystems = OpaqueIdentityKind.entries.map { kind ->
                ExchangeContract.OPAQUE_IDENTITY_SYSTEM_FORM
                    .replace("<deployment-root>", deploymentRoot)
                    .replace("<identity-kind>", kind.rawValue)
                    .replace("<key-id>", keyId)
                    .replace("<epoch>", epoch.rawValue)
            }
            val systems = (opaqueSystems +
                "$deploymentRoot/NamingSystem/grove-device-local-identifier")
                .map { IdentifierSystem.create(rawValue = it).getOrNull() }
            if (systems.any { it == null }) {
                return Result.failure(ExchangeIdentityError.InvalidDeploymentRoot(root = root.rawValue))
            }
            val byKind = systems.filterNotNull()
            return Result.success(
                DeploymentIdentifierSystems(
                    sourceRecord = byKind[OpaqueIdentityKind.SOURCE_RECORD.ordinal],
                    sourceOutput = byKind[OpaqueIdentityKind.SOURCE_OUTPUT.ordinal],
                    writerRecord = byKind[OpaqueIdentityKind.WRITER_RECORD.ordinal],
                    providerRecord = byKind[OpaqueIdentityKind.PROVIDER_RECORD.ordinal],
                    providerOutput = byKind[OpaqueIdentityKind.PROVIDER_OUTPUT.ordinal],
                    sourceArtifact = byKind[OpaqueIdentityKind.SOURCE_ARTIFACT.ordinal],
                    providerArtifact = byKind[OpaqueIdentityKind.PROVIDER_ARTIFACT.ordinal],
                    sourceContext = byKind[OpaqueIdentityKind.SOURCE_CONTEXT.ordinal],
                    recordingDevice = byKind[OpaqueIdentityKind.RECORDING_DEVICE.ordinal],
                    deviceSnapshot = byKind[OpaqueIdentityKind.DEVICE_SNAPSHOT.ordinal],
                    contextSnapshot = byKind[OpaqueIdentityKind.CONTEXT_SNAPSHOT.ordinal],
                    recordVersion = byKind[OpaqueIdentityKind.RECORD_VERSION.ordinal],
                    recordRetraction = byKind[OpaqueIdentityKind.RECORD_RETRACTION.ordinal],
                    deviceLocalIdentifier = byKind.last(),
                ),
            )
        }
    }
}
