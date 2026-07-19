package com.example.pepper_person_id_poc.speakerbenchmark.dataset

import java.nio.file.Path

fun main(args: Array<String>) {
    val command = PrepareJvsArguments.parse(args)
    if (command.showHelp) {
        println(PrepareJvsArguments.usage())
        return
    }

    val root = Path.of("").toAbsolutePath().normalize()
    val archive = resolve(root, command.archivePath)
    val output = resolve(root, command.outputDirectory)
    val result = JvsDatasetPreparer.prepare(
        JvsPreparationOptions(
            archivePath = archive,
            outputDirectory = output,
            force = command.force,
            expectedArchiveSizeBytes = JvsCorpusPin.FILE_SIZE_BYTES,
            expectedArchiveSha256 = JvsCorpusPin.SHA256,
        ),
    )

    println("Prepared ${result.entryCount} local JVS evaluation WAV files.")
    println("Manifest: ${result.manifestPath}")
    println("Archive SHA-256: ${result.archiveSha256}")
    println(JvsCorpusPin.PIN_NOTE)
}

internal data class PrepareJvsCommand(
    val archivePath: String = "data/downloads/${JvsCorpusPin.FILE_NAME}",
    val outputDirectory: String = "data/audio/jvs-evaluation",
    val force: Boolean = false,
    val showHelp: Boolean = false,
)

internal object PrepareJvsArguments {
    fun parse(args: Array<String>): PrepareJvsCommand {
        var archivePath = "data/downloads/${JvsCorpusPin.FILE_NAME}"
        var outputDirectory = "data/audio/jvs-evaluation"
        var force = false
        var showHelp = false
        var index = 0
        while (index < args.size) {
            when (val argument = args[index]) {
                "--archive" -> {
                    archivePath = args.getOrNull(++index)
                        ?: throw IllegalArgumentException("--archive requires a path")
                }

                "--output" -> {
                    outputDirectory = args.getOrNull(++index)
                        ?: throw IllegalArgumentException("--output requires a path")
                }

                "--force" -> force = true
                "--help", "-h" -> showHelp = true
                else -> throw IllegalArgumentException("Unknown JVS preparation option: $argument")
            }
            index += 1
        }
        require(archivePath.isNotBlank()) { "--archive path must not be blank" }
        require(outputDirectory.isNotBlank()) { "--output path must not be blank" }
        return PrepareJvsCommand(archivePath, outputDirectory, force, showHelp)
    }

    fun usage(): String = """
        Prepare local JVS evaluation WAV files without extracting the full corpus.

        Usage:
          ./gradlew :speaker-benchmark:prepareJvs
          ./gradlew :speaker-benchmark:prepareJvs -PjvsArchive=<zip> -PjvsOutput=<dir> [-PjvsForce=true]

        The production command requires the pinned ${JvsCorpusPin.FILE_SIZE_BYTES}-byte archive and
        SHA-256 ${JvsCorpusPin.SHA256}. This is a reproducibility pin measured on 2026-07-13,
        not an official publisher checksum. JVS media remains local and must not be redistributed.
    """.trimIndent()
}

private fun resolve(root: Path, configured: String): Path = Path.of(configured).let { path ->
    (if (path.isAbsolute) path else root.resolve(path)).toAbsolutePath().normalize()
}
