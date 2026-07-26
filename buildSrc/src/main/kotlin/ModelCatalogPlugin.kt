import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

abstract class ModelCatalogDistributionExtension {
    abstract val candidateArtifactIds: SetProperty<String>
    abstract val candidateRuntimeIds: SetProperty<String>
    abstract val candidateSelectionEvidenceFile: RegularFileProperty
    abstract val benchmarkModelAssetsDirectory: DirectoryProperty
}

abstract class VerifyCandidateSelectionEvidenceTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    abstract val evidenceFile: RegularFileProperty

    @get:Input
    abstract val artifactIds: SetProperty<String>

    @get:Input
    abstract val runtimeIds: SetProperty<String>

    @get:Internal
    abstract val projectRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        check(evidenceFile.isPresent) {
            "Candidate packaging is prohibited without -PcandidateSelectionEvidence=<approved JSON>."
        }
        val evidence = evidenceFile.get().asFile
        check(evidence.isFile) {
            "Candidate selection evidence is missing: ${evidence.absolutePath}"
        }
        val parsed = JsonSlurper().parse(evidence) as? Map<*, *>
            ?: error("Candidate selection evidence must be a JSON object")
        check(parsed.keys == setOf("schemaVersion", "status", "artifactIds", "runtimeIds", "evidence")) {
            "Candidate selection evidence must contain exactly schemaVersion, status, artifactIds, " +
                "runtimeIds, and evidence"
        }
        check((parsed["schemaVersion"] as? Number)?.toInt() == 1) {
            "Candidate selection evidence schemaVersion must be 1"
        }
        check(parsed["status"] == "APPROVED") {
            "Candidate selection evidence status must be APPROVED"
        }

        fun selectedIds(field: String): Set<String> {
            val values = parsed[field] as? List<*>
                ?: error("Candidate selection evidence $field must be an array")
            val ids = values.mapIndexed { index, value ->
                (value as? String)?.takeIf(String::isNotBlank)
                    ?: error("$field[$index] must be a nonblank string")
            }
            check(ids.isNotEmpty()) { "$field must not be empty" }
            check(ids.size == ids.toSet().size) { "$field must not contain duplicates" }
            return ids.toSet()
        }

        val selectedArtifacts = selectedIds("artifactIds")
        val selectedRuntimes = selectedIds("runtimeIds")
        check(selectedArtifacts == artifactIds.get()) {
            "Candidate artifact selection differs from the configured allowlist"
        }
        check(selectedRuntimes == runtimeIds.get()) {
            "Candidate runtime selection differs from the configured allowlist"
        }

        val records = parsed["evidence"] as? List<*>
            ?: error("Candidate selection evidence evidence must be an array")
        check(records.isNotEmpty()) {
            "Candidate selection requires at least one benchmark evidence file"
        }
        val root = projectRoot.get().asFile.canonicalFile
        val sha256Pattern = Regex("[0-9a-f]{64}")
        records.forEachIndexed { index, value ->
            val record = value as? Map<*, *>
                ?: error("evidence[$index] must be an object")
            check(record.keys == setOf("kind", "path", "sha256")) {
                "evidence[$index] must contain exactly kind, path, and sha256"
            }
            check((record["kind"] as? String)?.isNotBlank() == true) {
                "evidence[$index].kind must be nonblank"
            }
            val rawPath = (record["path"] as? String)?.takeIf(String::isNotBlank)
                ?: error("evidence[$index].path must be nonblank")
            val file = File(rawPath).let { if (it.isAbsolute) it else root.resolve(rawPath) }.canonicalFile
            check(file.toPath().startsWith(root.toPath())) {
                "evidence[$index].path escapes the project: $rawPath"
            }
            check(file.isFile) {
                "evidence[$index] file is missing: ${file.absolutePath}"
            }
            val expectedHash = (record["sha256"] as? String)?.lowercase()
                ?: error("evidence[$index].sha256 must be a string")
            check(expectedHash.matches(sha256Pattern)) {
                "evidence[$index].sha256 must be 64 lowercase hex digits"
            }
            check(file.sha256() == expectedHash) {
                "evidence[$index] SHA-256 mismatch: ${file.absolutePath}"
            }
        }
    }
}

abstract class GenerateCandidateModelAllowlistTask : DefaultTask() {
    @get:InputFile
    abstract val catalogFile: RegularFileProperty

    @get:Input
    abstract val artifactIds: SetProperty<String>

    @get:Input
    abstract val runtimeIds: SetProperty<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val selection = DistributionSelection(
            artifactIds = artifactIds.get(),
            runtimeIds = runtimeIds.get(),
        )
        val result = ModelLicenseGate.evaluate(catalogFile.get().asFile, selection)
        result.requireApproved()

