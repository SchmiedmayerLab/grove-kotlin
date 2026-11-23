//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Who is exporting: one deployment, one participant, one application installation.
 *
 * Immutable; create it again when an input changes. What every record shares is checked once here,
 * so a deployment fault is reported where the producer is configured rather than inside a conversion.
 */
class ExchangeProducer private constructor(
    @property:InternalGroveFhirApi val identityScope: OpaqueIdentityScope,
    @property:InternalGroveFhirApi val subject: Subject,
    val facts: ConversionFacts,
) {

    /**
     * A configuration the producer refuses, as every record built under it would be refused.
     */
    sealed class ConfigurationError(message: String) : Exception(message) {

        /**
         * The subject's pseudonym is numbered in one of the deployment's own identity systems.
         */
        data class ReservedSubjectIdentifierSystem(val system: IdentifierSystem) :
            ConfigurationError("The subject's system $system is reserved for graph identities.")

        /**
         * A study or enrollment identifier is numbered in one of the deployment's own identity systems.
         */
        data class ReservedStudyIdentifierSystem(val system: IdentifierSystem) :
            ConfigurationError("The study system $system is reserved for graph identities.")

        /**
         * Two enrollments name the same [study].
         */
        data class DuplicateStudy(
            val study: BusinessIdentifier,
        ) : ConfigurationError("Two enrollments name ${study.value}.")

        /**
         * Two enrollments carry the same [enrollment] identifier.
         */
        data class DuplicateEnrollment(val enrollment: BusinessIdentifier) :
            ConfigurationError("Two enrollments carry ${enrollment.value}.")

        /**
         * The bundled Patient states an identifier without a Grove role besides the pseudonym, such as a
         * medical record number, so it names no one pseudonym.
         */
        data object BundledPatientIdentifiers :
            ConfigurationError("The bundled Patient states identifiers besides the pseudonym.")

        /**
         * The registered diagnostic: every configuration fault is a deployment defect.
         */
        val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_INPUT_UNCLASSIFIED.diagnostic(location = "ExchangeProducer")
    }

    companion object {

        /**
         * The producer of one installation, or the [ConfigurationError] that every record would hit.
         *
         * @param identityScope the deployment's scope that mints every opaque identity
         * @param subject the participant every record is about
         * @param application the converting application
         * @param host the host the application runs on, as it is now
         * @param studies the participant's known enrollments, each study once
         */
        @OptIn(InternalGroveFhirApi::class)
        fun create(
            identityScope: OpaqueIdentityScope,
            subject: Subject,
            application: ApplicationDevice,
            host: HostDevice,
            studies: List<StudyEnrollment> = emptyList(),
        ): Result<ExchangeProducer> {
            configurationError(
                identityScope = identityScope,
                subject = subject,
                studies = studies,
            )?.let { return Result.failure(it) }
            return Result.success(
                ExchangeProducer(
                    identityScope = identityScope,
                    subject = subject,
                    facts = ConversionFacts(
                        application = application,
                        host = host,
                        studies = studies,
                    ),
                ),
            )
        }

        @OptIn(InternalGroveFhirApi::class)
        private fun configurationError(
            identityScope: OpaqueIdentityScope,
            subject: Subject,
            studies: List<StudyEnrollment>,
        ): ConfigurationError? {
            val reserved = identityScope.systems.all.toSet()
            val untypedPatientIdentifiers = subject.bundledPatient.identifier.filter { identifier ->
                identifier.type?.coding.orEmpty().none { it.system == GroveIdentifierRole.CODE_SYSTEM }
            }
            val isSinglePseudonym = untypedPatientIdentifiers.size == 1 &&
                untypedPatientIdentifiers[0].system == subject.identifier.system.rawValue &&
                untypedPatientIdentifiers[0].value == subject.identifier.value
            return when {
                subject.identifier.system in reserved ->
                    ConfigurationError.ReservedSubjectIdentifierSystem(system = subject.identifier.system)
                subject is Subject.Bundled && !isSinglePseudonym -> ConfigurationError.BundledPatientIdentifiers
                else -> studyError(studies = studies, reserved = reserved)
            }
        }

        private fun studyError(studies: List<StudyEnrollment>, reserved: Set<IdentifierSystem>): ConfigurationError? {
            val seenStudies = mutableSetOf<BusinessIdentifier>()
            val seenEnrollments = mutableSetOf<BusinessIdentifier>()
            return studies.firstNotNullOfOrNull { enrollment ->
                val reservedIdentifier = listOf(enrollment.study, enrollment.enrollment).firstOrNull { it.system in reserved }
                when {
                    reservedIdentifier != null -> ConfigurationError.ReservedStudyIdentifierSystem(system = reservedIdentifier.system)
                    !seenStudies.add(enrollment.study) -> ConfigurationError.DuplicateStudy(study = enrollment.study)
                    !seenEnrollments.add(enrollment.enrollment) ->
                        ConfigurationError.DuplicateEnrollment(enrollment = enrollment.enrollment)
                    else -> null
                }
            }
        }
    }
}
