//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

/**
 * A [LicenseInformationSource] returning the result set through [setResult], a project without
 * dependencies by default, and counting how often it was read.
 */
internal class FakeLicenseInformationSource : LicenseInformationSource {
    private var result: Result<LicenseInformation> = Result.success(
        LicenseInformation(
            project = LicenseProjectFixtures.create(),
            dependencies = emptyList(),
        ),
    )

    /**
     * How often the information was read.
     */
    var reads = 0
        private set

    /**
     * Makes every following read return [result].
     */
    fun setResult(result: Result<LicenseInformation>) {
        this.result = result
    }

    override suspend fun information(): Result<LicenseInformation> {
        reads++
        return result
    }
}
