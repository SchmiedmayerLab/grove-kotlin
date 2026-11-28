# Module health-fhir

Converts Health Connect records into Grove Mobile FHIR exchange records, following the Health
Connect adapter of the [grove-fhir](https://github.com/SchmiedmayerLab/grove-fhir) release pinned in
`gradle.properties`. It never reads Health Connect: the app passes the records it read.

## Usage

```kotlin
val exporter = HealthConnectFhirExporter(
    producer = producer,
    repositoryScope = repositoryScope,
    options = HealthConnectFhirExporter.Options(userText = HealthConnectFhirExporter.Options.UserText.OMIT),
)
val exports = exporter.export(records = records, instant = Instant.now())
```

Each `HealthConnectExport` holds the converted `ExchangeRecord`, or the registered diagnostic it was
refused for, and the warnings about what the record doesn't state. Pack the records with
`ExchangeBundlePacker` from `fhir-contract`.

`repositoryScope` names one Health Connect store with a stable pseudonymous identifier; record
identities derive from it. `userText` decides whether session titles and notes, which a person
wrote, travel.

When Health Connect reports a Record deleted, pass a `HealthConnectDeletion` (its id and Record
class, which the change list it came from names) to `exporter.retract(deletions, instant)`. Each
retraction names the Record's whole source record and packs into a retraction Bundle.

## Coverage

Every Record type the Health Connect adapter contract supports converts; `PlannedExerciseSessionRecord`,
which the contract defers, is refused with `mobile-input.unsupported-source-type`.

Sleep and exercise sessions convert as the HealthKit adapter converts the same data. A sleep session
yields one sleep-stage Observation per stage and no sleep-duration summary, as the guide defines no
rule for its value; a session without stages is not converted. An exercise session's shared activity
and segment codes follow the rule the guide's HealthKit activity mapping applies, since the guide
publishes no such mapping for Health Connect.

## Conformance

The tests run grove-fhir's `mobile-semantics` corpus: every vector that admits Health Connect is
converted from the Record a writer would store and compared with its normalized projection.
