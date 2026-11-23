//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * A business [identifier] together with the Grove [role] it carries in `Identifier.type`.
 */
data class RoledIdentifier(
    val identifier: BusinessIdentifier,
    val role: GroveIdentifierRole,
) {

    /**
     * The deterministic `urn:uuid:` of the entry this identifier keys; see [BusinessIdentifier.fullUrl].
     */
    val fullUrl: String get() = identifier.fullUrl

    /**
     * This identifier as a FHIR `Identifier` typed with its Grove role.
     */
    val fhirIdentifier: Identifier
        get() = Identifier(
            system = identifier.system.rawValue,
            value = identifier.value,
            type = CodeableConcept(
                coding = listOf(
                    Coding(
                        system = GroveIdentifierRole.CODE_SYSTEM,
                        code = role.code,
                    ),
                ),
            ),
        )
}
