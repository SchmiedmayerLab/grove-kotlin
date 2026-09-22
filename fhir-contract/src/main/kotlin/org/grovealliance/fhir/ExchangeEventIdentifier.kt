//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import java.util.UUID

/** The clear `e0:<producer-instance>:<sequence>` identity of one exchange event. */
public data class ExchangeEventIdentifier(
    public val system: IdentifierSystem,
    public val producerInstance: UUID,
    public val sequence: EventSequence,
) {
    init {
        require(ExchangeProtocol.producerInstance.matches(producerInstance.toString())) {
            "The producer instance must be an RFC 4122 UUID."
        }
    }

    /** The event identifier with its `event` role coding. */
    public val identifier: RoledIdentifier = RoledIdentifier(
        BusinessIdentifier(
            system,
            "${ExchangeContract.EVENT_IDENTITY_PREFIX}:$producerInstance:${sequence.value}",
        ),
        GroveIdentifierRole.EVENT,
    )

    public companion object {
        /** Reads a stored event identifier, or throws [ExchangeIdentityException] when it is not canonical. */
        public fun from(identifier: BusinessIdentifier): ExchangeEventIdentifier {
            val match = ExchangeProtocol.eventIdentityValue.matchEntire(identifier.value)
                ?: throw ExchangeIdentityException(ExchangeIdentityError.MalformedIdentifier("Bundle.identifier.value"))
            return ExchangeEventIdentifier(
                identifier.system,
                UUID.fromString(match.groupValues[1]),
                EventSequence(match.groupValues[2]),
            )
        }
    }
}

/** The deterministic `n0:<node-role>:<ordinal>:<digest>` key of an entry without a business identifier. */
public data class EntryNodeKey(
    public val system: IdentifierSystem,
    public val event: ExchangeEventIdentifier,
    public val nodeRole: String,
    public val ordinal: Long,
) {
    init {
        require(ExchangeProtocol.lowercaseCode.matches(nodeRole)) { "An entry-node role is a lowercase code token." }
        require(ordinal >= 0) { "An entry-node ordinal is zero-based." }
    }

    /** The entry-node identifier with its `entry-node` role coding. */
    public val identifier: RoledIdentifier = RoledIdentifier(
        BusinessIdentifier(system, value(event.identifier.identifier, nodeRole, ordinal.toString())),
        GroveIdentifierRole.ENTRY_NODE,
    )

    internal companion object {
        fun value(event: BusinessIdentifier, nodeRole: String, ordinal: String): String {
            val digest = ExchangeProtocol.sha256(
                ExchangeProtocol.frameFields(
                    listOf(ExchangeContract.ENTRY_NODE_DOMAIN, event.system.value, event.value, nodeRole, ordinal),
                ),
            )
            return "${ExchangeContract.ENTRY_NODE_IDENTITY_PREFIX}:$nodeRole:$ordinal:${ExchangeProtocol.base64Url(digest)}"
        }
    }
}
