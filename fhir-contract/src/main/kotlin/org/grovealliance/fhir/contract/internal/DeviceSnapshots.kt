//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract.internal

import org.grovealliance.fhir.contract.ApplicationDevice
import org.grovealliance.fhir.contract.BusinessIdentifier
import org.grovealliance.fhir.contract.CodeSystemUrl
import org.grovealliance.fhir.contract.CodeableConcept
import org.grovealliance.fhir.contract.Coding
import org.grovealliance.fhir.contract.Device
import org.grovealliance.fhir.contract.Extension
import org.grovealliance.fhir.contract.ExtensionUrl
import org.grovealliance.fhir.contract.HostDevice
import org.grovealliance.fhir.contract.Identifier
import org.grovealliance.fhir.contract.InternalGroveFhirApi
import org.grovealliance.fhir.contract.Meta
import org.grovealliance.fhir.contract.NodeRole
import org.grovealliance.fhir.contract.OpaqueIdentityScope
import org.grovealliance.fhir.contract.Profile
import org.grovealliance.fhir.contract.RecordingDevice
import org.grovealliance.fhir.contract.Reference

/**
 * Builds and keys the Device snapshots of one record's [subject] under [identityScope]: the converting
 * application, its host, and the hardware that recorded the record.
 *
 * Each body is final before it is keyed, and a snapshot is keyed after every node it references, so
 * a changed host changes the application snapshot that names it.
 */
