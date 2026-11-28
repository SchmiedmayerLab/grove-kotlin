//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * One exact direct profile claim: the [profiles] a resource declares, with the Grove identifier roles
 * it must and may carry.
 */
@InternalGroveFhirApi
data class DirectProfileClaim(
    val profiles: List<String>,
    val requiredIdentifierRoles: List<String>,
    val optionalIdentifierRoles: List<String>,
)
