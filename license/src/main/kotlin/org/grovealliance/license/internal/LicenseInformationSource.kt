//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import android.content.Context
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.util.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.grovealliance.core.DependenciesGraph
import org.grovealliance.core.coroutines.runCatchingAsync
import org.grovealliance.license.Dependency
import org.grovealliance.license.LicenseConfiguration
import org.grovealliance.license.LicenseProject

/**
 * The app and the third-party software it is built with.
 */
internal interface LicenseInformationSource {

    /**
     * The configured project and its dependencies.
     */
    suspend fun information(): Result<LicenseInformation>
}

/**
 * The app and its dependencies, in display order.
 */
internal data class LicenseInformation(
    val project: LicenseProject,
    val dependencies: List<Dependency>,
)

/**
 * [LicenseInformationSource] taking the project from the [configuration], resolved against the
 * [graph], and the dependencies from it too when configured, or else from the list generated into the app's resources at build
 * time, sorted by name regardless of case.
 */
internal class LicenseInformationSourceImpl(
    private val context: Context,
    private val graph: DependenciesGraph,
    private val configuration: LicenseConfiguration,
    private val ioDispatcher: CoroutineDispatcher,
) : LicenseInformationSource {

    override suspend fun information(): Result<LicenseInformation> = runCatchingAsync {
        LicenseInformation(
            project = configuration.project(graph),
            dependencies = configuration.dependencies?.invoke(graph) ?: generatedDependencies(),
        )
    }

    private suspend fun generatedDependencies(): List<Dependency> = withContext(ioDispatcher) {
        Libs.Builder()
            .withContext(context)
            .build()
            .libraries
            .map { dependency(library = it) }
            .sortedBy { it.name.lowercase() }
    }

    private fun dependency(library: Library) = Dependency(
        id = library.uniqueId,
        name = library.name,
        version = library.artifactVersion,
        licenseIdentifier = library.licenses.firstNotNullOfOrNull { it.spdxId?.takeIf(String::isNotBlank) },
        licenseText = library.licenses
            .mapNotNull { it.licenseContent?.takeIf(String::isNotBlank) }
            .joinToString(separator = "\n\n")
            .ifEmpty { null },
        url = webUrl(url = library.scm?.url) ?: webUrl(url = library.website),
    )

    private fun webUrl(url: String?): String? = url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}
