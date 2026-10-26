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
    namespace = "org.grovealliance.storage.local"
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
    testFixtures { enable = true }
}

dependencies {
    api(project(":core"))
    implementation(project(":foundation"))
    implementation(project(":core-coroutines"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto.ktx)
    testFixturesImplementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.bundles.integration.testing)
}
