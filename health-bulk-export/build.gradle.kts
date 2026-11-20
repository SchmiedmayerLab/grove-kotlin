//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.grove.library)
    alias(libs.plugins.grove.serialization)
}

android {
    namespace = "org.grovealliance.health.export"
}

dependencies {
    api(project(":core"))
    api(project(":health"))

    implementation(project(":core-coroutines"))
    implementation(project(":core-logging"))
    implementation(project(":core-time"))
    implementation(project(":storage-local"))

    testImplementation(project(":testing-concurrency"))
    testImplementation(testFixtures(project(":core-time")))
    testImplementation(testFixtures(project(":storage-local")))
}
