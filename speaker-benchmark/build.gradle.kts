plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.example.pepper_person_id_poc"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":speaker-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.onnxruntime)

    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.example.pepper_person_id_poc.speakerbenchmark.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("prepareJvs") {
    group = "speaker benchmark"
    description = "Prepare the pinned local JVS evaluation subset without extracting the full archive."
    mainClass.set("com.example.pepper_person_id_poc.speakerbenchmark.dataset.PrepareJvsMainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootProject.projectDir

    val archive = providers.gradleProperty("jvsArchive")
        .orElse("data/downloads/jvs_ver1.zip")
    val output = providers.gradleProperty("jvsOutput")
        .orElse("data/audio/jvs-evaluation")
    val force = providers.gradleProperty("jvsForce")
        .map(String::toBoolean)
        .orElse(false)
    doFirst {
        args("--archive", archive.get(), "--output", output.get())
        if (force.get()) args("--force")
    }
}

tasks.test {
    useJUnitPlatform()
}
