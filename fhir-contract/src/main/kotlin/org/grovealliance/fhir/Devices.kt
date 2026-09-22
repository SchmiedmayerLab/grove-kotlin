//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir

import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.Device

/** The application that saved, routed or converted the record, as an immutable event-time snapshot. */
public class ApplicationDevice(
    public val name: String,
    public val packageName: String,
    public val version: String,
    public val build: String? = null,
) {
    init {
        requireNonblankScalar(name, "Application name")
        requireNonblankScalar(packageName, "Application package name")
        require(packageName.none(Char::isWhitespace)) { "An application package name carries no whitespace." }
        requireNonblankScalar(version, "Application version")
        build?.let { requireNonblankScalar(it, "Application build") }
    }

    /** The source-device token of this application's event snapshot. */
    internal val sourceDeviceToken: String
        get() = listOfNotNull(packageName, version, build).joinToString("|")

    internal fun resource(): Device = Device().apply {
        meta.addProfile(ExchangeContract.MOBILE_APPLICATION_DEVICE_PROFILE)
        addDeviceName().setName(name).setType(Device.DeviceNameType.USERFRIENDLYNAME)
        addVersion()
            .setType(CodeableConcept(Coding(ExchangeContract.MDC, ExchangeContract.APPLICATION_SOFTWARE_VERSION, null)))
            .setValue(this@ApplicationDevice.version)
        build?.let {
            addVersion()
                .setType(CodeableConcept(Coding(ExchangeContract.GROVE_APPLICATION_VERSION_TYPE, BUILD_VERSION_CODE, null)))
                .setValue(it)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ApplicationDevice && name == other.name && packageName == other.packageName &&
            version == other.version && build == other.build

    override fun hashCode(): Int = listOf(name, packageName, version, build).hashCode()

    override fun toString(): String =
        "ApplicationDevice(name=$name, packageName=$packageName, version=$version, build=$build)"

    public companion object {
        private const val BUILD_VERSION_CODE = "build"
    }
}

/** The host hardware and operating system the conversion ran on, as an immutable event-time snapshot. */
public class HostDevice(
    public val operatingSystemVersion: String,
    public val name: String? = null,
    public val manufacturer: String? = null,
    public val modelNumber: String? = null,
) {
    init {
        requireNonblankScalar(operatingSystemVersion, "Host operating system version")
        name?.let { requireNonblankScalar(it, "Host name") }
        manufacturer?.let { requireNonblankScalar(it, "Host manufacturer") }
        modelNumber?.let { requireNonblankScalar(it, "Host model number") }
    }

    /** The source-device token of this host's event snapshot. */
    internal val sourceDeviceToken: String
        get() = "${manufacturer.orEmpty()}|${modelNumber.orEmpty()}|$operatingSystemVersion"

    internal fun resource(): Device = Device().apply {
        meta.addProfile(ExchangeContract.MOBILE_HOST_DEVICE_PROFILE)
        name?.let { addDeviceName().setName(it).setType(Device.DeviceNameType.USERFRIENDLYNAME) }
        this@HostDevice.manufacturer?.let { manufacturer = it }
        this@HostDevice.modelNumber?.let { modelNumber = it }
        addVersion()
            .setType(
                CodeableConcept(
                    Coding(ExchangeContract.GROVE_APPLICATION_VERSION_TYPE, OPERATING_SYSTEM_VERSION_CODE, null),
                ),
            )
            .setValue(operatingSystemVersion)
    }

    override fun equals(other: Any?): Boolean =
        other is HostDevice && operatingSystemVersion == other.operatingSystemVersion && name == other.name &&
            manufacturer == other.manufacturer && modelNumber == other.modelNumber

    override fun hashCode(): Int = listOf(operatingSystemVersion, name, manufacturer, modelNumber).hashCode()

    override fun toString(): String =
        "HostDevice(operatingSystemVersion=$operatingSystemVersion, name=$name, " +
            "manufacturer=$manufacturer, modelNumber=$modelNumber)"

    public companion object {
        private const val OPERATING_SYSTEM_VERSION_CODE = "os-version"
    }
}

/** The physical device that acquired a measurement, established by a governed stable per-unit token. */
public class RecordingDevice(
    public val stableUnitToken: String,
    public val name: String? = null,
    public val manufacturer: String? = null,
    public val modelNumber: String? = null,
) {
    init {
        requireNonblankScalar(stableUnitToken, "Recording device stable unit token")
        name?.let { requireNonblankScalar(it, "Recording device name") }
        manufacturer?.let { requireNonblankScalar(it, "Recording device manufacturer") }
        modelNumber?.let { requireNonblankScalar(it, "Recording device model number") }
    }

    internal fun resource(): Device = Device().apply {
        meta.addProfile(ExchangeContract.MOBILE_RECORDING_DEVICE_PROFILE)
        name?.let { addDeviceName().setName(it).setType(Device.DeviceNameType.USERFRIENDLYNAME) }
        this@RecordingDevice.manufacturer?.let { manufacturer = it }
        this@RecordingDevice.modelNumber?.let { modelNumber = it }
    }

    override fun equals(other: Any?): Boolean =
        other is RecordingDevice && stableUnitToken == other.stableUnitToken && name == other.name &&
            manufacturer == other.manufacturer && modelNumber == other.modelNumber

    override fun hashCode(): Int = listOf(stableUnitToken, name, manufacturer, modelNumber).hashCode()

    override fun toString(): String =
        "RecordingDevice(stableUnitToken=<redacted>, name=$name, manufacturer=$manufacturer, modelNumber=$modelNumber)"
}

/** How the converter application participated in the measurement. */
public sealed interface ConverterRole {
    /** The application only assembled the graph. */
    public data object Assembler : ConverterRole

    /** The application itself mediated the measurement and is the output's gateway device. */
    public data object Gateway : ConverterRole

    /** A distinct application mediated the measurement; it is emitted as a second application snapshot. */
    public data class GatewayApplication(public val application: ApplicationDevice) : ConverterRole
}

/** The nodes of one exchange graph a repository may assign a `Resource.id` to. */
public enum class ExchangeGraphNode {
    BUNDLE,
    PRIMARY_OUTPUT,
    SOURCE_ARTIFACT,
    RECORDING_DEVICE,
    APPLICATION_DEVICE,
    HOST_DEVICE,
    SOURCE_AUTHOR,
    SOURCE_AUTHOR_HOST,
    PROVENANCE,
}

internal fun requireNonblankScalar(value: String, label: String) {
    require(ExchangeProtocol.isScalarText(value)) { "$label contains an unpaired UTF-16 surrogate." }
    require(value.isNotBlank()) { "$label must not be blank." }
}
