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
    namespace = "org.grovealliance.health.fhir"

    buildTypes {
        debug {
            // The FHIR reference implementation jars cannot be instrumented by JaCoCo.
            enableAndroidTestCoverage = false
        }
    }

    testOptions {
        unitTests.all { test ->
            test.extensions.configure<org.gradle.testing.jacoco.plugins.JacocoTaskExtension> {
                // The reference implementation ships parser classes beyond JaCoCo's JVM class
                // size ceiling. They are a dependency, not producer code, so do not transform them.
                excludes = (excludes.orEmpty() + "org.hl7.fhir.*").distinct()
            }
            val defaultExport = layout.buildDirectory.dir("conformance-fixtures").get().asFile.absolutePath
            test.systemProperty("grove.conformance.export", System.getenv("GROVE_CONFORMANCE_EXPORT") ?: defaultExport)
            val defaultWireExport = layout.buildDirectory.dir("wire-fixtures").get().asFile.absolutePath
            test.systemProperty("grove.wire.export", System.getenv("GROVE_WIRE_EXPORT") ?: defaultWireExport)
            val defaultCapabilityExport = layout.buildDirectory
                .file("producer-capabilities/health-connect.json")
                .get()
                .asFile
                .absolutePath
            test.systemProperty("grove.capability.export", System.getenv("GROVE_CAPABILITY_EXPORT") ?: defaultCapabilityExport)
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":fhir-contract"))
    api(libs.androidx.health.connect.client)

    // connect-testing currently declares a 1.2 alpha client transitively. Keep the FHIR producer's
    // tests on the exact stable 1.1.0 API that the library compiles and claims in its capability
    // manifest, so an alpha-only field cannot silently enter the supported source contract.
    testImplementation(libs.androidx.health.connect.testing) {
        exclude(group = "androidx.health.connect", module = "connect-client")
        exclude(group = "androidx.health.connect", module = "connect-client-proto")
        exclude(group = "androidx.health.connect", module = "connect-client-external-protobuf")
    }
    testImplementation(libs.kotlinx.serialization.json)
}
