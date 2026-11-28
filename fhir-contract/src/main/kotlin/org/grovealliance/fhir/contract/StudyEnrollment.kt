//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.BlankText

/**
 * One known study association: the [study], its exact protocol revision [protocolUrl] and
 * [protocolVersion], and the participant's [enrollment].
 */
@ConsistentCopyVisibility
data class StudyEnrollment private constructor(
    val study: BusinessIdentifier,
    val protocolUrl: String,
    val protocolVersion: String,
    val enrollment: BusinessIdentifier,
) {

    /**
     * Why an enrollment can't be carried.
     */
    sealed class ValidationError(message: String) : Exception(message) {

        /**
         * The protocol version is blank.
         */
        data object BlankProtocolVersion : ValidationError("The protocol version is blank.")
    }

    companion object {

        /**
         * The enrollment, or [ValidationError.BlankProtocolVersion] when [protocolVersion] is blank.
         */
        fun create(
            study: BusinessIdentifier,
            protocolUrl: String,
            protocolVersion: String,
            enrollment: BusinessIdentifier,
        ): Result<StudyEnrollment> = if (BlankText().isBlank(value = protocolVersion)) {
            Result.failure(ValidationError.BlankProtocolVersion)
        } else {
            Result.success(
                StudyEnrollment(
                    study = study,
                    protocolUrl = protocolUrl,
                    protocolVersion = protocolVersion,
                    enrollment = enrollment,
                ),
            )
        }
    }
}
