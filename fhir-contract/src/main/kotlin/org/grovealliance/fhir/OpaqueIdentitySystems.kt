//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import java.net.URI

/** The ten deployment-owned identifier systems, one per opaque identity kind, key id and epoch. */
public class OpaqueIdentitySystems(systems: Map<OpaqueIdentityKind, IdentifierSystem>) {
    private val systems: Map<OpaqueIdentityKind, IdentifierSystem> = systems.toMap()

    init {
        require(this.systems.keys == OpaqueIdentityKind.entries.toSet()) {
            "Every opaque identity kind requires exactly one identifier system."
        }
        require(this.systems.values.toSet().size == this.systems.size) {
            "One identifier system cannot serve two opaque identity kinds."
        }
    }

    /** The system minted for one identity kind. */
    public operator fun get(kind: OpaqueIdentityKind): IdentifierSystem = systems.getValue(kind)

    /** Every system, keyed by its identity kind. */
    public val all: Map<OpaqueIdentityKind, IdentifierSystem>
        get() = systems

    public val sourceRecord: IdentifierSystem get() = get(OpaqueIdentityKind.SOURCE_RECORD)
    public val sourceOutput: IdentifierSystem get() = get(OpaqueIdentityKind.SOURCE_OUTPUT)
    public val writerRecord: IdentifierSystem get() = get(OpaqueIdentityKind.WRITER_RECORD)
    public val providerRecord: IdentifierSystem get() = get(OpaqueIdentityKind.PROVIDER_RECORD)
    public val providerOutput: IdentifierSystem get() = get(OpaqueIdentityKind.PROVIDER_OUTPUT)
    public val sourceArtifact: IdentifierSystem get() = get(OpaqueIdentityKind.SOURCE_ARTIFACT)
    public val providerArtifact: IdentifierSystem get() = get(OpaqueIdentityKind.PROVIDER_ARTIFACT)
    public val sourceContext: IdentifierSystem get() = get(OpaqueIdentityKind.SOURCE_CONTEXT)
    public val recordingDevice: IdentifierSystem get() = get(OpaqueIdentityKind.RECORDING_DEVICE)
    public val deviceSnapshot: IdentifierSystem get() = get(OpaqueIdentityKind.DEVICE_SNAPSHOT)

    override fun equals(other: Any?): Boolean = other is OpaqueIdentitySystems && systems == other.systems

    override fun hashCode(): Int = systems.hashCode()

    override fun toString(): String = "OpaqueIdentitySystems($systems)"
}

/** All twelve deployment-owned identifier systems: the ten opaque kinds, the event and the entry node. */
public data class DeploymentIdentifierSystems(
    public val opaque: OpaqueIdentitySystems,
    public val event: IdentifierSystem,
    public val entryNode: IdentifierSystem,
) {
    init {
        require(event != entryNode) { "The event and entry-node systems must be distinct." }
        require(opaque.all.values.none { it == event || it == entryNode }) {
            "The event and entry-node systems must not reuse an opaque identity system."
        }
    }

    /** All twelve systems. */
    public val all: Set<IdentifierSystem>
        get() = opaque.all.values.toSet() + event + entryNode

    public companion object {
        /** Every system in the protocol's recommended form under one deployment root, key id and epoch. */
        public fun derived(root: IdentifierSystem, keyId: String, epoch: EventSequence): DeploymentIdentifierSystems {
            require(ExchangeProtocol.token.matches(keyId)) { "A key id uses only URI-safe unreserved characters." }
            val uri = URI(root.value)
            require(!root.value.endsWith('/') && uri.rawQuery == null && uri.rawFragment == null) {
                "A deployment root has no trailing slash, query or fragment."
            }
            fun form(template: String, kind: String? = null): IdentifierSystem = IdentifierSystem(
                template
                    .replace(ExchangeContract.DEPLOYMENT_ROOT_PLACEHOLDER, root.value)
                    .replace(ExchangeContract.IDENTITY_KIND_PLACEHOLDER, kind.orEmpty())
                    .replace(ExchangeContract.KEY_ID_PLACEHOLDER, keyId)
                    .replace(ExchangeContract.EPOCH_PLACEHOLDER, epoch.value),
            )
            return DeploymentIdentifierSystems(
                opaque = OpaqueIdentitySystems(
                    OpaqueIdentityKind.entries.associateWith { kind ->
                        form(ExchangeContract.OPAQUE_IDENTITY_SYSTEM_FORM, kind.code)
                    },
                ),
                event = form(ExchangeContract.EVENT_IDENTITY_SYSTEM_FORM),
                entryNode = form(ExchangeContract.ENTRY_NODE_IDENTITY_SYSTEM_FORM),
            )
        }
    }
}
