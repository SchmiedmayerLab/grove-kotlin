//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * What one adapter states identically in every record it emits: its [adapterId], the profiles of its
 * conversion Provenance and application Device, the [applicationIdentifiers] its application Device
 * carries beside its snapshot key, and the [writerApplicationSystem] a writer record's application
 * is numbered in.
 */
@InternalGroveFhirApi
data class ExchangeAdapterContract(
    val adapterId: String,
    val provenanceProfile: String,
    val applicationDeviceProfile: String,
    val writerApplicationSystem: IdentifierSystem? = null,
    val applicationIdentifiers: (ApplicationDevice) -> List<Identifier> = { emptyList() },
)
