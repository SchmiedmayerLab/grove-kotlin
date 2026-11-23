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

    testOptions {
        unitTests.all { test ->
            val fixtures = layout.buildDirectory.dir("conformance-fixtures/health-connect").get().asFile
            test.systemProperty("grove.conformance.export", System.getenv("GROVE_CONFORMANCE_EXPORT") ?: fixtures.absolutePath)
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addStaticSourceDirectory("src/generated/kotlin")
    }
}

kotlin {
    compilerOptions {
        optIn.add("org.grovealliance.fhir.contract.InternalGroveFhirApi")
    }
}

dependencies {
    api(project(":fhir-contract"))
    api(libs.androidx.health.connect.client)

    // connect-testing brings a newer connect-client; the tests run on the exact release the contract names.
    testImplementation(libs.androidx.health.connect.testing) {
        exclude(group = "androidx.health.connect", module = "connect-client")
        exclude(group = "androidx.health.connect", module = "connect-client-proto")
        exclude(group = "androidx.health.connect", module = "connect-client-external-protobuf")
    }
}
