//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The identities one assembled record minted, for callers that index or cross-reference them.
 */
data class ExchangeGraphIdentifiers(
    val sourceRecord: RoledIdentifier,
    val primaryOutput: RoledIdentifier,
    val applicationSnapshot: RoledIdentifier,
    val hostSnapshot: RoledIdentifier,
    val provenance: RoledIdentifier,
    val childOutputs: List<RoledIdentifier>,
    val recordingDeviceSnapshot: RoledIdentifier?,
    val writerSnapshot: RoledIdentifier?,
    val writerHostSnapshot: RoledIdentifier?,
)
