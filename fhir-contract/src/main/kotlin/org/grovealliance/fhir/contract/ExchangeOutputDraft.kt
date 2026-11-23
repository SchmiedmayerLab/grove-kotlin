//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * One output of a source record before the envelope is applied: the [resource]'s own content, its
 * [role] and [discriminator] among the record's outputs, and the envelope [links] it takes.
 *
 * @property memberOfPrimary whether the primary Observation lists this output in `hasMember`
 * @property derivedFromPrimary whether this output names the primary in `derivedFrom`; never set on
 * the primary itself
 * @property specimenOfPrimary whether this output is the Specimen the primary Observation names in `specimen`
 * @property clearIdentifiers identifiers the deployment discloses beside the opaque ones, in order
 * @property writerRecord the logical record the writing application assigned, when the source states it
 * @property wasUserEntered whether a person entered the value by hand
 * @property trailingExtensions extensions stated after the ones the links add, in order
 */
@InternalGroveFhirApi
data class ExchangeOutputDraft(
    val role: String,
    val discriminator: String = "single",
    val resource: Resource,
    val links: Set<Link> = Link.entries.toSet(),
    val memberOfPrimary: Boolean = false,
    val derivedFromPrimary: Boolean = false,
    val specimenOfPrimary: Boolean = false,
    val clearIdentifiers: List<Identifier> = emptyList(),
    val writerRecord: WriterRecord? = null,
    val wasUserEntered: Boolean = false,
    val trailingExtensions: List<Extension> = emptyList(),
) {

    /**
     * The resource an output travels as.
     */
    sealed interface Resource {

        /**
         * A measurement.
         */
        data class ObservationResource(val observation: Observation) : Resource

        /**
         * The specimen a measurement was taken in.
         */
        data class SpecimenResource(val specimen: Specimen) : Resource
    }

    /**
     * An envelope statement an output carries beside its content.
     */
    enum class Link {

        /**
         * `subject`: who the output is about.
         */
        SUBJECT,

        /**
         * `device`: the record's sole peripheral when it supplied every sample, otherwise the recording Device.
         */
        RECORDING_DEVICE,

        /**
         * The gateway-device extension when the converter role names a gateway.
         */
        GATEWAY,

        /**
         * One research-study reference per bundled enrollment.
         */
        STUDIES,

        /**
         * The manual-entry recording method when the value was entered by hand.
         */
        MANUAL_ENTRY,
    }

    /**
     * The logical record [writerApplication] assigned, [writerRecordId], in its [version].
     */
    data class WriterRecord(
        val writerApplication: String,
        val writerRecordId: String,
        val version: String,
    )
}
