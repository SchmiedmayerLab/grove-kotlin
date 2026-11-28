//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import java.time.Instant

/**
 * One record before assembly: the [sourceRecord], its [outputs] (the primary first), and the devices
 * and policies the adapter resolved for it.
 *
 * @property instant the conversion instant
 * @property recordingDevice the hardware the source names
 * @property sourceDevices the peripherals that supplied the record through [recordingDevice], each a
 * recording Device of its own
 * @property sourceDevicesSuppliedEverySample whether the one entry of [sourceDevices] supplied every
 * sample, so an Observation names that peripheral as its device
 * @property writer the application that wrote the record, named as the Provenance author
 * @property sourceExtensions adapter facts about the consumed source record
 * @property sourceAgents further agents of the source record entity, each a logical reference, such as the
 *   application that entered the record into its store
 */
@InternalGroveFhirApi
data class ExchangeRecordDraft(
    val instant: Instant,
    val sourceRecord: SourceRecordIdentity,
    val outputs: List<ExchangeOutputDraft>,
    val recordingDevice: RecordingDevice? = null,
    val sourceDevices: List<RecordingDevice> = emptyList(),
    val sourceDevicesSuppliedEverySample: Boolean = false,
    val writer: ExchangeWriterDraft? = null,
    val sourceExtensions: List<Extension> = emptyList(),
    val sourceAgents: List<Provenance.Agent> = emptyList(),
    val converterRole: ConverterRole = ConverterRole.Assembler,
)
