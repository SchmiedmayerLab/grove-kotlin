//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Observation

internal fun Observation.sourceToken(system: String, path: String = "Observation.valueCodeableConcept"): String =
    valueCodeableConcept?.coding?.firstOrNull { it.system == system }?.code
        ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedCode(path))

/** The AndroidX integer behind a source token, or a refusal when the token is outside the closed domain. */
internal fun Map<Int, String>.sourceValue(token: String, path: String): Int =
    entries.firstOrNull { it.value == token }?.key ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedCode(path))

/** The AndroidX integer behind an externally coded element, or the unknown value when the element is absent. */
internal fun Map<Int, String>.externalValue(mapping: ExternalCodedMapping, coding: Coding?, path: String): Int {
    if (coding == null) return UNKNOWN_SOURCE_VALUE
    val token = mapping.values.firstOrNull { it.coding?.system == coding.system && it.coding.code == coding.code }?.source
        ?: refuseProjection(HealthConnectProjectionRefusal.UnsupportedCode(path))
    return sourceValue(token, path)
}

/** Every AndroidX 1.1.0 enumeration states its unknown or absent value as zero. */
internal const val UNKNOWN_SOURCE_VALUE = 0

/** The exact source code an Observation.method carries in one Health Connect CodeSystem. */
internal fun Observation.methodCode(system: String): String =
    method?.coding?.firstOrNull { it.system == system }?.code
        ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement("Observation.method"))

/** One component's Quantity value, or null when the Observation carries no such component. */
internal fun Observation.componentValue(component: ComponentSpec): Double? {
    val match = this.component.firstOrNull { candidate ->
        candidate.code.coding.any { it.system == component.code.system && it.code == component.code.code }
    } ?: return null
    val path = "Observation.component[${component.id}]"
    val quantitySpec = requireNotNull(component.quantity)
    val value = match.valueQuantity?.takeIf { it.hasValue() } ?: refuseProjection(HealthConnectProjectionRefusal.MissingElement(path))
    if (value.system != quantitySpec.system || value.code != quantitySpec.code) {
        refuseProjection(HealthConnectProjectionRefusal.UnitMismatch(path))
    }
    return value.value.toDouble()
}

internal fun Observation.sessionTitle(): String? =
    getExtensionByUrl(HealthConnectContract.HEALTH_CONNECT_SESSION_TITLE)?.value?.primitiveValue()

internal fun Observation.sessionNotes(): String? = note.firstOrNull()?.text