@OptIn(InternalGroveFhirApi::class)
internal class DeviceSnapshots(
    private val identityScope: OpaqueIdentityScope,
    private val subject: BusinessIdentifier,
    private val adapterId: String,
    private val applicationDeviceProfile: String,
) {
    private val blank = BlankText()

    /**
     * The application Device: its profile, status, name, version and build, the host it runs on as
     * [parentUrl], and the [identifiers] the adapter states for it. Without [statesVersion], the
     * marketing version is left out, as a source that doesn't state it.
     */
    fun application(
        application: ApplicationDevice,
        parentUrl: String?,
        statesVersion: Boolean = true,
        identifiers: List<Identifier> = emptyList(),
    ): Result<IdentifiedDevice> {
        val versions = listOfNotNull(
            mdcVersion(code = SOFTWARE_VERSION, display = SOFTWARE_VERSION_DISPLAY, value = application.version).takeIf { statesVersion },
            application.build?.let { groveVersion(code = "build", display = "Build", value = it) },
        )
        val body = Device(
            meta = Meta(profile = listOf(applicationDeviceProfile)),
            identifier = identifiers,
            status = ACTIVE,
            deviceName = listOf(Device.DeviceName(name = application.name, type = USER_FRIENDLY_NAME)),
            version = versions,
            parent = parentUrl?.let { Reference(reference = it) },
        )
        return key(body = body, role = NodeRole.Application)
    }

    /**
     * The host Device: its profile, status, names, manufacturer, product type and operating system.
     */
    fun host(host: HostDevice): Result<IdentifiedDevice> {
        val versions = listOfNotNull(
            groveVersion(code = "os-version", display = "Operating system version", value = host.operatingSystemVersion),
            host.operatingSystemName?.let { groveVersion(code = "os-name", display = "Operating system name", value = it) },
            host.operatingSystemBuild?.let { groveVersion(code = "os-build", display = "Operating system build", value = it) },
        )
        val body = Device(
            meta = Meta(profile = listOf(Profile.Mobile.GROVE_HOST_DEVICE)),
            status = ACTIVE,
            deviceName = names(name = host.name, modelName = host.model),
            manufacturer = host.manufacturer,
            modelNumber = host.productType,
            version = versions,
        )
        return key(body = body, role = NodeRole.Host)
    }

    /**
     * The recording Device: every fact [device] states, then its unit identity when it has a stable
     * unit token, then its key.
     */
    fun recording(device: RecordingDevice): Result<IdentifiedDevice> {
        val model = blank.nonBlank(value = device.model)
        val productType = blank.nonBlank(value = device.productType)
        val localIdentifier = blank.nonBlank(value = device.localIdentifier)?.let {
            Identifier(
                system = identityScope.systems.deviceLocalIdentifier.rawValue,
                value = it,
                type = CodeableConcept(
                    coding = listOf(Coding(system = CodeSystemUrl.Mobile.GROVE_DEVICE_IDENTIFIER_TYPE, code = "device-local-identifier")),
                ),
            )
        }
        val unit = blank.nonBlank(value = device.stableUnitToken)?.let { token ->
            identityScope.recordingDevice(
                adapterId = adapterId,
                subject = subject,
                tokenScope = device.tokenScope.orEmpty(),
                stableUnitToken = token,
            ).getOrElse { return Result.failure(it) }.fhirIdentifier
        }
        val body = Device(
            meta = Meta(profile = listOf(Profile.Mobile.GROVE_RECORDING_DEVICE)),
            identifier = listOfNotNull(unit, localIdentifier),
            udiCarrier = listOfNotNull(udiCarrier(device = device)),
            status = ACTIVE,
            type = blank.nonBlank(value = device.deviceType)?.let { CodeableConcept(text = it) },
            manufacturer = blank.nonBlank(value = device.manufacturer),
            serialNumber = blank.nonBlank(value = device.serialNumber),
            deviceName = names(name = blank.nonBlank(value = device.name), modelName = model.takeIf { productType != null }),
            modelNumber = productType ?: model,
            version = recordingVersions(device = device),
        )
        return key(body = body, role = NodeRole.RecordingDevice)
    }

    private fun key(body: Device, role: NodeRole): Result<IdentifiedDevice> = identityScope.contentSnapshot(
        node = body,
        subject = subject,
        role = role,
    ).map { identity ->
        IdentifiedDevice(
            resource = body.copy(identifier = listOf(identity.fhirIdentifier) + body.identifier),
            identity = identity,
            url = identity.fullUrl,
        )
    }

    private fun recordingVersions(device: RecordingDevice): List<Device.Version> = listOfNotNull(
        blank.nonBlank(value = device.hardwareVersion)?.let { mdcVersion(code = "531974", display = "MDC_ID_PROD_SPEC_HW", value = it) },
        blank.nonBlank(value = device.firmwareVersion)?.let { mdcVersion(code = "531976", display = "MDC_ID_PROD_SPEC_FW", value = it) },
        blank.nonBlank(value = device.softwareVersion)?.let {
            mdcVersion(code = SOFTWARE_VERSION, display = SOFTWARE_VERSION_DISPLAY, value = it)
        },
        blank.nonBlank(value = device.systemName)?.let { groveVersion(code = "os-name", display = "Operating system name", value = it) },
        blank.nonBlank(value = device.systemVersion)?.let {
            groveVersion(code = "os-version", display = "Operating system version", value = it)
        },
    )

    /**
     * `Device.udiCarrier`; R4 has no element for the raw production identifier, so an extension carries it.
     */
    private fun udiCarrier(device: RecordingDevice): Device.UdiCarrier? {
        val identifier = blank.nonBlank(value = device.udiDeviceIdentifier)
        val production = blank.nonBlank(value = device.udiProductionIdentifier)
        if (identifier == null && production == null) return null
        return Device.UdiCarrier(
            extension = listOfNotNull(
                production?.let {
                    Extension(url = ExtensionUrl.Mobile.GROVE_UDI_PRODUCTION_IDENTIFIER, value = Extension.Value.StringValue(value = it))
                },
            ),
            deviceIdentifier = identifier,
        )
    }

    private fun names(name: String?, modelName: String?): List<Device.DeviceName> = listOfNotNull(
        name?.let { Device.DeviceName(name = it, type = USER_FRIENDLY_NAME) },
        modelName?.let { Device.DeviceName(name = it, type = "model-name") },
    )

    private fun mdcVersion(code: String, display: String, value: String) = version(
        code = code,
        display = display,
        system = MDC,
        value = value,
    )

    private fun groveVersion(code: String, display: String, value: String) = version(
        code = code,
        display = display,
        system = CodeSystemUrl.Mobile.GROVE_APPLICATION_VERSION_TYPE,
        value = value,
    )

    private fun version(code: String, display: String, system: String, value: String) = Device.Version(
        type = CodeableConcept(
            coding = listOf(
                Coding(
                    system = system,
                    code = code,
                    display = display,
                ),
            ),
        ),
        value = value,
    )

    private companion object {
        const val ACTIVE = "active"
        const val USER_FRIENDLY_NAME = "user-friendly-name"
        const val SOFTWARE_VERSION = "531975"
        const val SOFTWARE_VERSION_DISPLAY = "MDC_ID_PROD_SPEC_SW"
        const val MDC = "urn:iso:std:iso:11073:10101"
    }
}
