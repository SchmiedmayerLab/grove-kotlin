//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.Bundle
import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.ContextSnapshotRole
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.NodeRole
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.PlanDefinition
import org.grovealliance.fhir.contract.Reference
import org.grovealliance.fhir.contract.ResearchStudy
import org.grovealliance.fhir.contract.ResearchSubject
import org.grovealliance.fhir.contract.ResourceType
import org.grovealliance.fhir.contract.StudyEnrollment
import org.grovealliance.fhir.contract.Subject

/**
 * The bundled study context of one record: its content-keyed entries, what every output states as
 * its [subjectReference], and the [studyReferences] outputs make to each study.
 *
 * @property patient the Patient entry when the subject is bundled
 * @property entries the ResearchStudy, PlanDefinition and ResearchSubject entries, in that order per
 * enrollment, with a PlanDefinition two enrollments share stated once
 */
@OptIn(InternalGroveFhirApi::class)
internal data class StudyContext(
    val patient: Bundle.Entry?,
    val entries: List<Bundle.Entry>,
    val subjectReference: Reference,
    val studyReferences: List<Reference>,
) {

    val allEntries: List<Bundle.Entry> get() = listOfNotNull(patient) + entries

    companion object {

        /**
         * The study context of [subject] and the known [studies] of one record, each node keyed by its
         * content after the nodes it references: the plan, the study that names it, the subject that
         * names the study.
         */
        fun create(
            subject: Subject,
            studies: List<StudyEnrollment>,
            identityScope: OpaqueIdentityScope,
        ): Result<StudyContext> = runCatching {
            val patientEntry = (subject as? Subject.Bundled)?.let {
                patientEntry(subject = subject, identityScope = identityScope).getOrThrow()
            }
            val subjectReference = patientEntry
                ?.let { Reference(reference = it.fullUrl, type = ResourceType.PATIENT.value) }
                ?: subject.identifier.reference(resourceType = ResourceType.PATIENT)
            val entries = mutableListOf<Bundle.Entry>()
            val stated = mutableSetOf<String>()
            val studyReferences = studies.map { enrollment ->
                val study = studyEntries(
                    enrollment = enrollment,
                    subject = subject.identifier,
                    subjectReference = subjectReference,
                    identityScope = identityScope,
                ).getOrThrow()
                entries += study.first.filter { stated.add(it.fullUrl) }
                study.second
            }
            StudyContext(
                patient = patientEntry,
                entries = entries,
                subjectReference = subjectReference,
                studyReferences = studyReferences,
            )
        }

        private fun patientEntry(subject: Subject, identityScope: OpaqueIdentityScope): Result<Bundle.Entry> {
            val patient = subject.bundledPatient
            return identityScope.contentSnapshot(
                node = patient,
                subject = subject.identifier,
                role = NodeRole.Context(role = ContextSnapshotRole.PATIENT),
            ).map { key ->
                Bundle.Entry.keyed(
                    identifier = key,
                    resource = patient.copy(identifier = patient.identifier + key.fhirIdentifier),
                )
            }
        }

        private fun studyEntries(
            enrollment: StudyEnrollment,
            subject: BusinessIdentifier,
            subjectReference: Reference,
            identityScope: OpaqueIdentityScope,
        ): Result<Pair<List<Bundle.Entry>, Reference>> = runCatching {
            val plan = PlanDefinition(url = enrollment.protocolUrl, version = enrollment.protocolVersion)
            val planKey = identityScope.contentSnapshot(
                node = plan,
                subject = subject,
                role = NodeRole.Context(role = ContextSnapshotRole.PLAN_DEFINITION),
            ).getOrThrow()
            val study = ResearchStudy(
                identifier = listOf(enrollment.study.fhirIdentifier),
                protocol = listOf(Reference(reference = planKey.fullUrl)),
            )
            val studyKey = identityScope.contentSnapshot(
                node = study,
                subject = subject,
                role = NodeRole.Context(role = ContextSnapshotRole.RESEARCH_STUDY),
            ).getOrThrow()
            val researchSubject = ResearchSubject(
                identifier = listOf(enrollment.enrollment.fhirIdentifier),
                study = Reference(reference = studyKey.fullUrl),
                individual = subjectReference,
            )
            val subjectKey = identityScope.contentSnapshot(
                node = researchSubject,
                subject = subject,
                role = NodeRole.Context(role = ContextSnapshotRole.RESEARCH_SUBJECT),
            ).getOrThrow()
            val entries = listOf(
                Bundle.Entry.keyed(identifier = studyKey, resource = study.copy(identifier = study.identifier + studyKey.fhirIdentifier)),
                Bundle.Entry.keyed(identifier = planKey, resource = plan.copy(identifier = listOf(planKey.fhirIdentifier))),
                Bundle.Entry.keyed(
                    identifier = subjectKey,
                    resource = researchSubject.copy(identifier = researchSubject.identifier + subjectKey.fhirIdentifier),
                ),
            )
            entries to Reference(reference = studyKey.fullUrl, type = ResourceType.RESEARCH_STUDY.value)
        }
    }
}
