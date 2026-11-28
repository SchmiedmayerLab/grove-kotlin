//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Applies a record's identities and envelope links to each of its outputs.
 */
@InternalGroveFhirApi
internal class OutputDecorator(
    private val envelope: ExchangeEnvelope,
    private val sourceRecord: SourceRecordIdentity,
    private val subjectReference: Reference,
    private val studyReferences: List<Reference>,
    private val observationDeviceUrl: String?,
    private val gatewayUrl: String?,
) {

    /**
     * The output's identity and its resource with the record's identifiers and the links it takes.
     */
    fun decorate(output: ExchangeOutputDraft): Result<Pair<RoledIdentifier, FhirResource>> = runCatching {
        val identity = sourceRecord.output(role = output.role, discriminator = output.discriminator).getOrThrow()
        val writerRecord = output.writerRecord?.let { writerRecord(record = it).getOrThrow() }
        val identifiers = listOf(sourceRecord.identifier.fhirIdentifier, identity.fhirIdentifier) +
            output.clearIdentifiers +
            listOfNotNull(writerRecord?.fhirIdentifier)
        val resource: FhirResource = when (val resource = output.resource) {
            is ExchangeOutputDraft.Resource.ObservationResource -> decorate(
                observation = resource.observation.copy(identifier = identifiers),
                output = output,
            )
            is ExchangeOutputDraft.Resource.SpecimenResource -> resource.specimen.copy(
                identifier = identifiers,
                subject = subjectReference.takeIf { ExchangeOutputDraft.Link.SUBJECT in output.links } ?: resource.specimen.subject,
            )
        }
        identity to resource
    }

    private fun decorate(observation: Observation, output: ExchangeOutputDraft): Observation {
        val links = output.links
        var extensions = observation.extension
        if (ExchangeOutputDraft.Link.MANUAL_ENTRY in links && output.wasUserEntered) {
            extensions = extensions.replacing(extension = MANUAL_ENTRY)
        }
        if (ExchangeOutputDraft.Link.GATEWAY in links && gatewayUrl != null) {
            extensions = extensions.replacing(
                extension = Extension(
                    url = Hl7Url.OBSERVATION_GATEWAY_DEVICE,
                    value = Extension.Value.ReferenceValue(value = Reference(reference = gatewayUrl)),
                ),
            )
        }
        if (ExchangeOutputDraft.Link.STUDIES in links) {
            extensions = extensions + studyReferences.map { study ->
                Extension(url = Hl7Url.WORKFLOW_RESEARCH_STUDY, value = Extension.Value.ReferenceValue(value = study))
            }
        }
        output.writerRecord?.let { record ->
            extensions = extensions + Extension(
                url = ExchangeContract.WRITER_RECORD_VERSION_EXTENSION,
                value = Extension.Value.StringValue(value = record.version),
            )
        }
        return observation.copy(
            extension = extensions + output.trailingExtensions,
            subject = if (ExchangeOutputDraft.Link.SUBJECT in links) subjectReference else observation.subject,
            device = observationDeviceUrl
                ?.takeIf { ExchangeOutputDraft.Link.RECORDING_DEVICE in links }
                ?.let { Reference(reference = it) }
                ?: observation.device,
        )
    }

    private fun writerRecord(record: ExchangeOutputDraft.WriterRecord): Result<RoledIdentifier> {
        val system = envelope.adapter.writerApplicationSystem
            ?: return Result.failure(IllegalStateException("The adapter names no writer application system."))
        return BusinessIdentifier.create(system = system, value = record.writerApplication).fold(
            onSuccess = { application ->
                envelope.identityScope.writerRecord(
                    subject = envelope.subject.identifier,
                    writerApplication = application,
                    writerRecordId = record.writerRecordId,
                )
            },
            onFailure = { Result.failure(it) },
        )
    }

    /**
     * These extensions with every one at [extension]'s url replaced by it, appended last.
     */
    private fun List<Extension>.replacing(extension: Extension) = filterNot { it.url == extension.url } + extension

    private companion object {
        val MANUAL_ENTRY = Extension(
            url = ExtensionUrl.Mobile.GROVE_RECORDING_METHOD,
            value = Extension.Value.CodingValue(
                value = Coding(
                    system = CodeSystemUrl.Mobile.GROVE_RECORDING_METHOD,
                    code = "manual-entry",
                    display = "Manual entry",
                ),
            ),
        )
    }
}
