//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import org.grovealliance.fhir.ExchangeGraphDiagnostic
import org.grovealliance.fhir.ExchangeGraphRule
import org.grovealliance.fhir.at

/** What an accepted record lost; every case is one `mobile-omission.*` registry row with severity warning. */
public sealed interface HealthConnectConversionWarning {
    /** The registry diagnostic this warning reports. */
    public val diagnostic: ExchangeGraphDiagnostic

    /** The record named a device the deployment cannot identify by a stable per-unit token. */
    public data class RecordingDeviceOmitted(public val deviceName: String) : HealthConnectConversionWarning {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_OMISSION_RECORDING_DEVICE.at("Observation.device")
    }

    /** The source supplied an effective instant without a UTC offset, so it was serialized in UTC. */
    public data class SourceOffsetUnavailable(public val field: String) : HealthConnectConversionWarning {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_OMISSION_SOURCE_OFFSET.at("Observation.effective[x]")
    }

    /** The record carried populated fields outside the adapter's typed allowlist. */
    public data class UnmodeledMetadataWithheld(public val fields: Set<String>) : HealthConnectConversionWarning {
        override val diagnostic: ExchangeGraphDiagnostic
            get() = ExchangeGraphRule.MOBILE_OMISSION_UNMODELED_METADATA.at("Observation.extension")
    }
}
