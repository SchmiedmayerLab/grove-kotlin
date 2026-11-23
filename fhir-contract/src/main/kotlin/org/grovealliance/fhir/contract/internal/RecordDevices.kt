//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ApplicationDevice
import org.grovealliance.fhir.contract.ConverterRole
import org.grovealliance.fhir.contract.ExchangeEnvelope
import org.grovealliance.fhir.contract.ExchangeOutputDraft
import org.grovealliance.fhir.contract.ExchangeRecordDraft
import org.grovealliance.fhir.contract.ExchangeWriterDraft
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.RoledIdentifier

/**
 * The Device snapshots of one record, and the links its outputs make to them.
 *
 * @property sources the record's peripherals, each once; one that states the recording Device's very
 * content is that entry
 * @property soleSource the record's one peripheral, when it names exactly one
 * @property writer the writer application
 * @property writerHost the host the writer application ran on
 * @property writerEntries the writer snapshots the record carries as entries of their own
 */
@OptIn(InternalGroveFhirApi::class)
internal data class RecordDevices(
    val host: IdentifiedDevice,
    val application: IdentifiedDevice,
    val gateway: IdentifiedDevice?,
    val gatewayUrl: String?,
    val recording: IdentifiedDevice?,
    val sources: List<IdentifiedDevice>,
    val soleSource: IdentifiedDevice?,
    val writer: IdentifiedDevice?,
    val writerHost: IdentifiedDevice?,
    val writerEntries: List<IdentifiedDevice>,
) {

    /**
     * Every Device entry, in its fixed order.
     */
    val entries: List<IdentifiedDevice>
        get() = listOfNotNull(recording) + sources + listOfNotNull(host, application, gateway) + writerEntries

    /**
     * The recording Device that recorded the peripherals' samples, which the Provenance names as the
     * composer; `null` when the record names no peripheral.
     */
    val composerUrl: String? get() = recording?.url.takeIf { sources.isNotEmpty() }

    /**
     * The Device an Observation names as the one that generated its data: the sole peripheral when it
     * supplied every sample, the recording Device otherwise.
     */
    fun observationDeviceUrl(sourceDevicesSuppliedEverySample: Boolean): String? =
        (if (sourceDevicesSuppliedEverySample) soleSource ?: recording else recording)?.url

    companion object {

        /**
         * The snapshots [draft] states under [envelope]'s converter and adapter.
         */
        fun resolve(draft: ExchangeRecordDraft, envelope: ExchangeEnvelope): Result<RecordDevices> = runCatching {
            val snapshots = DeviceSnapshots(
                identityScope = envelope.identityScope,
                subject = envelope.subject.identifier,
                adapterId = envelope.adapter.adapterId,
                applicationDeviceProfile = envelope.adapter.applicationDeviceProfile,
            )
            val host = snapshots.host(host = envelope.facts.host).getOrThrow()
            val application = snapshots.application(
                application = envelope.facts.application,
                parentUrl = host.url,
                identifiers = envelope.adapter.applicationIdentifiers(envelope.facts.application),
            ).getOrThrow()
            val statesGateway = draft.outputs.any { output ->
                output.resource is ExchangeOutputDraft.Resource.ObservationResource && ExchangeOutputDraft.Link.GATEWAY in output.links
            }
            val (gateway, gatewayUrl) = gateway(
                role = draft.converterRole,
                statesGateway = statesGateway,
                converter = envelope.facts.application,
                application = application,
                snapshots = snapshots,
                envelope = envelope,
            )
            val recording = draft.recordingDevice?.let { snapshots.recording(device = it).getOrThrow() }
            val peripherals = draft.sourceDevices.map { snapshots.recording(device = it).getOrThrow() }
            val stated = listOfNotNull(recording?.identity).toMutableSet()
            val sources = peripherals.filter { stated.add(it.identity) }
            val writer = draft.writer?.let { writer(writer = it, snapshots = snapshots, envelope = envelope) }
            val converterIdentities = setOfNotNull(host.identity, application.identity, gateway?.identity)
            RecordDevices(
                host = host,
                application = application,
                gateway = gateway,
                gatewayUrl = gatewayUrl,
                recording = recording,
                sources = sources,
                soleSource = sources.firstOrNull().takeIf { peripherals.map { it.identity }.toSet().size == 1 },
                writer = writer?.second,
                writerHost = writer?.first,
                writerEntries = writer
                    ?.let { writerEntries(application = it.second, host = it.first, stated = converterIdentities) }
                    .orEmpty(),
            )
        }

        @Suppress("detekt:LongParameterList")
        private fun gateway(
            role: ConverterRole,
            statesGateway: Boolean,
            converter: ApplicationDevice,
            application: IdentifiedDevice,
            snapshots: DeviceSnapshots,
            envelope: ExchangeEnvelope,
        ): Pair<IdentifiedDevice?, String?> = when (role) {
            ConverterRole.Assembler -> null to null
            ConverterRole.Gateway -> null to application.url
            is ConverterRole.GatewayApplication -> when {
                role.application.isSameRelease(other = converter) -> null to application.url
                !statesGateway -> null to null
                else -> snapshots.application(
                    application = role.application,
                    parentUrl = null,
                    identifiers = envelope.adapter.applicationIdentifiers(role.application),
                ).getOrThrow().let { it to it.url }
            }
        }

        private fun writer(
            writer: ExchangeWriterDraft,
            snapshots: DeviceSnapshots,
            envelope: ExchangeEnvelope,
        ): Pair<IdentifiedDevice, IdentifiedDevice> {
            val host = snapshots.host(host = writer.host).getOrThrow()
            val application = snapshots.application(
                application = writer.application,
                parentUrl = host.url,
                statesVersion = writer.statesVersion,
                identifiers = envelope.adapter.applicationIdentifiers(writer.application),
            ).getOrThrow()
            return host to application
        }

        /**
         * The writer snapshots a record carries as entries of its own: none when the converter already
         * states the application, and the host only when the converter doesn't state it.
         */
        private fun writerEntries(
            application: IdentifiedDevice,
            host: IdentifiedDevice,
            stated: Set<RoledIdentifier>,
        ): List<IdentifiedDevice> = if (application.identity in stated) {
            emptyList()
        } else {
            listOf(host).filterNot { it.identity in stated } + application
        }

        private fun ApplicationDevice.isSameRelease(other: ApplicationDevice) =
            packageName == other.packageName && version == other.version && build == other.build
    }
}
