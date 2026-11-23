//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The canonical URLs of the HL7 code systems and extensions the exchange states.
 */
object Hl7Url {

    /**
     * The code system of a Provenance agent's participation type.
     */
    const val PROVENANCE_PARTICIPANT_TYPE = "http://terminology.hl7.org/CodeSystem/provenance-participant-type"

    /**
     * The code system of an Observation's category.
     */
    const val OBSERVATION_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category"

    /**
     * The extension naming the Device an Observation passed through.
     */
    const val OBSERVATION_GATEWAY_DEVICE = "http://hl7.org/fhir/StructureDefinition/observation-gatewayDevice"

    /**
     * The extension naming the body position during an Observation.
     */
    const val OBSERVATION_BODY_POSITION = "http://hl7.org/fhir/StructureDefinition/observation-bodyPosition"

    /**
     * The extension naming the ResearchStudy a resource belongs to.
     */
    const val WORKFLOW_RESEARCH_STUDY = "http://hl7.org/fhir/StructureDefinition/workflow-researchStudy"
}
