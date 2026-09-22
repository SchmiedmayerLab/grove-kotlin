# Module fhir-contract

<!--

This source file is part of the Grove open-source project

SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)

SPDX-License-Identifier: MIT

-->

`fhir-contract` is the Kotlin form of the Grove Mobile exchange contract: the identities, the event context, the graph assembler and the validator every Kotlin producer shares.
It has no Android dependency.
`health-fhir` builds on it, and a provider or questionnaire adapter would build on it the same way.

## Why this exists

A source record becomes one immutable, self-describing FHIR Bundle, the exchange graph, that any receiver can deduplicate, correct and retract without knowing the source platform.
This module holds what makes that possible independently of the platform: HMAC identities that never leak a native id, deterministic entry keys and full URLs, the provenance that names the assembling application on its host, optional study context, and the rules a graph must satisfy.
Every rule is a registered producer diagnostic of the grove-fhir catalog, generated into `ExchangeGraphRule` with its reason and severity.

## What it holds

### Identity

`DeploymentIdentifierSystems.derived` names the twelve identifier systems of one deployment root, key id and epoch.
`OpaqueIdentityScope` mints every opaque identity under them with one HMAC-SHA-256 key; the published conformance key is admitted only through `forConformanceTesting`.
`ExchangeEventIdentifier` and `EntryNodeKey` are the clear event and entry-node identities, and `BusinessIdentifier` with `RoledIdentifier` carry any of them into FHIR.

### Context

`ExchangeEventContext` is everything one event shares: the subject, the event identifier, the scope, the repository scope, the application, the host, the conversion instant, the converter role, the study enrollments and the repository ids.
The entry-node system is derived from the scope, and the conversion instant defaults to now.
The host is explicit here because a plain JVM module cannot read the device it runs on; an adapter such as `health-fhir` supplies that default.

### Assembly

`ExchangeGraphAssembler` builds the device snapshots, the subject and study entries and the conversion provenance, decorates every output with its subject, device and study references, and closes one validated `ExchangeGraph`.
It is internal API for adapters and requires `InternalGroveFhirApi`.

```kotlin
val assembler = ExchangeGraphAssembler(context, adapterId = "questionnaire", recordingDevice = null)
val output = GraphEntry(outputIdentity, observation.also(assembler::decorate))
val provenance = assembler.conversionProvenance(profile, sourceRecord, listOf(output), occurred)
val graph = assembler.activeGraph(listOf(output), provenance)
```

### Verification

`ExchangeGraph.parse` validates any Bundle against the registered rules and returns `Valid` with the graph or `Invalid` with exactly one diagnostic.
`semanticallyEquals` compares two graphs the way the receiver-lifecycle corpus does, and `retractionTargets` derives the targets a `RetractionEvent` must name.

```kotlin
when (val result = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, json)) {
    is ExchangeGraphParseResult.Valid -> result.graph.semanticallyEquals(stored)
    is ExchangeGraphParseResult.Invalid -> log(result.error.diagnostic)
}
```

The tests run the normative vectors on their own and the mobile-exchange and receiver-lifecycle corpora when `GROVE_EXCHANGE_PROTOCOL_CATALOG`, `GROVE_MOBILE_EXCHANGE_CORPUS_DIRECTORY` and `GROVE_RECEIVER_LIFECYCLE_CORPUS_DIRECTORY` point into a grove-fhir checkout.

## Glossary

| IG term | Kotlin type |
| --- | --- |
| Exchange event | `ExchangeEventIdentifier` with its `ExchangeEventContext` |
| Exchange graph | `ExchangeGraph` |
| Business identifier | `BusinessIdentifier` |
| Identifier role | `GroveIdentifierRole` on a `RoledIdentifier` |
| Opaque identity | `OpaqueIdentityScope` minting `OpaqueIdentityKind` under `DeploymentIdentifierSystems` |
| Entry-node key | `EntryNodeKey` |
| Subject | `Subject.Logical` or `Subject.Bundled` |
| Study enrollment | `StudyEnrollment` |
| Application, host and recording device | `ApplicationDevice`, `HostDevice`, `RecordingDevice` |
| Writer | The writer-record identity minted by `OpaqueIdentityScope.writerRecord` |
| Retraction event and target | `RetractionEvent`, `RetractionTarget`, `RetractionTargetRole` |
| Governed source identifier | `GovernedSourceIdentifierDisclosurePolicy` |
| Producer diagnostic | `ExchangeGraphRule` and `ExchangeGraphDiagnostic` |

# Package org.grovealliance.fhir

The exchange contract: identities and their systems, the event context, the graph assembler, the parsed exchange graph and its registered rules.
