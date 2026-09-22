//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

/** Marks graph-assembly API that exists for Grove adapters; an application never calls it directly. */
@RequiresOptIn(
    message = "This API assembles exchange graphs for Grove adapters and is not part of the application surface.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public annotation class InternalGroveFhirApi

/** Marks the one entry point that admits the published conformance key, which production rejects. */
@RequiresOptIn(
    message = "The published conformance key is prohibited in production identity configuration.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
public annotation class ConformanceTestingApi
