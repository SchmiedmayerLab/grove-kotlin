//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.BlankText

/**
 * The host a conversion runs on, as its Device snapshot states it.
 *
 * An application release and its host operating system have different lifecycles, so they are two
 * Device snapshots, linked through `Device.parent`.
 *
 * @property productType the hardware product type, which `Device.modelNumber` states
 * @property model the device's model name
 */
@ConsistentCopyVisibility
data class HostDevice private constructor(
    val operatingSystemName: String?,
    val operatingSystemVersion: String,
    val operatingSystemBuild: String?,
    val productType: String?,
    val model: String?,
    val name: String?,
    val manufacturer: String?,
) {

    /**
     * A stated fact of the host is blank; [fact] names it.
     */
    data class BlankFactError(val fact: String) : Exception("The host's $fact is blank.")

    companion object {

        /**
         * The host, or a [BlankFactError] for the first stated fact that is blank.
         */
        @Suppress("detekt:LongParameterList")
        fun create(
            operatingSystemVersion: String,
            operatingSystemName: String? = null,
            operatingSystemBuild: String? = null,
            productType: String? = null,
            model: String? = null,
            name: String? = null,
            manufacturer: String? = null,
        ): Result<HostDevice> {
            val facts = listOf(
                "operating system version" to operatingSystemVersion,
                "operating system name" to operatingSystemName,
                "operating system build" to operatingSystemBuild,
                "product type" to productType,
                "model" to model,
                "name" to name,
                "manufacturer" to manufacturer,
            )
            val blank = facts.firstOrNull { (_, value) -> value != null && BlankText().isBlank(value = value) }
            return blank?.let { Result.failure(BlankFactError(fact = it.first)) } ?: Result.success(
                HostDevice(
                    operatingSystemName = operatingSystemName,
                    operatingSystemVersion = operatingSystemVersion,
                    operatingSystemBuild = operatingSystemBuild,
                    productType = productType,
                    model = model,
                    name = name,
                    manufacturer = manufacturer,
                ),
            )
        }
    }
}
