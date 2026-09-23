//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.ExchangeGraphRule
import org.grovealliance.fhir.ProducerDiagnostic
import org.grovealliance.fhir.at

/** What an accepted record lost; every case is one `mobile-omission.*` registry row with severity warning. */
public sealed interface HealthConnectConversionWarning {
    /** The registry diagnostic this warning reports. */
    public val diagnostic: ProducerDiagnostic

    /** The record named a device no stable per-unit token identifies; [deviceName] is its manufacturer and model, when known. */
    public data class RecordingDeviceOmitted(public val deviceName: String?) : HealthConnectConversionWarning {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_OMISSION_RECORDING_DEVICE.at("Observation.device")
    }

    /** The source stated no UTC offset for the effective [field], such as `Observation.effectiveDateTime`, so it is in UTC. */
    public data class SourceOffsetUnavailable(public val field: String) : HealthConnectConversionWarning {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_OMISSION_SOURCE_OFFSET.at(this.field)
    }

    /** The record carried populated fields outside the adapter's typed allowlist; [keys] names them in sorted order. */
    public data class UnmodeledMetadataWithheld(public val keys: List<String>) : HealthConnectConversionWarning {
        override val diagnostic: ProducerDiagnostic
            get() = ExchangeGraphRule.MOBILE_OMISSION_UNMODELED_METADATA.at("Observation.extension")
    }
}
