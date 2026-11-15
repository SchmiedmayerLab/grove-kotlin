//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.license.internal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import kotlinx.coroutines.flow.MutableStateFlow
import org.grovealliance.resources.Strings
import org.grovealliance.testing.screenshot.ScreenshotTest
import org.grovealliance.ui.CardRow
import org.grovealliance.ui.GroveScaffoldState
import org.grovealliance.ui.ImageResource
import org.grovealliance.ui.StringResource
import org.grovealliance.ui.groveAppBar
import org.grovealliance.ui.groveSectionList
import org.junit.Test

class LicenseInformationScreenContentScreenshotTest : ScreenshotTest() {
    private val row = CardRow(
        key = "com.google.firebase:firebase-firestore",
        content = LicenseRow(
            name = StringResource("Cloud Firestore"),
            license = StringResource("Apache-2.0"),
            version = StringResource(Strings.license_information_version, "26.0.2"),
            onClick = {},
        ),
    )
    private val layout = LicenseInformationLayout(
        list = groveSectionList {
            section(key = "project") {
                card(key = "project") {
                    row(
                        row.copy(
                            key = "project",
                            content = row.content.copy(
                                name = StringResource("Example App"),
                                license = StringResource("MIT"),
                                version = StringResource(Strings.license_information_version, "1.2.0"),
                            ),
                        ),
                    )
                }
            }
            section(key = "dependencies") {
                title(
                    StringResource(
                        Strings.license_information_dependencies_header,
                        StringResource("Example App"),
                    ),
                )
                card(key = "dependencies") {
                    row(
                        row.copy(
                            key = "com.mikepenz:aboutlibraries-core",
                            content = row.content.copy(
                                name = StringResource("AboutLibraries Library"),
                                version = StringResource(Strings.license_information_version, "15.3.0"),
                            ),
                        ),
                    )
                    row(row)
                    row(
                        row.copy(
                            key = "org.grovealliance:core",
                            content = row.content.copy(
                                name = StringResource("Grove"),
                                license = StringResource("MIT"),
                                version = StringResource(Strings.license_information_version, "0.1.0"),
                            ),
                        ),
                    )
                    row(
                        row.copy(
                            key = "org.example:unlabelled",
                            content = row.content.copy(
                                name = StringResource("A library without a license label or version"),
                                license = null,
                                version = null,
                                onClick = null,
                            ),
                        ),
                    )
                    row(
                        row.copy(
                            key = "com.github.luben:zstd-jni",
                            content = row.content.copy(
                                name = StringResource("zstd-jni"),
                                license = StringResource("BSD-2-Clause"),
                                version = StringResource(Strings.license_information_version, "1.5.7-16"),
                            ),
                        ),
                    )
                }
                footer(Strings.license_information_footer)
            }
        },
        detail = MutableStateFlow(null),
        onBack = {},
    )
    private val base = LicenseInformationScreenContent(
        scaffoldState = GroveScaffoldState(
            appBar = groveAppBar {
                back {}
                title(Strings.license_information_title)
            },
        ),
        state = MutableStateFlow(LicenseInformationScreenState.Content(layout = layout)),
    )

    @Test
    fun `LicenseInformationScreenContent list`() {
        // when / then
        screenshot { base.Content() }
    }

    @Test
    fun `LicenseInformationScreenContent license text`() {
        // given
        val content = base.copy(
            scaffoldState = GroveScaffoldState(
                appBar = groveAppBar {
                    back {}
                    title(
                        content = LicenseDetailTitle(
                            title = StringResource("zstd-jni"),
                            subtitle = StringResource("github.com/luben/zstd-jni"),
                        ),
                    )
                    action(
                        imageResource = ImageResource(
                            image = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = StringResource(Strings.license_information_open_in_browser),
                        ),
                    ) {}
                },
            ),
            state = MutableStateFlow(
                LicenseInformationScreenState.Content(
                    layout = layout.copy(detail = MutableStateFlow(LicenseDetailLayout(text = StringResource(BSD_LICENSE)))),
                ),
            ),
        )

        // when / then
        screenshot { content.Content() }
    }

    private companion object {
        const val BSD_LICENSE = "BSD 2-Clause License\n\n" +
            "Redistribution and use in source and binary forms, with or without modification, are permitted " +
            "provided that the following conditions are met:\n\n" +
            "1. Redistributions of source code must retain the above copyright notice, this list of conditions " +
            "and the following disclaimer.\n\n" +
            "2. Redistributions in binary form must reproduce the above copyright notice, this list of " +
            "conditions and the following disclaimer in the documentation and/or other materials provided " +
            "with the distribution.\n\n" +
            "THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ANY EXPRESS OR " +
            "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND " +
            "FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED."
    }
}
