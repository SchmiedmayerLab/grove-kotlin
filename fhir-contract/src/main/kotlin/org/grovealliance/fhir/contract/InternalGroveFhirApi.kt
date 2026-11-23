//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * Marks API that Grove's FHIR modules share among themselves, such as the identity kinds and the
 * lossless JSON an adapter builds on. Apps don't need it; its shape may change without notice.
 */
@RequiresOptIn(
    message = "Internal to Grove's FHIR modules; apps build on the public exchange API instead.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
annotation class InternalGroveFhirApi
