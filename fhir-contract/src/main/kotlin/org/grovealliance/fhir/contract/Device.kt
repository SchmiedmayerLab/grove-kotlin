//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.JsonObjectBuilder

/**
 * A FHIR `Device`: the converting application, its host, or the hardware that recorded a measurement.
 */
@Suppress("detekt:LongParameterList")
data class Device(
    val meta: Meta? = null,
    val extension: List<Extension> = emptyList(),
    val identifier: List<Identifier> = emptyList(),
    val udiCarrier: List<UdiCarrier> = emptyList(),
    val status: String? = null,
    val type: CodeableConcept? = null,
    val manufacturer: String? = null,
    val serialNumber: String? = null,
    val deviceName: List<DeviceName> = emptyList(),
    val modelNumber: String? = null,
    val version: List<Version> = emptyList(),
    val parent: Reference? = null,
) : FhirResource {

    override val resourceType: ResourceType get() = ResourceType.DEVICE

    /**
     * One name of the device, of [type] `user-friendly-name` or `model-name`.
     */
    data class DeviceName(
        val name: String,
        val type: String,
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .string(name = "name", value = name)
            .string(name = "type", value = type)
            .build()
    }

    /**
     * One version of the device, such as its software or operating-system version, of [type].
     */
    data class Version(
        val type: CodeableConcept? = null,
        val value: String,
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .element(name = "type", value = type)
            .string(name = "value", value = value)
            .build()
    }

    /**
     * The device's UDI: its [deviceIdentifier], with the raw production identifier in an [extension].
     */
    data class UdiCarrier(
        val extension: List<Extension> = emptyList(),
        val deviceIdentifier: String? = null,
    ) : FhirElement {

        @InternalGroveFhirApi
        override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
            .elements(name = "extension", values = extension)
            .string(name = "deviceIdentifier", value = deviceIdentifier)
            .build()
    }

    @InternalGroveFhirApi
    override fun toJson(): LosslessJsonValue = JsonObjectBuilder()
        .string(name = "resourceType", value = resourceType.value)
        .element(name = "meta", value = meta)
        .elements(name = "extension", values = extension)
        .elements(name = "identifier", values = identifier)
        .elements(name = "udiCarrier", values = udiCarrier)
        .string(name = "status", value = status)
        .element(name = "type", value = type)
        .string(name = "manufacturer", value = manufacturer)
        .string(name = "serialNumber", value = serialNumber)
        .elements(name = "deviceName", values = deviceName)
        .string(name = "modelNumber", value = modelNumber)
        .elements(name = "version", values = version)
        .element(name = "parent", value = parent)
        .build()
}
