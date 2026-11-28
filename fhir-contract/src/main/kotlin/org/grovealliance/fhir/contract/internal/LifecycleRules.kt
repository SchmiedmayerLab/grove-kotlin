//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ExchangeContract
import org.grovealliance.fhir.contract.ExchangeGraphError
import org.grovealliance.fhir.contract.ExchangeGraphRule
import org.grovealliance.fhir.contract.ExchangeRecord
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.internal.GraphDocument.Companion.violation

/**
 * The lifecycle rules of a graph of [kind]: every Patient entry names one pseudonym outside the
 * Grove-reserved systems, then the profile and record rules of an active graph or the rules of a
 * retraction graph.
 */
@OptIn(InternalGroveFhirApi::class)
internal class LifecycleRules(
    private val document: GraphDocument,
    private val kind: ExchangeRecord.Kind,
) {
    private val facts = GraphFacts(document = document)

    fun violation(): ExchangeGraphError? = patientViolation() ?: when (kind) {
        ExchangeRecord.Kind.ACTIVE -> ProfileRules(document = document, facts = facts).violation()
            ?: ActiveRecordRules(document = document, facts = facts).violation()
        ExchangeRecord.Kind.RETRACTION -> RetractionRules(document = document, facts = facts).violation()
    }

    private fun patientViolation(): ExchangeGraphError? = document.entries.indices
        .filter { document.type(index = it) == ResourceType.PATIENT }
        .firstOrNull { index ->
            val pseudonym = document.resource(index = index)?.let { facts.patientPseudonym(patient = it) }
            pseudonym == null || pseudonym.system.rawValue in RESERVED_SYSTEMS
        }
        ?.let { violation(rule = ExchangeGraphRule.MOBILE_EXCHANGE_PATIENT_PSEUDONYM, location = "Bundle.entry[$it].resource.identifier") }

    private companion object {
        val RESERVED_SYSTEMS = setOf(
            ExchangeContract.IDENTIFIER_ROLE_CODE_SYSTEM,
            ExchangeContract.LIFECYCLE_EVENT_CODE_SYSTEM,
            ExchangeContract.RETRACTION_TARGET_ROLE_CODE_SYSTEM,
        )
    }
}
