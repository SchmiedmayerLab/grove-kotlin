//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.DeploymentIdentifierSystems
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.KeyEpoch
import org.grovealliance.fhir.contract.OpaqueIdentityScope

/**
 * The conformance identity scope and a participant pseudonym, for tests that assemble graph pieces.
 */
@OptIn(InternalGroveFhirApi::class)
object ExchangeTestScope {
    private val epoch = KeyEpoch.of(value = 1).getOrThrow()

    val identityScope: OpaqueIdentityScope = OpaqueIdentityScope.forConformanceTesting(
        systems = DeploymentIdentifierSystems.derived(
            root = system(rawValue = "https://study.example.org/fhir"),
            keyId = "test-key",
            epoch = epoch,
        ).getOrThrow(),
        keyId = "test-key",
        epoch = epoch,
    ).getOrThrow()

    val subject: BusinessIdentifier = identifier(
        system = "https://study.example.org/fhir/identifiers/participant",
        value = "participant-001",
    )

    fun identifier(system: String, value: String): BusinessIdentifier = BusinessIdentifier.create(
        system = system(rawValue = system),
        value = value,
    ).getOrThrow()

    fun system(rawValue: String): IdentifierSystem = IdentifierSystem.create(rawValue = rawValue).getOrThrow()
}
