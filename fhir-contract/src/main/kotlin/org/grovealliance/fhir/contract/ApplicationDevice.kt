//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.fhir.contract

import org.grovealliance.fhir.contract.internal.BlankText

/**
 * The converting application, as its Device snapshot states it: its [name], the [packageName] that
 * identifies the product, its marketing [version] and, separately, its [build].
 */
@ConsistentCopyVisibility
data class ApplicationDevice private constructor(
    val name: String,
    val packageName: String,
    val version: String,
    val build: String?,
) {

    /**
     * Why an application can't be named by a Device snapshot.
     */
    sealed class ValidationError(message: String) : Exception(message) {

        /**
         * The name is blank.
         */
        data object BlankName : ValidationError("The application name is blank.")

        /**
         * [packageName] is not dot-separated, non-empty labels of ASCII letters, digits, `-` and `_`.
         */
        data class InvalidPackageName(val packageName: String) : ValidationError("$packageName is not a package name.")

        /**
         * The version is blank.
         */
        data object BlankVersion : ValidationError("The application version is blank.")

        /**
         * The build is stated but blank.
         */
        data object BlankBuild : ValidationError("The application build is blank.")
    }

    companion object {

        /**
         * The application, or the [ValidationError] of the first fact a Device snapshot can't state.
         */
        fun create(
            name: String,
            packageName: String,
            version: String,
            build: String? = null,
        ): Result<ApplicationDevice> {
            val blank = BlankText()
            val error = when {
                blank.isBlank(value = name) -> ValidationError.BlankName
                !isValidPackageName(value = packageName) -> ValidationError.InvalidPackageName(packageName = packageName)
                blank.isBlank(value = version) -> ValidationError.BlankVersion
                build != null && blank.isBlank(value = build) -> ValidationError.BlankBuild
                else -> null
            }
            return error?.let { Result.failure(it) } ?: Result.success(
                ApplicationDevice(
                    name = name,
                    packageName = packageName,
                    version = version,
                    build = build,
                ),
            )
        }

        private fun isValidPackageName(value: String): Boolean =
            value.split('.').all { label ->
                label.isNotEmpty() && label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
            }
    }
}
