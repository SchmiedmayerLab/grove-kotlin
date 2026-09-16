//
// This source file is part of the Grove open-source project
//
// SPDX-FileCopyrightText: 2026 Stanford University and the project authors (see CONTRIBUTORS.md)
//
// SPDX-License-Identifier: MIT

package org.grovealliance.studydefinition

import com.github.luben.zstd.ZstdOutputStream
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.grovealliance.studydefinition.fixtures.StudyBundleFixtures
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class StudyBundleArchiveTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val exampleBundle: File = StudyBundleFixtures.exampleBundleDir()

    @Test
    fun `unpack extracts every entry of the archive`() {
        val bundleDir = bundleDir()

        exampleArchive().inputStream().use { StudyBundle.unpack(it, bundleDir) }

        assertThat(File(bundleDir, "definition.json").exists()).isTrue()
        assertThat(bundleDir.walkTopDown().count { it.isFile })
            .isEqualTo(exampleBundle.walkTopDown().count { it.isFile })
        assertThat(File(bundleDir, "consent/consent+en-US.md").readText())
            .isEqualTo(File(exampleBundle, "consent/consent+en-US.md").readText())
    }

    @Test
    fun `unpacks the schema version the definition model decodes`() {
        val bundleDir = bundleDir()

        exampleArchive().inputStream().use { StudyBundle.unpack(it, bundleDir) }

        val root = Json.parseToJsonElement(File(bundleDir, "definition.json").readText()).jsonObject
        assertThat(root["schemaVersion"]?.jsonPrimitive?.content)
            .isEqualTo(StudyDefinitionJson.SCHEMA_VERSION)
    }

    @Test
    fun `unpack replaces previous bundle contents`() {
        val bundleDir = bundleDir()
        File(bundleDir, "stale.txt").apply { parentFile?.mkdirs() }.writeText("stale")

        exampleArchive().inputStream().use { StudyBundle.unpack(it, bundleDir) }

        assertThat(File(bundleDir, "stale.txt").exists()).isFalse()
    }

    @Test
    fun `unpack rejects a directory without the bundle extension`() {
        assertThrows(IllegalArgumentException::class.java) {
            exampleArchive().inputStream().use { StudyBundle.unpack(it, File(temporaryFolder.root, "bundle")) }
        }
    }

    @Test
    fun `unpack rejects entries escaping the bundle`() {
        val bundleDir = File(temporaryFolder.root, "evil.${StudyBundle.FILE_EXTENSION}")

        val exception = assertThrows(IllegalArgumentException::class.java) {
            maliciousArchive().inputStream().use { StudyBundle.unpack(it, bundleDir) }
        }

        assertThat(exception).hasMessageThat().contains("escapes the bundle")
        assertThat(File(temporaryFolder.root, "escaped.txt").exists()).isFalse()
    }

    private fun bundleDir() = File(temporaryFolder.root, "example.${StudyBundle.FILE_EXTENSION}")

    /**
     * The example bundle as the archive the exporter ships it in.
     */
    private fun exampleArchive(): ByteArray {
        val tar = ByteArrayOutputStream()
        exampleBundle.walkTopDown().filter { it.isFile }.forEach { file ->
            val contents = file.readBytes()
            tar.write(header(file.relativeTo(exampleBundle).invariantSeparatorsPath, contents.size))
            tar.write(contents)
            tar.write(ByteArray(padding(contents.size)))
        }
        tar.write(ByteArray(BLOCK_SIZE * 2))
        return compress(tar.toByteArray())
    }

    /**
     * A zstd-compressed tar holding a single file whose path climbs out of the bundle directory.
     */
    private fun maliciousArchive(): ByteArray {
        val contents = "escaped".toByteArray()
        val tar = ByteArrayOutputStream().apply {
            write(header("../escaped.txt", contents.size))
            write(contents)
            write(ByteArray(padding(contents.size)))
            write(ByteArray(BLOCK_SIZE * 2))
        }
        return compress(tar.toByteArray())
    }

    /**
     * A ustar header for a regular file, carrying the fields [StudyBundle] reads back.
     */
    private fun header(name: String, size: Int): ByteArray {
        val header = ByteArray(BLOCK_SIZE)
        name.toByteArray().copyInto(header)
        "%011o ".format(size).toByteArray().copyInto(header, destinationOffset = SIZE_OFFSET)
        header[TYPE_OFFSET] = '0'.code.toByte()
        return header
    }

    private fun padding(size: Int) = (BLOCK_SIZE - size % BLOCK_SIZE) % BLOCK_SIZE

    private fun compress(tar: ByteArray): ByteArray = ByteArrayOutputStream().also { compressed ->
        ZstdOutputStream(compressed).use { it.write(tar) }
    }.toByteArray()

    private companion object {
        const val BLOCK_SIZE = 512
        const val SIZE_OFFSET = 124
        const val TYPE_OFFSET = 156
    }
}
