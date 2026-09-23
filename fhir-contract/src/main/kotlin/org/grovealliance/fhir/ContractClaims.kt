//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/** One adapter's conversion Provenance profile and the adapter output profiles it must target. */
public data class AdapterConversionProvenanceClaim(
    public val adapter: String,
    public val profile: String,
    public val targetAdapterProfiles: Set<String>,
)

/** One registered recording payload format and the media types it admits. */
public data class RecordingFormat(public val code: String, public val contentTypes: Set<String>)

/** One catalog-governed Reference path and the resource types it may target. */
public data class GovernedReferencePath(
    public val resourceType: String,
    public val path: String,
    public val repeating: Boolean,
    public val targetTypes: Set<String>,
)

/** Where the optional governed source identifier may and may never appear in an active event. */
public data class GovernedSourceIdentifierPolicy(
    public val maxPerPrimaryOutput: Int,
    public val maxPrimaryOutputsPerEvent: Int,
    public val neverResourceTypes: Set<String>,
    public val neverProfiles: Set<String>,
)
