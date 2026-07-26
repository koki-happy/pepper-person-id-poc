// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val required005SpecArtifactPaths = listOf(
    "specs/005-full-person-identification/spec.md",
    "specs/005-full-person-identification/plan.md",
    "specs/005-full-person-identification/research.md",
    "specs/005-full-person-identification/data-model.md",
    "specs/005-full-person-identification/quickstart.md",
    "specs/005-full-person-identification/tasks.md",
    "specs/005-full-person-identification/checklists/requirements.md",
    "specs/005-full-person-identification/contracts/identification-result.md",
    "specs/005-full-person-identification/contracts/persistence-policy.md",
    "specs/005-full-person-identification/contracts/model-catalog.md",
    "specs/005-full-person-identification/contracts/face-pipeline.md",
    "specs/005-full-person-identification/contracts/speaker-pipeline.md",
    "specs/005-full-person-identification/contracts/metrics.md",
)

tasks.register("verify005SpecArtifacts") {
    group = "verification"
    description = "Verifies that all required 005 specification artifacts and contracts exist."

    val requiredArtifacts = required005SpecArtifactPaths.map(layout.projectDirectory::file)
    inputs.files(requiredArtifacts)

    doLast {
        val missingArtifacts = requiredArtifacts.filterNot { it.asFile.isFile }
        if (missingArtifacts.isNotEmpty()) {
            throw GradleException(
                missingArtifacts.joinToString(
                    prefix = "Missing required 005 specification artifacts:\n",
                    separator = "\n",
                ) { "- ${it.asFile.relativeTo(rootDir).invariantSeparatorsPath}" },
            )
        }
    }
}
