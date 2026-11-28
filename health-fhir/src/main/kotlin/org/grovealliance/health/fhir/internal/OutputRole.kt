//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

/**
 * The role an output plays in the record that emits it, a component of its source-output identity.
 *
 * @property value the role's identity token
 */
internal enum class OutputRole(val value: String) {
    SINGLE(value = "single"),
    SAMPLE(value = "sample"),
    SPECIMEN(value = "specimen"),
    PRESENT_FIELD(value = "present-field"),
    SLEEP_STAGE(value = "sleep-stage"),
    WORKOUT_SEGMENT(value = "workout-segment"),
}