        val catalog = JsonSlurper().parse(catalogFile.get().asFile) as Map<*, *>
        val artifacts = (catalog["artifacts"] as List<*>)
            .map { it as Map<*, *> }
            .filter { it["artifactId"] in result.includedArtifactIds }
        val runtimes = (catalog["runtimes"] as List<*>)
            .map { it as Map<*, *> }
            .filter { it["runtimeId"] in result.includedRuntimeIds }
        check(artifacts.size == result.includedArtifactIds.size)
        check(runtimes.size == result.includedRuntimeIds.size)

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        val assetFilenames = artifacts.flatMap { artifact ->
            buildList {
                (artifact["filename"] as? String)?.let(::add)
                (artifact["companionFiles"] as? List<*>).orEmpty()
                    .map { it as Map<*, *> }
                    .mapNotNullTo(this) { it["filename"] as? String }
            }
        }.distinct().sorted()
        output.writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    linkedMapOf(
                        "schemaVersion" to 1,
                        "sourceDistributionRevision" to catalog["distributionRevision"],
                        "artifactIds" to result.includedArtifactIds.sorted(),
                        "runtimeIds" to result.includedRuntimeIds.sorted(),
                        "assetFilenames" to assetFilenames,
                        "runtimeVersions" to runtimes.associate {
                            it["runtimeId"] as String to it["version"]
                        }.toSortedMap(),
                    ),
                ),
            ) + System.lineSeparator(),
        )
    }
}

abstract class PrepareCandidateModelAssetsTask : DefaultTask() {
    @get:InputFile
    abstract val allowlistFile: RegularFileProperty

    @get:InputDirectory
    abstract val benchmarkModelAssetsDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val allowlist = JsonSlurper().parse(allowlistFile.get().asFile) as? Map<*, *>
            ?: error("Candidate allowlist must be a JSON object")
        val filenames = (allowlist["assetFilenames"] as? List<*>)
            ?.mapIndexed { index, value ->
                (value as? String)?.takeIf(String::isNotBlank)
                    ?: error("assetFilenames[$index] must be a nonblank string")
            }
            ?: error("Candidate allowlist must contain assetFilenames[]")
        val sourceRoot = benchmarkModelAssetsDirectory.get().asFile.canonicalFile
        val outputRoot = outputDirectory.get().asFile
        if (outputRoot.exists()) outputRoot.deleteRecursively()
        val modelOutput = outputRoot.resolve("models")
        modelOutput.mkdirs()
        filenames.forEach { filename ->
            check(filename == File(filename).name) {
                "Candidate asset filename must not contain a path: $filename"
            }
            val source = sourceRoot.resolve(filename).canonicalFile
            check(source.toPath().startsWith(sourceRoot.toPath()) && source.isFile) {
                "Selected candidate asset is missing from benchmark assets: $filename"
            }
            source.copyTo(modelOutput.resolve(filename), overwrite = true)
        }
        val distributionOutput = outputRoot.resolve("distribution")
        distributionOutput.mkdirs()
        allowlistFile.get().asFile.copyTo(
            distributionOutput.resolve("model-allowlist.json"),
            overwrite = true,
        )
    }
}

class ModelCatalogPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "modelCatalogDistribution",
            ModelCatalogDistributionExtension::class.java,
        )
        extension.benchmarkModelAssetsDirectory.convention(
            project.layout.projectDirectory.dir("src/benchmark/assets/models"),
        )
        val verifyEvidence = project.tasks.register(
            "verifyCandidateSelectionEvidence",
            VerifyCandidateSelectionEvidenceTask::class.java,
        ).apply {
            configure {
                group = "distribution"
                description = "Fails closed unless candidate selection evidence is approved and hash-valid."
                evidenceFile.set(extension.candidateSelectionEvidenceFile)
                artifactIds.set(extension.candidateArtifactIds)
                runtimeIds.set(extension.candidateRuntimeIds)
                projectRoot.set(project.rootProject.layout.projectDirectory)
            }
        }
        val generateAllowlist = project.tasks.register(
            "generateCandidateModelAllowlist",
            GenerateCandidateModelAllowlistTask::class.java,
        ).apply {
            configure {
                group = "distribution"
                description = "Generates the candidate model/runtime allowlist from schema-v2 catalog."
                dependsOn(verifyEvidence)
                catalogFile.set(project.rootProject.layout.projectDirectory.file("config/models.json"))
                artifactIds.set(extension.candidateArtifactIds)
                runtimeIds.set(extension.candidateRuntimeIds)
                outputFile.set(
                    project.layout.buildDirectory.file("generated/candidate/model-allowlist.json"),
                )
            }
        }
        project.tasks.register(
            "prepareCandidateModelAssets",
            PrepareCandidateModelAssetsTask::class.java,
        ).configure {
            group = "distribution"
            description = "Copies only selected, licensed candidate model assets into generated assets."
            dependsOn(generateAllowlist)
            allowlistFile.set(generateAllowlist.flatMap { it.outputFile })
            benchmarkModelAssetsDirectory.set(extension.benchmarkModelAssetsDirectory)
            outputDirectory.set(project.layout.buildDirectory.dir("generated/candidateAssets"))
        }
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
