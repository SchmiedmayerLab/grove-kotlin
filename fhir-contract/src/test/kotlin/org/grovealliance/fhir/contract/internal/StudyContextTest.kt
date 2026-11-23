//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Patient
import org.grovealliance.fhir.contract.PlanDefinition
import org.grovealliance.fhir.contract.ResearchStudy
import org.grovealliance.fhir.contract.ResearchSubject
import org.grovealliance.fhir.contract.StudyEnrollment
import org.grovealliance.fhir.contract.Subject
import org.junit.Test

@OptIn(InternalGroveFhirApi::class)
class StudyContextTest {
    private val enrollment = StudyEnrollment.create(
        study = ExchangeTestScope.identifier(system = "https://study.example.org/fhir/identifiers/research-study", value = "mhc"),
        protocolUrl = "https://study.example.org/fhir/PlanDefinition/mhc",
        protocolVersion = "50",
        enrollment = ExchangeTestScope.identifier(system = "https://study.example.org/fhir/identifiers/research-subject", value = "e-1"),
    ).getOrThrow()

    @Test
    fun `references a logical subject by its pseudonym and links each study to its plan and subject`() {
        // when
        val context = StudyContext.create(
            subject = Subject.Logical(identifier = ExchangeTestScope.subject),
            studies = listOf(enrollment),
            identityScope = ExchangeTestScope.identityScope,
        ).getOrThrow()

        // then
        val (study, plan, researchSubject) = context.entries.map { it.resource }
        assertThat(context.patient).isNull()
        assertThat(context.subjectReference.identifier?.value).isEqualTo("participant-001")
        assertThat((study as ResearchStudy).protocol.single().reference).isEqualTo(context.entries[1].fullUrl)
        assertThat((plan as PlanDefinition).version).isEqualTo("50")
        assertThat((researchSubject as ResearchSubject).study.reference).isEqualTo(context.entries[0].fullUrl)
        assertThat(context.studyReferences.single().reference).isEqualTo(context.entries[0].fullUrl)
    }

    @Test
    fun `bundles a patient keyed by its content`() {
        // when
        val context = StudyContext.create(
            subject = Subject.Bundled(identifier = ExchangeTestScope.subject, patient = Patient()),
            studies = emptyList(),
            identityScope = ExchangeTestScope.identityScope,
        ).getOrThrow()

        // then
        val patient = context.patient?.resource as Patient
        assertThat(patient.identifier.map { it.value }).hasSize(2)
        assertThat(context.subjectReference.reference).isEqualTo(context.patient?.fullUrl)
    }

    @Test
    fun `states a plan two enrollments share once`() {
        // given
        val second = StudyEnrollment.create(
            study = ExchangeTestScope.identifier(system = "https://study.example.org/fhir/identifiers/research-study", value = "mhc-2"),
            protocolUrl = enrollment.protocolUrl,
            protocolVersion = enrollment.protocolVersion,
            enrollment = ExchangeTestScope.identifier(
                system = "https://study.example.org/fhir/identifiers/research-subject",
                value = "e-2",
            ),
        ).getOrThrow()

        // when
        val context = StudyContext.create(
            subject = Subject.Logical(identifier = ExchangeTestScope.subject),
            studies = listOf(enrollment, second),
            identityScope = ExchangeTestScope.identityScope,
        ).getOrThrow()

        // then
        assertThat(context.entries.count { it.resource is PlanDefinition }).isEqualTo(1)
        assertThat(context.entries).hasSize(5)
    }
}
