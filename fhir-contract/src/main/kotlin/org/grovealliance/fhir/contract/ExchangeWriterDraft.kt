//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The application that wrote a record into the source store and the host it ran on, stated as
 * snapshots of their own and as the Provenance author; [statesVersion] is whether the source states
 * the application's marketing version.
 */
@InternalGroveFhirApi
data class ExchangeWriterDraft(
    val application: ApplicationDevice,
    val host: HostDevice,
    val statesVersion: Boolean,
)
