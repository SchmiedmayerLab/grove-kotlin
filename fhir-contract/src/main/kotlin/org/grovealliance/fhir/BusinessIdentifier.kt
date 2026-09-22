//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Identifier

/** A complete `Identifier.system` and `Identifier.value` pair; never a repository-assigned resource id. */
public class BusinessIdentifier(
    public val system: IdentifierSystem,
    public val value: String,
) : Comparable<BusinessIdentifier> {
    init {
        require(ExchangeProtocol.isScalarText(value)) { "Identifier.value contains an unpaired UTF-16 surrogate." }
        require(value.isNotBlank()) { "Identifier.value must not be blank." }
    }

    /** The deterministic UUID URN of an entry keyed by this identifier. */
    public val fullUrl: String
        get() = ExchangeProtocol.fullUrl(system.value, value)

    /** The identifier as a FHIR element without a role coding. */
    public fun toFhir(): Identifier = Identifier().setSystem(system.value).setValue(value)

    override fun compareTo(other: BusinessIdentifier): Int =
        compareValuesBy(this, other, { it.system.value }, { it.value })

    override fun equals(other: Any?): Boolean =
        other is BusinessIdentifier && system == other.system && value == other.value

    override fun hashCode(): Int = 31 * system.hashCode() + value.hashCode()

    override fun toString(): String = "BusinessIdentifier(system=$system, value=<redacted>)"

    public companion object {
        /** The complete pair a FHIR Identifier carries, or an [IllegalArgumentException] when incomplete. */
        public fun from(identifier: Identifier): BusinessIdentifier {
            require(identifier.hasSystem() && identifier.hasValue()) {
                "A complete Identifier.system and Identifier.value are required."
            }
            return BusinessIdentifier(IdentifierSystem(identifier.system), identifier.value)
        }
    }
}

/** A business identifier together with the Grove role it plays in `Identifier.type`. */
public class RoledIdentifier(
    public val identifier: BusinessIdentifier,
    public val role: GroveIdentifierRole,
) {
    /** The deterministic UUID URN of an entry keyed by this identifier. */
    public val fullUrl: String
        get() = identifier.fullUrl

    /** The identifier as a FHIR element carrying its role coding. */
    public fun toFhir(): Identifier = identifier.toFhir().apply {
        type = CodeableConcept(
            Coding(ExchangeContract.GROVE_IDENTIFIER_ROLE, role.code, role.display),
        )
    }

    override fun equals(other: Any?): Boolean =
        other is RoledIdentifier && identifier == other.identifier && role == other.role

    override fun hashCode(): Int = 31 * identifier.hashCode() + role.hashCode()

    override fun toString(): String = "RoledIdentifier(identifier=$identifier, role=${role.code})"

    public companion object {
        /** The roled pair a FHIR Identifier carries, or null when it carries no Grove role coding. */
        public fun from(identifier: Identifier): RoledIdentifier? {
            val codings = identifier.type.coding.filter { it.system == ExchangeContract.GROVE_IDENTIFIER_ROLE }
            val role = codings.singleOrNull()?.code?.let(GroveIdentifierRole::of) ?: return null
            return RoledIdentifier(BusinessIdentifier.from(identifier), role)
        }
    }
}
