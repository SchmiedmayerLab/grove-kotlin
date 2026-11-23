//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.contract.ApplicationDevice
import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.DeploymentIdentifierSystems
import org.grovealliance.fhir.contract.ExchangeProducer
import org.grovealliance.fhir.contract.HostDevice
import org.grovealliance.fhir.contract.IdentifierSystem
import org.grovealliance.fhir.contract.KeyEpoch
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.Subject

/**
 * A producer under the conformance identity key, and the repository scope of one Health Connect store.
 */
object ExchangeProducerFixtures {
    private val epoch = KeyEpoch.of(value = 1).getOrThrow()

    val repositoryScope: BusinessIdentifier = identifier(
        system = "https://study.example.org/fhir/NamingSystem/health-connect-repository",
        value = "repository-1",
    )

    val writerApplicationSystem: IdentifierSystem = system(rawValue = "https://study.example.org/fhir/NamingSystem/android-package")

    fun create(): ExchangeProducer = ExchangeProducer.create(
        identityScope = OpaqueIdentityScope.forConformanceTesting(
            systems = DeploymentIdentifierSystems.derived(
                root = system(rawValue = "https://study.example.org/fhir"),
                keyId = "test-key",
                epoch = epoch,
            ).getOrThrow(),
            keyId = "test-key",
            epoch = epoch,
        ).getOrThrow(),
        subject = Subject.Logical(
            identifier = identifier(
                system = "https://study.example.org/fhir/identifiers/participant",
                value = "participant-001",
            ),
        ),
        application = ApplicationDevice.create(
            name = "My Heart Counts",
            packageName = "edu.stanford.myheartcounts",
            version = "1.0",
        ).getOrThrow(),
        host = HostDevice.create(
            operatingSystemVersion = "16",
            productType = "Pixel 9",
        ).getOrThrow(),
    ).getOrThrow()

    private fun identifier(system: String, value: String) = BusinessIdentifier.create(
        system = system(rawValue = system),
        value = value,
    ).getOrThrow()

    private fun system(rawValue: String) = IdentifierSystem.create(rawValue = rawValue).getOrThrow()
}
