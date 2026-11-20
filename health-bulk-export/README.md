# Module health-bulk-export

Exports historical Health Connect records in batches, for example to upload years of data after a
participant joins. A session splits the time from its start date until the moment it is first
created into batches per record type, hands each batch to a `BatchProcessor`, and checkpoints its
progress after every batch. Creating the session again with the same identifier, in the same or a
later process, resumes with the batches still to do.

## Setup

Register the module next to `health`, which it reads through:

```kotlin
override val configuration = Configuration {
    health { … }
    bulkHealthExport()
}
```

## Usage

```kotlin
val exporter = requireDependency<BulkHealthExporter>()
val session = exporter.session(
    id = BulkExportSessionId(value = "historical"),
    recordTypes = setOf(RecordType.steps, RecordType.heartRate),
    startDate = ExportSessionStartDate.Last(period = Period.ofYears(10)),
    processor = uploader,
).getOrThrow()

session.start(
    retryFailedBatches = true,
    concurrencyLevel = BulkExportConcurrencyLevel.Limit(limit = 2),
).getOrThrow().collect { output -> … }
```

- The processor returns a `Result`: a failed batch is kept and retried when the session is started
  with `retryFailedBatches`. `didPersist` is called once a batch's completion is checkpointed.
- `pause()` stops a running session and leaves its batches in progress pending.
  `BulkHealthExporter.deleteSession` terminates a session and deletes its checkpoint.
- `state` and `progress` report where the session is; a session that couldn't write its
  checkpoint pauses with `BulkExportPauseReason.CheckpointWriteFailed` and keeps the outputs it
  couldn't emit until the next start.
- A record spanning the edge of two batches may reach the processor twice.

The module only runs sessions in the current process. To keep exporting after the process dies,
start the session from work that outlives it, such as a WorkManager worker that is enqueued again
until the session completes.

## Permissions

Record types the app may not read export as empty batches. Health Connect returns records older
than 30 days only with `READ_HEALTH_DATA_HISTORY`, which the app requests through `health`.
