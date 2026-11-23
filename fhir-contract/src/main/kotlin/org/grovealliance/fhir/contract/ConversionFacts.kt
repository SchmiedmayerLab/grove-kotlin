//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * What a record states about the converting [application], its [host] and the participant's [studies],
 * in graph order.
 */
@ConsistentCopyVisibility
data class ConversionFacts internal constructor(
    val application: ApplicationDevice,
    val host: HostDevice,
    val studies: List<StudyEnrollment>,
)
