//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

group = providers.gradleProperty("grove.group").get()
version = providers.gradleProperty("grove.version").get()

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(JavaVersion.VERSION_21.majorVersion.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        optIn.add("org.grovealliance.fhir.contract.InternalGroveFhirApi")
    }
}

// Written by Scripts/sync-grove-fhir.py from the pinned grove-fhir release; outside src/main, so detekt
// never reformats it into drift.
sourceSets.main {
    kotlin.srcDir("src/generated/kotlin")
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.kotlinx.serialization.json)
}
