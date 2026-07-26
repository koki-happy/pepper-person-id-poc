import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class ModelCatalogDistributionExtension {
    abstract val candidateArtifactIds: SetProperty<String>
    abstract val candidateRuntimeIds: SetProperty<String>
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
        output.writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    linkedMapOf(
                        "schemaVersion" to 1,
                        "sourceDistributionRevision" to catalog["distributionRevision"],
                        "artifactIds" to result.includedArtifactIds.sorted(),
                        "runtimeIds" to result.includedRuntimeIds.sorted(),
                        "assetFilenames" to artifacts.mapNotNull { it["filename"] as? String }.sorted(),
                        "runtimeVersions" to runtimes.associate {
                            it["runtimeId"] as String to it["version"]
                        }.toSortedMap(),
                    ),
                ),
            ) + System.lineSeparator(),
        )
    }
}

class ModelCatalogPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "modelCatalogDistribution",
            ModelCatalogDistributionExtension::class.java,
        )
        project.tasks.register(
            "generateCandidateModelAllowlist",
            GenerateCandidateModelAllowlistTask::class.java,
        ).configure {
            group = "distribution"
            description = "Generates the candidate model/runtime allowlist from schema-v2 catalog."
            catalogFile.set(project.rootProject.layout.projectDirectory.file("config/models.json"))
            artifactIds.set(extension.candidateArtifactIds)
            runtimeIds.set(extension.candidateRuntimeIds)
            outputFile.set(
                project.layout.buildDirectory.file("generated/candidate/model-allowlist.json"),
            )
        }
    }
}
