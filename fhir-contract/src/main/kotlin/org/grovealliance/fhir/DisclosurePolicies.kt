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

/** Whether a workout route may leave the device as a recording document. */
public enum class RouteDisclosurePolicy {
    OMIT,
    AUTHORIZED,
}

/** One coding of an optional source-native `Identifier.type`; never a Grove graph role. */
public class GovernedSourceIdentifierTypeCoding(
    public val system: IdentifierSystem,
    public val code: String,
    public val display: String? = null,
) {
    init {
        require(system.value != ExchangeContract.GROVE_IDENTIFIER_ROLE) {
            "A source-native Identifier.type cannot claim a Grove graph identity role."
        }
        require(FHIR_CODE.matches(code)) {
            "A source-native Identifier.type code has no leading, trailing, consecutive or control whitespace."
        }
        display?.let { requireNonblankScalar(it, "Source-native Identifier.type display") }
    }

    internal fun toFhir(): Coding = Coding(system.value, code, display)

    override fun equals(other: Any?): Boolean =
        other is GovernedSourceIdentifierTypeCoding && system == other.system && code == other.code &&
            display == other.display

    override fun hashCode(): Int = listOf(system, code, display).hashCode()

    override fun toString(): String = "GovernedSourceIdentifierTypeCoding(system=$system, code=$code, display=$display)"

    private companion object {
        val FHIR_CODE = Regex("""[^\s\p{Cc}]+(?: [^\s\p{Cc}]+)*""")
    }
}

/** The optional `Identifier.type` of a disclosed source-native identifier. */
public class GovernedSourceIdentifierType(
    codings: List<GovernedSourceIdentifierTypeCoding> = emptyList(),
    public val text: String? = null,
) {
    public val codings: List<GovernedSourceIdentifierTypeCoding> = codings.toList()

    init {
        text?.let { requireNonblankScalar(it, "Source-native Identifier.type text") }
        require(this.codings.isNotEmpty() || text != null) {
            "A source-native Identifier.type requires at least one coding or nonblank text."
        }
    }

    internal fun toFhir(): CodeableConcept = CodeableConcept().apply {
        codings.forEach { addCoding(it.toFhir()) }
        text = this@GovernedSourceIdentifierType.text
    }

    override fun toString(): String = "GovernedSourceIdentifierType(codings=$codings, text=$text)"
}

/** Whether the exact source-native record identifier leaves the device on the designated primary output. */
public sealed interface GovernedSourceIdentifierDisclosurePolicy {
    /** The default: the native identifier stays on the device. */
    public data object Omit : GovernedSourceIdentifierDisclosurePolicy

    /** Disclose the native identifier under the deployment's own key-space system. */
    public class Authorized(
        public val system: IdentifierSystem,
        public val type: GovernedSourceIdentifierType? = null,
    ) : GovernedSourceIdentifierDisclosurePolicy {
        init {
            require(system.value != ExchangeContract.GROVE_IDENTIFIER_ROLE) {
                "A source-native identifier system cannot be the Grove graph-role CodeSystem."
            }
        }

        /** The disclosed identifier for one exact native value. */
        public fun identifier(nativeValue: String): Identifier = Identifier().apply {
            system = this@Authorized.system.value
            value = nativeValue
            this@Authorized.type?.let { type = it.toFhir() }
        }

        override fun toString(): String = "GovernedSourceIdentifierDisclosurePolicy.Authorized(system=$system, type=$type)"
    }
}
