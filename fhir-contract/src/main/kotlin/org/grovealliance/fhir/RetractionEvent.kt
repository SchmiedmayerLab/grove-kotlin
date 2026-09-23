//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.InstantType
import org.hl7.fhir.r4.model.Provenance
import org.hl7.fhir.r4.model.Reference
import java.time.Instant

/**
 * The lifecycle assertion that a source record is no longer exposed, naming every prior graph node.
 *
 * It neither asserts clinical error nor instructs a repository to delete anything.
 */
public class RetractionEvent(
    targets: List<RetractionTarget>,
    public val context: ExchangeEventContext,
    public val sourceRecord: RoledIdentifier,
    public val retractedAt: Instant,
) {
    public val targets: List<RetractionTarget> = targets.toList()

    init {
        require(sourceRecord.role == GroveIdentifierRole.SOURCE_RECORD) {
            "A retraction names its source record by a source-record identity."
        }
        require(retractedAt in ExchangeEventContext.FHIR_INSTANT_RANGE) {
            "The retraction instant must have a four-digit FHIR year in the range 0001 through 9999."
        }
    }

    /** The validated retraction graph. */
    public val graph: ExchangeGraph = ExchangeGraph.of(ExchangeGraphKind.RETRACTION, context.event, bundle())

    private fun bundle(): Bundle = Bundle().apply {
        identifier = context.event.identifier.toFhir()
        meta.addProfile(ExchangeContract.MOBILE_RETRACTION_BUNDLE_PROFILE)
        type = Bundle.BundleType.COLLECTION
        timestampElement = InstantType(retractedAt.toString())
        context.repositoryIds[ExchangeGraphNode.BUNDLE]?.let { id = it.value }
        val key = EntryNodeKey(context.entryNodeIdentifierSystem, context.event, RETRACTION_PROVENANCE_ROLE, 0)
        @OptIn(InternalGroveFhirApi::class)
        GraphEntry(key.identifier, provenance()).addTo(this)
    }

    private fun provenance(): Provenance = Provenance().apply {
        meta.addProfile(ExchangeContract.MOBILE_RETRACTION_PROVENANCE_PROFILE)
        context.repositoryIds[ExchangeGraphNode.PROVENANCE]?.let { id = it.value }
        occurred = DateTimeType(retractedAt.toString())
        recordedElement = InstantType(retractedAt.toString())
        activity = CodeableConcept(
            Coding(
                ExchangeContract.GROVE_LIFECYCLE_EVENT,
                ExchangeContract.RETRACTION_ACTIVITY_CODE,
                ExchangeContract.RETRACTION_ACTIVITY_DISPLAY,
            ),
        )
        addAgent().apply {
            type = CodeableConcept(Coding(ExchangeContract.PROVENANCE_PARTICIPANT, ASSEMBLER, "Assembler"))
            who = Reference().apply {
                type = "Device"
                identifier = context.identityScope.deviceSnapshot(
                    context.event,
                    DeviceSnapshotRole.APPLICATION,
                    context.application.sourceDeviceToken,
                ).toFhir()
            }
        }
        addEntity().apply {
            role = Provenance.ProvenanceEntityRole.SOURCE
            what = Reference().setIdentifier(sourceRecord.toFhir())
        }
        targets
            .sortedWith(compareBy({ it.identifier.identifier }, { it.resourceType.name }, { it.role.code }))
            .forEach { addTarget(it.toReference()) }
    }

    override fun toString(): String = "RetractionEvent(event=${context.event}, targets=${targets.size})"

    private companion object {
        const val RETRACTION_PROVENANCE_ROLE = "retraction-provenance"
        const val ASSEMBLER = "assembler"
    }
}
