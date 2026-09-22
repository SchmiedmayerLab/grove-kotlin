//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

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
    explicitApi()
    jvmToolchain(JavaVersion.VERSION_21.majorVersion.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

tasks.withType<Test>().configureEach {
    System.getenv("GROVE_EXCHANGE_PROTOCOL_CATALOG")?.let { catalog ->
        systemProperty("grove.exchange-protocol.catalog", catalog)
    }
    System.getenv("GROVE_MOBILE_EXCHANGE_CORPUS_DIRECTORY")?.let { corpusDirectory ->
        systemProperty("grove.mobile-exchange.corpus-directory", corpusDirectory)
    }
    System.getenv("GROVE_RECEIVER_LIFECYCLE_CORPUS_DIRECTORY")?.let { corpusDirectory ->
        systemProperty("grove.receiver-lifecycle.corpus-directory", corpusDirectory)
    }
}

dependencies {
    api(libs.hl7.fhir.r4)
    // HAPI declares its JSON parser's Gson dependency optional; the graph boundary parses JSON.
    implementation(libs.google.gson)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    testImplementation(libs.kotlinx.serialization.json)
}
