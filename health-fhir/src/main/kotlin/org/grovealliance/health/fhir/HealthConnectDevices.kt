//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import android.content.Context
import android.os.Build
import org.grovealliance.fhir.ApplicationDevice
import org.grovealliance.fhir.HostDevice

/**
 * The host facts of the device this process runs on, read from [Build].
 *
 * Its `sourceDeviceToken` is `Build.MANUFACTURER|Build.MODEL|Build.VERSION.RELEASE`;
 * `Build.DEVICE` becomes the display name and is not part of it.
 */
public fun HostDevice.Companion.current(): HostDevice = HostDevice(
    operatingSystemVersion = Build.VERSION.RELEASE,
    name = Build.DEVICE,
    manufacturer = Build.MANUFACTURER,
    modelNumber = Build.MODEL,
)

/** The converter application facts of the running application, read from its package manager. */
public fun ApplicationDevice.Companion.from(context: Context): ApplicationDevice {
    val packageManager = context.packageManager
    val info = packageManager.getPackageInfo(context.packageName, 0)
    return ApplicationDevice(
        name = context.applicationInfo.loadLabel(packageManager).toString(),
        packageName = context.packageName,
        version = info.versionName ?: UNVERSIONED,
        build = info.longVersionCode.toString(),
    )
}

private const val UNVERSIONED = "0"
