//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.Bundle
import org.grovealliance.fhir.contract.Device
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.RoledIdentifier

/**
 * One Device snapshot, the content key every reference to it resolves through, and the [url] it is
 * stated at.
 */
@OptIn(InternalGroveFhirApi::class)
internal data class IdentifiedDevice(
    val resource: Device,
    val identity: RoledIdentifier,
    val url: String,
) {

    /**
     * The snapshot's Bundle entry, keyed by [identity] at [url].
     */
    val entry: Bundle.Entry
        get() = Bundle.Entry.keyed(
            identifier = identity,
            resource = resource,
            fullUrl = url,
        )
}
