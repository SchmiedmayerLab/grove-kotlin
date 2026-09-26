//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

plugins {
    alias(libs.plugins.grove.library)
}

android {
    namespace = "org.grovealliance.firebase"

    // `FirebaseOptions.Builder` validates its arguments through `android.text.TextUtils`, which the
    // unit-test android.jar only stubs out. Without this, building options throws "not mocked".
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":core"))
    api(libs.firebase.common)

    implementation(project(":core-coroutines"))
    implementation(project(":core-logging"))

    testImplementation(project(":testing-core"))
}
