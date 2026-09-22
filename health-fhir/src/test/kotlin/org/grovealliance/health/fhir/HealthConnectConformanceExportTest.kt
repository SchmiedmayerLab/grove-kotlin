//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.health.fhir

import com.google.common.truth.Truth.assertThat
import org.grovealliance.fhir.RetractionEvent
import org.grovealliance.fhir.Subject
import org.hl7.fhir.r4.formats.IParser
import org.hl7.fhir.r4.formats.JsonParser
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.Provenance
import org.junit.Test
import java.io.File
import java.time.Instant

/** Emits the deterministic corpus the conformance lane validates against the exact grove-fhir packages. */
class HealthConnectConformanceExportTest {
    private val fixtures = HealthConnectTestFixtures
    private val records = HealthConnectFixtureRecords
    private val converter = fixtures.converter
    private val parser: IParser = JsonParser().setOutputStyle(IParser.OutputStyle.PRETTY)

    // The conformance kit's Health Connect glucose check compares literal Patient references, so the lane bundles one.
    private val subject = Subject.Bundled(fixtures.conformanceSubject, Patient())
    private val scope = fixtures.conformanceScope

    @Test
    fun `emits the complete deterministic conformance and wire fixtures`() {
        val conformance = linkedMapOf<String, String>()
        val wire = linkedMapOf<String, String>()
        var sequence = 0L
        fun next(conversionInstant: Instant = fixtures.conversionInstant) =
            fixtures.context(sequence = ++sequence, conversionInstant = conversionInstant, subject = subject, scope = scope)

        records.conformanceRecords().forEach { (name, record) ->
            val conversion = fixtures.converted(record, next())
            val bundle = conversion.toBundle()
            conformance["health-connect-$name-bundle.json"] = parser.composeString(bundle) + "\n"
            bundle.entry.map { it.resource }.filterIsInstance<Observation>().forEachIndexed { index, observation ->
                conformance["health-connect-$name-observation-${index + 1}.json"] = parser.composeString(observation) + "\n"
            }
            val provenance = bundle.entry.map { it.resource }.filterIsInstance<Provenance>().single()
            conformance["health-connect-$name-provenance.json"] = parser.composeString(provenance) + "\n"
        }
        val semanticInstant = Instant.parse("2026-08-21T18:00:00Z")
        records.semanticVectorRecords().forEach { vector ->
            val conversion = fixtures.converted(vector.record, next(semanticInstant))
            val observation = conversion.toBundle().entry.map { it.resource }.filterIsInstance<Observation>()
                .first { candidate -> candidate.meta.profile.any { it.value == vector.profile } }
            conformance["health-connect-semantic-${vector.id}-observation.json"] = parser.composeString(observation) + "\n"
        }

        val upsert = fixtures.converted(records.heartRate(), next())
        wire["health-connect-heart-rate-upsert-bundle.json"] = upsert.graph.json
        val update = fixtures.converted(
            records.heartRate(samples = records.twoHeartRateSamples().take(1), lastModified = Instant.parse("2026-08-19T17:30:02Z")),
            next(),
        )
        conformance["health-connect-heart-rate-update-bundle.json"] = parser.composeString(update.toBundle()) + "\n"
        wire["health-connect-heart-rate-update-bundle.json"] = update.graph.json
        val emptied = converter.convert(
            records.heartRate(samples = emptyList(), lastModified = Instant.parse("2026-08-19T17:30:03Z")),
            fixtures.context(sequence = sequence + 1, subject = subject, scope = scope),
        )
        assertThat(emptied).isInstanceOf(HealthConnectConversionResult.NoOutput::class.java)
        val zeroOutput = RetractionEvent(
            targets = update.graph.retractionTargets(),
            context = next().event,
            sourceRecord = update.identifiers.sourceRecord,
            retractedAt = Instant.parse("2026-08-19T18:00:03Z"),
        )
        wire["health-connect-heart-rate-zero-output-retraction-bundle.json"] = zeroOutput.graph.json
        assertThat(zeroOutput.graph.toBundle().entry.map { it.resource.fhirType() }).containsExactly("Provenance")

        val deletionContext = next()
        val steps = fixtures.converted(records.steps("fixture-deletion"), deletionContext)
        val deletion = RetractionEvent(
            targets = converter.retractionTargets(steps.source, deletionContext.event),
            context = next().event,
            sourceRecord = steps.identifiers.sourceRecord,
            retractedAt = Instant.parse("2026-08-19T18:00:02Z"),
        )
        conformance["health-connect-step-deletion-bundle.json"] = parser.composeString(deletion.graph.toBundle()) + "\n"
        wire["health-connect-step-deletion-bundle.json"] = deletion.graph.json

        assertThat(conformance).hasSize(CONFORMANCE_RESOURCES)
        assertThat(wire).hasSize(WIRE_RESOURCES)
        assertThat(conformance.keys.filter { it.startsWith("health-connect-semantic-") })
            .containsExactlyElementsIn(records.semanticVectorRecords().map { "health-connect-semantic-${it.id}-observation.json" })
        fixtures.configuredFile("grove.conformance.export")?.let { directory -> conformance.writeTo(directory) }
        fixtures.configuredFile("grove.wire.export")?.let { directory -> wire.writeTo(directory) }
    }

    private fun Map<String, String>.writeTo(directory: File) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        directory.listFiles()?.forEach { it.delete() }
        forEach { (name, content) -> File(directory, name).writeText(content) }
    }

    private companion object {
        const val CONFORMANCE_RESOURCES = 75
        const val WIRE_RESOURCES = 4
    }
}
