# Module health-fhir

<!--

This source file is part of the Grove open-source project

SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)

SPDX-License-Identifier: MIT

-->

`health-fhir` turns AndroidX Health Connect records into Grove Mobile exchange graphs.
If you already know the pieces, jump to [Beyond the minimum](#beyond-the-minimum).

## Why this exists

A Health Connect record is a row in a store that only the phone it lives on can read.
This module turns one such record into one immutable, self-describing FHIR Bundle, the exchange graph, that any receiver can deduplicate, correct and retract without knowing anything about Health Connect.
Plain FHIR does not say whether two uploads are the same record, who assembled them, or which study they were collected for; Grove adds stable identities that never leak the native record id, provenance naming the application and the device that assembled the graph, and optional study context.
A receiver gets a graph it can store, compare byte for byte on a retry, supersede by version and retract by identity.

## What you need and why

Five inputs have no default because each one is a decision only your deployment can make.
They are assembled in this order.

### The subject pseudonym

A `Subject.Logical` is the pseudonymous identifier your deployment assigned to the participant: a system URI you own and a value that is neither a name, an account nor a device id.
Every output names its subject, so a receiver can group records per participant without learning who they are.
It comes from your enrollment or account service.
Persist it with the account: a participant who reinstalls the app must keep the same pseudonym, or the receiver sees two people.

### The identity scope

An `OpaqueIdentityScope` mints every Grove identity: the source record, each output, the writer's record and each device snapshot.
It holds an HMAC-SHA-256 key, the key id, the key epoch and the twelve identifier systems derived from your deployment root.
Why HMAC: the same record exported twice yields the same identifier, so a receiver deduplicates, yet nobody can recover the native record id from it.
The key comes from your key management, for example an Android Keystore HMAC key; the systems come from `DeploymentIdentifierSystems.derived` over your deployment root.
Persist the key id and the epoch next to the key.
Rotating either one opens a new identity space, and the old one must stay available for as long as its graphs can be replayed or retracted.

### The event identifier

An `ExchangeEventIdentifier` names one export: the producer instance, a UUID this installation generated once, and a monotonic sequence.
Every export is an immutable event.
A retry resends the same bytes under the same identifier; a new revision of the record gets the next sequence.
The producer instance comes from your installation state; the sequence is a counter you reserve before you convert.
Persist both, because a receiver cannot repair a sequence that was reused for different content.

### The repository scope

A `BusinessIdentifier` names the Health Connect store the record was read from: a system URI you own and one stable token per installation.
Two stores must never collide: the same record id on two phones, or after a reinstall that regenerated the token, must yield different source-record identities.
The token comes from your installation state; the system is a URI under your deployment root.
Persist the token with the producer instance, and decide deliberately whether a reinstall keeps it.

### The application

An `ApplicationDevice` names the app that assembled the graph: its name, package name and version.
The conversion provenance names this assembler, so a receiver can tell which build produced a graph.
`ApplicationDevice.from(context)` reads it from the package manager on every export; there is nothing to persist.

Health Connect adds one decision the shared defaults leave open: whether user-authored session titles and notes leave the device.
`HealthConnectConversionOptions` takes that policy explicitly; everything else in it defaults to omit.

> Note: `host` and `conversionInstant` have defaults.
> The host is read from `Build` through `HostDevice.current()` and the conversion instant is `Instant.now()`.
> Pass them explicitly when you convert on behalf of another device or replay a conversion at a fixed instant, as the conformance tests do.

## Assemble it

Once per installation: derive the systems, create the scope and read the application.
`identityKey` is your HMAC key, `keyId` and `keyEpoch` its persisted id and epoch, and `context` your Android context.

```kotlin
val systems = DeploymentIdentifierSystems.derived(
    root = IdentifierSystem("https://study.example.org/fhir"),
    keyId = keyId,
    epoch = keyEpoch,
)
val scope = OpaqueIdentityScope(systems = systems, keyId = keyId, epoch = keyEpoch, key = identityKey)
val application = ApplicationDevice.from(context)
```

Per export: reserve the next sequence and create the context, using every default.

```kotlin
val event = ExchangeEventIdentifier(systems.event, producerInstance, nextSequence)
val context = HealthConnectConversionContext(
    subject = subject,
    event = event,
    identityScope = scope,
    repositoryScope = repositoryScope,
    application = application,
    options = HealthConnectConversionOptions(userAuthoredText = UserAuthoredTextPolicy.OMIT),
)
```

Convert one record and hand the Bundle to your uploader.

```kotlin
when (val result = HealthConnectConverter().convert(record, context)) {
    is HealthConnectConversionResult.Converted -> upload(result.conversion.graph.json.toByteArray())
    is HealthConnectConversionResult.NoOutput -> Unit
    is HealthConnectConversionResult.Failed -> log(result.failure.diagnostic)
}
```

The three blocks are compiled as `HealthConnectWalkthrough` in the module's tests, so they cannot drift from the API.

| What to persist | Why |
| --- | --- |
| The event sequence | The next export must use a sequence no earlier export used, across process restarts. |
| The key id and epoch | They are part of every identifier system; the scope must mint under the same ones the receiver already knows. |
| The producer instance id | It is half of every event identifier; a new one makes every later event look like another producer. |
| The repository scope token | It is part of every source-record identity; a new one makes every record look new. |

> Important: Never reuse a sequence for different content.
> A retry is the same bytes under the same identifier; anything else is a new revision under the next sequence.

> Important: Never change the key or the epoch without deriving the systems again.
> The systems carry the key id and epoch; a scope whose key changed under unchanged systems mints identifiers that match nothing a receiver holds and collide with nothing it can detect.

## Beyond the minimum

### Study enrollment

When the participant's enrollment is known, pass it and the graph bundles the ResearchStudy, its exact-revision PlanDefinition and one ResearchSubject as entry-node keyed entries; every output then names the study.
A bundled Patient replaces the logical pseudonym with `Subject.Bundled`.

```kotlin
val enrollment = StudyEnrollment(study = studyIdentifier, protocolUrl = protocolUrl, protocolVersion = "2026.08", enrollment = enrollmentIdentifier)
HealthConnectConversionContext(subject, event, scope, repositoryScope, application, options, studies = listOf(enrollment))
```

See [StudyEnrollment](https://grovealliance.org/fhir/mobile/study.html) in the Mobile guide.

### Disclosure policies

Every disclosure defaults to omit.
`routeDisclosure` keeps exercise routes off the graph, `nativeIdentifierDisclosure` keeps the Health Connect record id out of every identifier, and `recordingDevice` never names a physical device because Health Connect supplies no stable per-unit token.
A deployment that governs its own namespace may pass `GovernedSourceIdentifierDisclosurePolicy.Authorized(system)`; the system must not be one of the twelve Grove systems.

```kotlin
HealthConnectConversionOptions(
    userAuthoredText = UserAuthoredTextPolicy.RETAIN,
    recordingDevice = RecordingDeviceResolver { device -> registry.stableUnitToken(device)?.let { RecordingDevice(it, name = device.model) } },
    nativeIdentifierDisclosure = GovernedSourceIdentifierDisclosurePolicy.Authorized(IdentifierSystem("https://study.example.org/fhir/identifiers/health-connect-records")),
)
```

### Repository ids

A receiver that assigns its own resource ids may tell the producer which id to write on the Bundle, the Provenance or a device snapshot through `repositoryIds`; nothing in the identities depends on them.

```kotlin
repositoryIds = mapOf(ExchangeGraphNode.BUNDLE to RepositoryId("event-44"))
```

### A distinct gateway application

When a companion app wrote the record and your app only assembled the graph, name the companion as the gateway and the provenance keeps both apart.

```kotlin
converterRole = ConverterRole.GatewayApplication(ApplicationDevice(name = "Wearable Companion", packageName = "com.example.wearable", version = "4.0"))
```

### Warnings

A converted record can carry warnings, each a registered `mobile-omission` diagnostic naming what the graph lost.
`RecordingDeviceOmitted` means the record named a device the resolver did not identify; `SourceOffsetUnavailable` means a sample or stage had no usable UTC offset and was written in UTC; `UnmodeledMetadataWithheld` names source fields the contract has no place for, such as a planned exercise session id.
Log them with the graph; they never block an upload.

### Batch conversion

`convert(records) { record -> context }` converts many records and returns the conversions and the refusals in input order.
The callback reserves one context, and therefore one event sequence, per record.

```kotlin
val batch = HealthConnectConverter().convert(records) { record -> exportContext(reserve(record)) }
```

### Retries and semantic equality

Store `graph.json` and `graph.sha256` with the sequence.
A retry resends the stored bytes; to check that a Bundle a receiver returned is the same event, parse it and compare with `semanticallyEquals`, which ignores formatting and nothing else.

```kotlin
val replay = ExchangeGraph.parse(ExchangeGraphKind.ACTIVE, json) as ExchangeGraphParseResult.Valid
replay.graph.semanticallyEquals(conversion.graph)
```

### Retraction events

When a record disappears from Health Connect, emit a retraction naming every node of its last active graph.
`retractionTargets` derives the targets from the catalog for record types with exactly one output per measurement; series and sessions need the stored graph's own `retractionTargets()`.

```kotlin
val targets = HealthConnectConverter().retractionTargets(HealthConnectSourceRecord(id, HealthConnectSourceType.STEPS), context.event)
val retraction = RetractionEvent(targets, nextContext.event, sourceRecordIdentity, retractedAt = Instant.now())
```

### Reverse projection

An output, or a whole graph, projects back onto the AndroidX record it came from.
`Observation.toHealthConnectRecord()` handles the exactly-one types and `ExchangeGraph.toHealthConnectRecords()` folds series, sessions, specimens and nutrients back together; the client record id defaults to the source-output identity.

```kotlin
val record = (observation.toHealthConnectRecord() as HealthConnectProjectionResult.Projected).record
```

### The conformance lane

`Scripts/validate-health-connect-fhir-conformance.sh` regenerates the contract from the grove-fhir catalogs, exports the deterministic fixture corpus from the tests and validates it with the official FHIR validator against the exact Mobile and Health Connect packages.
Run it against a grove-fhir checkout before you publish a change to the adapter.

## Glossary

| IG term | Kotlin type |
| --- | --- |
| Exchange event | `ExchangeEventIdentifier` with its `ExchangeEventContext` |
| Exchange graph | `ExchangeGraph`, parsed through `ExchangeGraph.parse` |
| Business identifier | `BusinessIdentifier` |
| Identifier role | `GroveIdentifierRole` on a `RoledIdentifier` |
| Opaque identity | `OpaqueIdentityScope` minting under `DeploymentIdentifierSystems` |
| Entry-node key | `EntryNodeKey` |
| Subject | `Subject.Logical` or `Subject.Bundled` |
| Study enrollment | `StudyEnrollment` |
| Application, host and recording device | `ApplicationDevice`, `HostDevice`, `RecordingDevice` |
| Writer | `HealthConnectContract.WRITER_PACKAGE_SYSTEM` on the provenance agent and the writer-record identity |
| Retraction event and target | `RetractionEvent`, `RetractionTarget` |
| Governed source identifier | `GovernedSourceIdentifierDisclosurePolicy` |
| Producer diagnostic | `ExchangeGraphDiagnostic` on every failure, warning and refusal |

# Package org.grovealliance.health.fhir

The Health Connect adapter: `HealthConnectConverter` and its context, options, results, warnings and failures, the generated catalog and contract constants, and the reverse projection onto AndroidX records.
