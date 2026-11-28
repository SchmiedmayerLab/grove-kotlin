//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * How the converting application relates to the measurement it converts.
 */
@InternalGroveFhirApi
sealed interface ConverterRole {

    /**
     * The application assembled the record from one it did not mediate.
     */
    data object Assembler : ConverterRole

    /**
     * The converting application itself mediated the measurement.
     */
    data object Gateway : ConverterRole

    /**
     * A distinct [application] mediated the measurement; Observation outputs name it as their gateway.
     */
    data class GatewayApplication(val application: ApplicationDevice) : ConverterRole
}
