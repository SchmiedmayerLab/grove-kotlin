//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.Patient

/** The participant, referenced through a deployment-scoped pseudonym or a bundled Patient entry. */
public sealed interface Subject {
    /** The deployment-scoped pseudonym pair; never a Grove identity and never a reserved code system. */
    public val identity: BusinessIdentifier

    /** An identifier-only logical Patient reference; the default, which fabricates no Bundle entry. */
    public data class Logical(override val identity: BusinessIdentifier) : Subject {
        init {
            requirePseudonym(identity)
        }
    }

    /** A concrete Patient bundled as an event-scoped entry that every output references by fullUrl. */
    public class Bundled(override val identity: BusinessIdentifier, patient: Patient) : Subject {
        private val snapshot: Patient = patient.copy()

        init {
            requirePseudonym(identity)
            require(snapshot.contained.isEmpty()) { "A bundled Patient carries no contained resources." }
            if (snapshot.identifier.none { it.system == identity.system.value && it.value == identity.value }) {
                snapshot.addIdentifier(identity.toFhir())
            }
        }

        /** A copy of the Patient entry as it enters the graph. */
        public val patient: Patient
            get() = snapshot.copy()

        override fun toString(): String = "Subject.Bundled(identity=$identity)"
    }

    private companion object {
        fun requirePseudonym(identity: BusinessIdentifier) {
            require(identity.system.value !in ExchangeContract.reservedPatientIdentifierSystems) {
                "A subject pseudonym must not use a protocol-reserved code system."
            }
        }
    }
}

/** One ResearchStudy, its exact-revision PlanDefinition and one ResearchSubject for a known enrollment. */
public data class StudyEnrollment(
    public val study: BusinessIdentifier,
    public val protocolUrl: String,
    public val protocolVersion: String,
    public val enrollment: BusinessIdentifier,
) {
    init {
        require(ExchangeProtocol.isAbsoluteAsciiUri(protocolUrl)) { "A protocol URL is an absolute canonical URL." }
        require(protocolVersion.isNotBlank() && ExchangeProtocol.isScalarText(protocolVersion)) {
            "A protocol version is nonblank Unicode-scalar text."
        }
    }
}
