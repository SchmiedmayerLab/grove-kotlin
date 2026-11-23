//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

/**
 * The hardware that acquired a record: every fact its source states about it. A blank fact counts as unset.
 *
 * Equal facts never prove one physical unit; only [stableUnitToken] does.
 *
 * @property stableUnitToken a verified per-unit token, never emitted: it enters the deployment-scoped
 * `recording-device` identity, together with [tokenScope]
 * @property tokenScope the authority that assigned [stableUnitToken], as the same token from two
 * writers names two units
 * @property name the name a person gave the device, which may name that person
 * @property model the model the source states; `Device.modelNumber` states it unless [productType] does
 * @property productType the hardware product type
 * @property deviceType the kind of device the source states, as `Device.type` text
 * @property localIdentifier the clear identifier the source states for the device, local to the host
 * that recorded it
 * @property udiProductionIdentifier the raw UDI production identifier, which may embed a lot or serial number
 * @property serialNumber a serial number, which links globally
 */
data class RecordingDevice(
    val stableUnitToken: String? = null,
    val tokenScope: String? = null,
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val productType: String? = null,
    val deviceType: String? = null,
    val systemName: String? = null,
    val systemVersion: String? = null,
    val hardwareVersion: String? = null,
    val firmwareVersion: String? = null,
    val softwareVersion: String? = null,
    val localIdentifier: String? = null,
    val udiDeviceIdentifier: String? = null,
    val udiProductionIdentifier: String? = null,
    val serialNumber: String? = null,
)
