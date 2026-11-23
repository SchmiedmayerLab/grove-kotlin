# Module fhir-contract

The source-neutral core of the Grove Mobile FHIR exchange: the identities every Kotlin producer
mints, the canonical JSON they are derived from, and the records and Bundles built from them, as the
[grove-fhir](https://github.com/SchmiedmayerLab/grove-fhir) exchange protocol defines them. It is a
plain JVM module with no Android dependency; platform adapters build on it.

## Identities

- `DeploymentIdentifierSystems.derived(root, keyId, epoch)` names one identifier system per identity
  kind under the deployment's own root, so identities never link across unrelated deployments.
- `OpaqueIdentityScope` mints every identity with one HMAC-SHA-256 key: `sourceRecord(…)` returns a
  `SourceRecordIdentity`, whose `output(role, discriminator)` names each output of the record. The
  same record converted twice gets the same identifiers, yet nobody can recover the native record id
  from them. The key can be a hardware-backed key, such as an Android Keystore HMAC key.
- `OpaqueIdentityScope.recommendedKeyId(prefix, key, subject)` derives a per-subject key id.
- `BusinessIdentifier.fullUrl` is the deterministic `urn:uuid:` of the entry an identifier keys.

Keep the key, its key id and its epoch for as long as data minted under them can be replayed or
retracted; a new key takes a new key id.

## Canonical JSON

Record versions and content snapshots are digests over `grove-canonical-json-v0`, which keeps number
lexemes exactly as written. The module parses and writes it losslessly for the adapters built on it.

## Records and Bundles

- `ExchangeProducer.create(identityScope, subject, application, host, studies)` states who the data is
  about and which application converts it.
- Platform adapters turn source records into `ExchangeRecord`s; `ExchangeBundlePacker` packs records
  into one Bundle, stating each shared Patient, Device and study node once, and returns it as a
  validated `ExchangeGraph`.
- `ExchangeGraph.validating(json, kind)` checks stored or received bytes against every structural rule
  of the exchange before they are trusted. Every record an adapter assembles passes the same check.

APIs marked `@InternalGroveFhirApi` are shared between Grove's FHIR modules and may change without
notice; apps build on the rest.

## Conformance

The tests run the exchange protocol's normative test vectors and the `mobile-exchange` corpus of the
grove-fhir release pinned in `gradle.properties`, each case refused with its exact rule and location.
`Scripts/sync-grove-fhir.py --grove-fhir <checkout>` copies that release's catalog and corpus and
regenerates the code under `src/generated`; `--check` reports any drift from the pin.
