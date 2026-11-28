//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir.internal

import androidx.health.connect.client.records.metadata.Metadata
import org.grovealliance.fhir.contract.CodeSystemUrl
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.Extension
import org.grovealliance.fhir.contract.ExtensionUrl
import org.grovealliance.fhir.contract.Hl7Url
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.MeasurementContract
import org.grovealliance.fhir.contract.Measurements
import org.grovealliance.fhir.contract.Meta
import org.grovealliance.fhir.contract.Observation

/**
 * The Observation of one output of a Record: the profile claim, code and category its measurement
 * fixes, its effective time and result, when Health Connect last modified the Record, the exact Record
 * class, and how the Record was captured when the source states it.
 */
@OptIn(InternalGroveFhirApi::class)
internal class ObservationMapper {

    /**
     * What an output claims: its direct [profiles], its [codings] in order, and its [category].
     */
    data class Claim(
        val profiles: List<String>,
        val codings: List<Coding>,
        val category: Coding?,
    )

    /**
     * The claim of a shared [measurement]: its semantic profile beside the adapter envelope, and the
     * catalog's category, else the one Grove states for that measurement.
     */
    fun mapClaim(measurement: MeasurementContract): Claim = Claim(
        profiles = listOf(measurement.profile, HealthConnectCatalog.OBSERVATION_PROFILE),
        codings = listOf(measurement.code) + measurement.requiredCodings,
        category = measurement.category ?: CATEGORIES[measurement.id],
    )

    /**
     * The output of [claim] for a Record of [recordType] with [metadata].
     */
    @Suppress("detekt:LongParameterList")
    fun mapObservation(
        claim: Claim,
        recordType: String,
        metadata: Metadata,
        effective: Observation.Effective,
        issued: String,
        value: Observation.Value?,
        component: List<Observation.Component> = emptyList(),
    ): Observation = Observation(
        meta = Meta(profile = claim.profiles),
        extension = listOfNotNull(
            Extension(url = HealthConnectCatalog.RECORD_TYPE_EXTENSION, value = Extension.Value.CodeValue(value = recordType)),
            recordingMethod(method = metadata.recordingMethod),
        ),
        category = listOfNotNull(claim.category?.let { CodeableConcept(coding = listOf(it)) }),
        code = CodeableConcept(coding = claim.codings.map { displayed(coding = it) }),
        effective = effective,
        issued = issued,
        value = value,
        component = component,
    )

    /**
     * [coding] with the display the guide's terminology states when the catalog states none.
     */
    fun displayed(coding: Coding): Coding {
        val key = coding.system.orEmpty() to coding.code.orEmpty()
        val display = HealthConnectCatalog.displays[key] ?: HealthConnectCatalog.sharedDisplays[key]
        return if (coding.display != null) coding else coding.copy(display = display)
    }

    private fun recordingMethod(method: Int): Extension? {
        val (code, display) = when (method) {
            Metadata.RECORDING_METHOD_MANUAL_ENTRY -> "manual-entry" to "Manual entry"
            Metadata.RECORDING_METHOD_ACTIVELY_RECORDED -> "actively-recorded" to "Actively recorded"
            Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED -> "automatically-recorded" to "Automatically recorded"
            else -> return null
        }
        return Extension(
            url = ExtensionUrl.Mobile.GROVE_RECORDING_METHOD,
            value = Extension.Value.CodingValue(
                value = Coding(system = CodeSystemUrl.Mobile.GROVE_RECORDING_METHOD, code = code, display = display),
            ),
        )
    }

    private companion object {
        val CATEGORIES = mapOf(
            "body-mass-index" to Coding(system = Hl7Url.OBSERVATION_CATEGORY, code = "vital-signs", display = "Vital Signs"),
            Measurements.STEP_COUNT.id to Coding(system = Hl7Url.OBSERVATION_CATEGORY, code = "activity", display = "Activity"),
            Measurements.DISTANCE.id to Coding(system = Hl7Url.OBSERVATION_CATEGORY, code = "activity", display = "Activity"),
            Measurements.ACTIVE_ENERGY.id to Coding(system = Hl7Url.OBSERVATION_CATEGORY, code = "activity", display = "Activity"),
            Measurements.SLEEP_STAGE.id to Coding(system = Hl7Url.OBSERVATION_CATEGORY, code = "activity", display = "Activity"),
        )
    }
}
