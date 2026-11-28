//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The participant a record is about, by the deployment's pseudonym [identifier].
 *
 * A [Logical] subject travels as an identifier-only Patient reference; a [Bundled] one travels as a
 * Patient entry, which carries the pseudonym as its only identifier without a Grove role.
 */
sealed interface Subject {

    /**
     * The pseudonym, however the subject travels.
     */
    val identifier: BusinessIdentifier

    /**
     * A subject referenced by its pseudonym alone.
     */
    data class Logical(override val identifier: BusinessIdentifier) : Subject

    /**
     * A subject bundled as the deployment's [patient].
     */
    data class Bundled(
        override val identifier: BusinessIdentifier,
        val patient: Patient,
    ) : Subject

    /**
     * The Patient a graph bundles for this subject: the deployment's, with the pseudonym added unless
     * it already states it, or one stating only the pseudonym.
     */
    @InternalGroveFhirApi
    val bundledPatient: Patient
        get() = when (this) {
            is Logical -> Patient(identifier = listOf(identifier.fhirIdentifier))
            is Bundled -> {
                val statesPseudonym = patient.identifier.any { it.system == identifier.system.rawValue && it.value == identifier.value }
                if (statesPseudonym) patient else patient.copy(identifier = patient.identifier + identifier.fhirIdentifier)
            }
        }
}
