//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.grove.library)
    alias(libs.plugins.grove.compose)
    alias(libs.plugins.grove.serialization)
}

android {
    namespace = "org.grovealliance.health"
    testFixtures { enable = true }
}

dependencies {
    api(libs.androidx.health.connect.client)
    api(project(":core"))
    api(project(":ui"))
    api(libs.androidx.fragment.ktx)
    implementation(project(":core-coroutines"))
    implementation(project(":core-lifecycle"))
    implementation(project(":core-time"))
    implementation(project(":storage-local"))
    implementation(libs.androidx.appcompat)

    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.runtime)
    testFixturesImplementation(libs.bundles.ktx.coroutines)

    testImplementation(project(":testing-concurrency"))
    testImplementation(testFixtures(project(":core-time")))
    testImplementation(testFixtures(project(":storage-local")))
    testImplementation(libs.androidx.health.connect.testing)
}
