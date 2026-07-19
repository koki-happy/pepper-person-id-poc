import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pepper_person_id_poc"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.pepper_person_id_poc"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Pepper is the production target. The AAR's x86 libonnxruntime.so imports
            // __write_chk, which Android API 23 does not provide, so x86 is opt-in only.
            val requestedAbi = providers.gradleProperty("targetAbi").getOrElse("armeabi-v7a")
            require(requestedAbi in setOf("armeabi-v7a", "arm64-v8a", "x86")) {
                "Unsupported targetAbi=$requestedAbi. Use armeabi-v7a (default), arm64-v8a, or x86."
            }
            abiFilters += requestedAbi
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (file("src/main/cpp/third_party/ready.marker").isFile) {
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                }
            }
        }

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    if (file("src/main/cpp/third_party/ready.marker").isFile) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.31.6"
            }
        }
    }
}

val verifyReleaseModelLicenses by tasks.registering {
    group = "verification"
    description = "Rejects release builds unless speaker model weight and commercial terms are approved."
    val provenanceFile = rootProject.file("config/models.json")
    val modelOptionsFile = project.file(
        "src/main/java/com/example/pepper_person_id_poc/domain/config/PocSettings.kt"
    )
    val modelAssetsDirectory = project.file("src/main/assets/models")
    inputs.file(provenanceFile)
    inputs.file(modelOptionsFile)

    doLast {
        val requiredCatalogIds = setOf(
            "campplus-en",
            "campplus-zh-en",
            "eres2net-en",
            "speakernet-m",
            "titanet-s",
        )
        val requiredBundledModels = mapOf(
            "campplus-en" to "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
            "campplus-zh-en" to "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
            "eres2net-en" to "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx",
        )
        val allowedNonSpeakerOnnxAssets = setOf(
            "face-reidentification-retail-0095.onnx",
            "face_detection_yunet_2026may.onnx",
            "face_recognition_sface_2021dec.onnx",
            "silero_vad.onnx",
        )
        val parsed = JsonSlurper().parse(provenanceFile) as? Map<*, *>
            ?: error("Release model-license gate could not parse ${provenanceFile.absolutePath}")
        val modelObjects = (parsed["models"] as? List<*>)
            ?.map { it as? Map<*, *> ?: error("Every models[] entry must be an object") }
            .orEmpty()
        fun field(model: Map<*, *>, name: String): String? = model[name] as? String
        val ids = modelObjects.map { field(it, "id").orEmpty() }
        check(ids.none(String::isBlank) && ids.size == ids.toSet().size) {
            "config/models.json must contain unique, non-blank model IDs (found $ids)"
        }
        check(ids.toSet() == requiredCatalogIds) {
            "config/models.json model IDs must exactly match $requiredCatalogIds (found ${ids.toSet()})"
        }
        val rejectedLicenseValues = setOf(
            "UNVERIFIED", "UNKNOWN", "TBD", "TODO", "PENDING", "N/A", "NA", "NONE"
        )
        val blockers = modelObjects.mapNotNull { model ->
            val modelId = field(model, "id").orEmpty()
            val weightLicense = field(model, "weightLicense")
            val commercialUse = field(model, "commercialUse")
            val licenseEvidence = field(model, "licenseEvidence")
            val rejectedFields = buildList {
                when {
                    weightLicense == null -> add("weightLicense is missing")
                    weightLicense.isBlank() -> add("weightLicense is blank")
                    weightLicense.trim().uppercase() in rejectedLicenseValues ->
                        add("weightLicense is not verified ('$weightLicense')")
                }
                when {
                    commercialUse == null -> add("commercialUse is missing")
                    commercialUse != "ALLOWED" -> add("commercialUse must be ALLOWED (found '$commercialUse')")
                }
                when {
                    licenseEvidence == null -> add("licenseEvidence is missing")
                    licenseEvidence.isBlank() -> add("licenseEvidence is blank")
                    licenseEvidence.trim().uppercase() in rejectedLicenseValues ->
                        add("licenseEvidence is not verified ('$licenseEvidence')")
                    !licenseEvidence.startsWith("https://") ->
                        add("licenseEvidence must be an https URL")
                }
            }
            rejectedFields.takeIf(List<String>::isNotEmpty)?.let { modelId to it }
        }
        val catalogById = modelObjects.associateBy { field(it, "id").orEmpty() }
        val optionPairs = Regex(
            """(?s)configModelId\s*=\s*\"([^\"]+)\".*?modelFileName\s*=\s*\"([^\"]+)\""""
        ).findAll(modelOptionsFile.readText()).associate { it.groupValues[1] to it.groupValues[2] }
        check(optionPairs == requiredBundledModels) {
            "SpeakerModelOption ID/file mappings must exactly match $requiredBundledModels (found $optionPairs)"
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        requiredBundledModels.forEach { (modelId, requiredFilename) ->
            val catalogModel = checkNotNull(catalogById[modelId]) { "Missing catalog model '$modelId'" }
            val catalogFilename = field(catalogModel, "filename")
            check(catalogFilename == requiredFilename) {
                "$modelId filename must be '$requiredFilename' (found '$catalogFilename')"
            }
            val expectedSize = (catalogModel["fileSizeBytes"] as? Number)?.toLong()
                ?: error("$modelId fileSizeBytes is missing or invalid")
            val expectedHash = field(catalogModel, "sha256")?.lowercase()
                ?: error("$modelId sha256 is missing")
            check(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "$modelId sha256 is invalid" }
            val asset = modelAssetsDirectory.resolve(requiredFilename)
            check(asset.isFile) { "Required release speaker asset is missing: ${asset.absolutePath}" }
            check(asset.length() == expectedSize) {
                "$modelId asset size mismatch: expected=$expectedSize actual=${asset.length()}"
            }
            check(sha256(asset) == expectedHash) { "$modelId asset SHA-256 does not match config/models.json" }
        }
        val catalogFilenames = modelObjects.mapNotNull { field(it, "filename") }.toSet()
        val bundledCatalogFiles = modelAssetsDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name in catalogFilenames }
            .map(File::getName)
            .toSet()
        check(bundledCatalogFiles == requiredBundledModels.values.toSet()) {
            "Bundled catalog speaker assets must exactly match ${requiredBundledModels.values.toSet()} " +
                "(found $bundledCatalogFiles)"
        }
        val unexpectedOnnxAssets = modelAssetsDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("onnx", ignoreCase = true) }
            .map(File::getName)
            .toSet() - requiredBundledModels.values.toSet() - allowedNonSpeakerOnnxAssets
        check(unexpectedOnnxAssets.isEmpty()) {
            "Release assets contain ONNX files outside the reviewed allowlist: $unexpectedOnnxAssets"
        }
        check(blockers.isEmpty()) {
            val details = blockers.joinToString(separator = "; ") { (modelId, reasons) ->
                "$modelId: ${reasons.joinToString()}"
            }
            "Release build is prohibited by model licensing policy in ${provenanceFile.absolutePath} " +
                "($details). Every model requires a verified weightLicense, commercialUse=ALLOWED, " +
                "and an https licenseEvidence URL."
        }
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(verifyReleaseModelLicenses)
    }
}

dependencies {
    implementation(project(":speaker-core"))
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.opencv)
    implementation(libs.mlkit.face.detection)
    val customOnnxRuntimeAar = file("libs/onnxruntime-mobile-1.27.0.aar")
    if (customOnnxRuntimeAar.isFile) {
        implementation(files(customOnnxRuntimeAar))
    }
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
