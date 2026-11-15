//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.grove.library)
    alias(libs.plugins.grove.compose)
}

android {
    namespace = "org.grovealliance.license"
}

dependencies {
    api(project(":core"))
    api(project(":ui"))

    implementation(project(":core-coroutines"))
    implementation(project(":core-logging"))
    implementation(project(":core-viewmodel"))
    implementation(libs.aboutlibraries.core)

    testImplementation(project(":testing-core"))
    testImplementation(project(":testing-screenshot"))
}
