import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

val liteRtVersion = "2.1.6"
val requestedTargetAbi = providers.gradleProperty("targetAbi").getOrElse("armeabi-v7a")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

apply<ModelCatalogPlugin>()

val candidateSelectionEvidencePath =
    providers.gradleProperty("candidateSelectionEvidence").orNull
val candidateSelectionEvidence = candidateSelectionEvidencePath?.let { path ->
    val file = rootProject.file(path)
    if (file.isFile) {
        JsonSlurper().parse(file) as? Map<*, *>
    } else {
        null
    }
}
val selectedCandidateArtifactIds = (candidateSelectionEvidence?.get("artifactIds") as? List<*>)
    .orEmpty()
    .mapNotNull { it as? String }
    .filter(String::isNotBlank)
    .toSet()
val selectedCandidateRuntimeIds = (candidateSelectionEvidence?.get("runtimeIds") as? List<*>)
    .orEmpty()
    .mapNotNull { it as? String }
    .filter(String::isNotBlank)
    .toSet()

extensions.configure<ModelCatalogDistributionExtension> {
    candidateArtifactIds.set(selectedCandidateArtifactIds)
    candidateRuntimeIds.set(selectedCandidateRuntimeIds)
    candidateSelectionEvidencePath?.let { path ->
        candidateSelectionEvidenceFile.set(rootProject.layout.projectDirectory.file(path))
    }
}

val generatedModelCatalogAssets = layout.buildDirectory.dir("generated/modelCatalogAssets/main")
val generatedCandidateAssets = layout.buildDirectory.dir("generated/candidateAssets")
val generateModelCatalogAsset by tasks.registering(Copy::class) {
    from(rootProject.file("config/models.json"))
    into(generatedModelCatalogAssets.map { it.dir("model-catalog") })
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
            // Pepper is the production target. LiteRT 2.1.6 publishes ARMv7/ARM64/x86_64,
            // while this app accepts only the two physical-device ABIs used for validation.
            require(requestedTargetAbi in setOf("armeabi-v7a", "arm64-v8a")) {
                "LiteRT 2.1.6 supports this app only for armeabi-v7a or arm64-v8a; " +
                    "targetAbi=$requestedTargetAbi is not allowed."
            }
            abiFilters += requestedTargetAbi
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
    flavorDimensions += "distribution"
    productFlavors {
        create("benchmark") {
            dimension = "distribution"
            applicationIdSuffix = ".benchmark"
            buildConfigField("String", "DISTRIBUTION_FLAVOR", "\"benchmark\"")
            buildConfigField("boolean", "PERSIST_METRICS_BY_DEFAULT", "true")
            if (file("src/main/cpp/third_party/ready.marker").isFile) {
                externalNativeBuild {
                    cmake {
                        arguments += listOf("-DINCLUDE_NCNN=ON", "-DINCLUDE_MNN=ON")
                    }
                }
            }
        }
        create("candidate") {
            dimension = "distribution"
            applicationIdSuffix = ".candidate"
            buildConfigField("String", "DISTRIBUTION_FLAVOR", "\"candidate\"")
            buildConfigField("boolean", "PERSIST_METRICS_BY_DEFAULT", "false")
            if (file("src/main/cpp/third_party/ready.marker").isFile) {
                externalNativeBuild {
                    cmake {
                        arguments += listOf(
                            "-DINCLUDE_NCNN=" +
                                if ("ncnn-20260526-android-cpu" in selectedCandidateRuntimeIds) "ON" else "OFF",
                            "-DINCLUDE_MNN=" +
                                if ("mnn-3.5.0-android-cpu" in selectedCandidateRuntimeIds) "ON" else "OFF",
                        )
                    }
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "tflite"
        noCompress += "mp4"
    }
    packaging {
        jniLibs {
            excludes += setOf("lib/x86/**", "lib/x86_64/**")
        }
    }
    sourceSets.getByName("main").assets.srcDir(generatedModelCatalogAssets.get().asFile)
    sourceSets.getByName("candidate").assets.srcDir(generatedCandidateAssets.get().asFile)
    if (file("src/main/cpp/third_party/ready.marker").isFile) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.31.6"
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateModelCatalogAsset)
}

val verifyModelArtifactHashes by tasks.registering {
    group = "verification"
    description = "Verifies cataloged model artifact sizes and SHA-256 hashes."
    val catalogFile = rootProject.file("config/models.json")
    val modelAssetsDirectory = project.file("src/benchmark/assets/models")
    val localModelsDirectory = rootProject.file("models")
    inputs.file(catalogFile)
    inputs.dir(modelAssetsDirectory)
    inputs.dir(localModelsDirectory)

    doLast {
        val parsed = JsonSlurper().parse(catalogFile) as? Map<*, *>
            ?: error("Model catalog must be a JSON object: ${catalogFile.absolutePath}")
        val artifacts = (parsed["artifacts"] as? List<*>)
            ?.mapIndexed { index, value ->
                value as? Map<*, *> ?: error("artifacts[$index] must be an object")
            }
            ?: error("config/models.json must contain artifacts[]")
        val sha256Pattern = Regex("[0-9a-f]{64}")

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

        fun verifyFile(owner: String, artifactFile: File, expectedHash: String, expectedSize: Long) {
            check(expectedHash.matches(sha256Pattern)) {
                "$owner sha256 must be 64 lowercase hex digits"
            }
            check(expectedSize > 0) { "$owner fileSizeBytes must be positive" }
            check(artifactFile.isFile) {
                "$owner artifact is missing: ${artifactFile.absolutePath}"
            }
            check(artifactFile.length() == expectedSize) {
                "$owner size mismatch: expected=$expectedSize actual=${artifactFile.length()}"
            }
            check(sha256(artifactFile) == expectedHash) {
                "$owner SHA-256 does not match config/models.json"
            }
        }

        artifacts.forEachIndexed { index, artifact ->
            val artifactId = (artifact["artifactId"] as? String)
                ?.takeIf(String::isNotBlank)
                ?: error("artifacts[$index].artifactId must be a nonblank string")
            val filename = artifact["filename"] as? String
            val expectedHash = artifact["sha256"] as? String
            val expectedSize = (artifact["fileSizeBytes"] as? Number)?.toLong()
            check((expectedHash == null) == (expectedSize == null)) {
                "$artifactId must define sha256 and fileSizeBytes together"
            }
            if (expectedHash != null && expectedSize != null) {
                val localPath = (artifact["localPath"] as? String)?.takeIf(String::isNotBlank)
                val artifactFile = if (localPath != null) {
                    val projectRoot = rootProject.projectDir.canonicalFile
                    rootProject.file(localPath).canonicalFile.also {
                        check(it.toPath().startsWith(projectRoot.toPath())) {
                            "$artifactId localPath escapes the project: $localPath"
                        }
                    }
                } else {
                    val assetsRoot = modelAssetsDirectory.canonicalFile
                    assetsRoot.resolve(
                        filename ?: error("$artifactId requires filename for hash verification"),
                    ).canonicalFile.also {
                        check(it.toPath().startsWith(assetsRoot.toPath())) {
                            "$artifactId filename escapes the model assets directory: $filename"
                        }
                    }
                }
                verifyFile(
                    owner = artifactId,
                    artifactFile = artifactFile,
                    expectedHash = expectedHash,
                    expectedSize = expectedSize,
                )
            }

            val companionFiles = (artifact["companionFiles"] as? List<*>).orEmpty()
            companionFiles.forEachIndexed { companionIndex, value ->
                val companion = value as? Map<*, *>
                    ?: error("$artifactId companionFiles[$companionIndex] must be an object")
                check(companion.keys == setOf("filename", "sha256", "fileSizeBytes")) {
                    "$artifactId companionFiles[$companionIndex] must contain exactly " +
                        "filename, sha256, and fileSizeBytes"
                }
                val assetsRoot = modelAssetsDirectory.canonicalFile
                val companionFilename = companion["filename"] as? String
                    ?: error("$artifactId companion filename must be a string")
                val companionFile = assetsRoot.resolve(companionFilename).canonicalFile
                check(companionFile.toPath().startsWith(assetsRoot.toPath())) {
                    "$artifactId companion filename escapes the model assets directory: " +
                        companionFilename
                }
                verifyFile(
                    owner = "$artifactId companionFiles[$companionIndex]",
                    artifactFile = companionFile,
                    expectedHash = companion["sha256"] as? String
                        ?: error("$artifactId companion sha256 must be a string"),
                    expectedSize = (companion["fileSizeBytes"] as? Number)?.toLong()
                        ?: error("$artifactId companion fileSizeBytes must be an integer"),
                )
            }
        }

        val generatedIntermediates = (parsed["generatedIntermediates"] as? List<*>)
            ?.mapIndexed { index, value ->
                value as? Map<*, *> ?: error("generatedIntermediates[$index] must be an object")
            }
            .orEmpty()
        generatedIntermediates.forEachIndexed { index, intermediate ->
            val owner = "generatedIntermediates[$index]"
            val filename = intermediate["filename"] as? String
                ?: error("$owner.filename must be a string")
            val assetsRoot = modelAssetsDirectory.canonicalFile
            val artifactFile = assetsRoot.resolve(filename).canonicalFile
            check(artifactFile.toPath().startsWith(assetsRoot.toPath())) {
                "$owner filename escapes the model assets directory: $filename"
            }
            verifyFile(
                owner = owner,
                artifactFile = artifactFile,
                expectedHash = intermediate["sha256"] as? String
                    ?: error("$owner.sha256 must be a string"),
                expectedSize = (intermediate["fileSizeBytes"] as? Number)?.toLong()
                    ?: error("$owner.fileSizeBytes must be an integer"),
            )
        }
    }
}

val verifyModelCatalog by tasks.registering {
    group = "verification"
    description = "Validates the schema-v2 model catalog, references, and compatibility records."
    dependsOn(verifyModelArtifactHashes)
    val catalogFile = rootProject.file("config/models.json")
    inputs.file(catalogFile)

    doLast {
        val parsed = JsonSlurper().parse(catalogFile) as? Map<*, *>
            ?: error("Model catalog must be a JSON object: ${catalogFile.absolutePath}")

        fun objects(field: String): List<Map<*, *>> =
            (parsed[field] as? List<*>)
                ?.mapIndexed { index, value ->
                    value as? Map<*, *> ?: error("$field[$index] must be an object")
                }
                ?: error("config/models.json must contain $field[]")

        fun Map<*, *>.requiredString(field: String, owner: String): String =
            (get(field) as? String)
                ?.takeIf(String::isNotBlank)
                ?: error("$owner.$field must be a nonblank string")

        fun uniqueIds(items: List<Map<*, *>>, field: String): Set<String> {
            val ids = items.mapIndexed { index, item ->
                item.requiredString(field, "$field[$index]")
            }
            check(ids.size == ids.toSet().size) { "$field values must be unique: $ids" }
            return ids.toSet()
        }

        check((parsed["schemaVersion"] as? Number)?.toInt() == 2) {
            "config/models.json schemaVersion must be 2"
        }
        val modelSpaces = objects("modelSpaces")
        val runtimes = objects("runtimes")
        val artifacts = objects("artifacts")
        val legacyModels = objects("models")
        val generatedIntermediates = (parsed["generatedIntermediates"] as? List<*>)
            ?.mapIndexed { index, value ->
                value as? Map<*, *> ?: error("generatedIntermediates[$index] must be an object")
            }
            .orEmpty()
        check(modelSpaces.isNotEmpty()) { "modelSpaces[] must not be empty" }
        check(runtimes.isNotEmpty()) { "runtimes[] must not be empty" }
        check(artifacts.isNotEmpty()) { "artifacts[] must not be empty" }
        check(legacyModels.isNotEmpty()) { "legacy models[] must not be empty" }
        val modelSpaceIds = uniqueIds(modelSpaces, "modelSpaceId")
        val runtimeIds = uniqueIds(runtimes, "runtimeId")
        val artifactIds = uniqueIds(artifacts, "artifactId")
        uniqueIds(legacyModels, "id")

        modelSpaces.forEachIndexed { index, modelSpace ->
            val owner = "modelSpaces[$index]"
            modelSpace.requiredString("modality", owner)
            check((modelSpace["embeddingDimension"] as? Number)?.toInt()?.let { it > 0 } == true) {
                "$owner.embeddingDimension must be positive"
            }
            modelSpace.requiredString("normalization", owner)
        }
        val runtimeVersions = runtimes.associate { runtime ->
            val runtimeId = runtime.requiredString("runtimeId", "runtime")
            runtime.requiredString("name", runtimeId)
            val version = runtime.requiredString("version", runtimeId)
            runtime.requiredString("provider", runtimeId)
            runtimeId to version
        }
        val sha256Pattern = Regex("[0-9a-f]{64}")
        val allowedStatuses = setOf(
            "VERIFIED",
            "BUILDABLE",
            "CONVERSION_REQUIRED",
            "UNSUPPORTED",
            "BLOCKED",
        )

        artifacts.forEachIndexed { index, artifact ->
            val artifactId = artifact.requiredString("artifactId", "artifacts[$index]")
            val role = artifact.requiredString("role", artifactId)
            artifact.requiredString("modality", artifactId)
            artifact.requiredString("architecture", artifactId)
            artifact.requiredString("precision", artifactId)
            val format = artifact.requiredString("format", artifactId)
            artifact.requiredString("sourceUrl", artifactId)
            artifact.requiredString("sourceRevision", artifactId)
            artifact.requiredString("inputLayout", artifactId)
            artifact.requiredString("outputSemantics", artifactId)
            artifact.requiredString("weightLicense", artifactId)
            val commercialUse = artifact.requiredString("commercialUse", artifactId)
            check(commercialUse in setOf("ALLOWED", "PROHIBITED", "UNKNOWN")) {
                "$artifactId.commercialUse is invalid: $commercialUse"
            }
            val licenseEvidence = artifact["licenseEvidence"]
            check(licenseEvidence == null || licenseEvidence is String && licenseEvidence.isNotBlank()) {
                "$artifactId.licenseEvidence must be null or a nonblank string"
            }
            if (commercialUse == "ALLOWED") {
                check(licenseEvidence is String && licenseEvidence.startsWith("https://")) {
                    "$artifactId with commercialUse=ALLOWED requires https licenseEvidence"
                }
            }

            val modelSpaceId = artifact["modelSpaceId"] as? String
            if (role == "EMBEDDING") {
                check(!modelSpaceId.isNullOrBlank()) {
                    "$artifactId embedding artifact requires modelSpaceId"
                }
            }
            if (modelSpaceId != null) {
                check(modelSpaceId in modelSpaceIds) {
                    "$artifactId references unknown modelSpaceId=$modelSpaceId"
                }
            }
            val filename = artifact["filename"] as? String
            check(format == "SDK_INTERNAL" || !filename.isNullOrBlank()) {
                "$artifactId requires filename unless format is SDK_INTERNAL"
            }
            val inputShape = artifact["inputShape"] as? List<*>
            val outputShape = artifact["outputShape"] as? List<*>
            val normalization = artifact["normalization"] as? Map<*, *>
            val compatibilityValues = artifact["runtimeCompatibility"] as? List<*>
            val allCompatibilityBlocked = compatibilityValues?.isNotEmpty() == true &&
                compatibilityValues.all {
                    (it as? Map<*, *>)?.get("status") == "BLOCKED"
                }
            val permitsUnknownTensorShape = format == "SDK_INTERNAL" || allCompatibilityBlocked
            check(inputShape != null && (inputShape.isNotEmpty() || permitsUnknownTensorShape)) {
                "$artifactId.inputShape must be nonempty unless SDK internal or fully BLOCKED"
            }
            check(outputShape != null && (outputShape.isNotEmpty() || permitsUnknownTensorShape)) {
                "$artifactId.outputShape must be nonempty unless SDK internal or fully BLOCKED"
            }
            check(!normalization.isNullOrEmpty()) {
                "$artifactId.normalization must contain preprocessing metadata"
            }

            val expectedHash = artifact["sha256"] as? String
            val expectedSize = (artifact["fileSizeBytes"] as? Number)?.toLong()
            check((expectedHash == null) == (expectedSize == null)) {
                "$artifactId must define sha256 and fileSizeBytes together"
            }
            expectedHash?.let {
                check(it.matches(sha256Pattern)) {
                    "$artifactId.sha256 must be 64 lowercase hex digits"
                }
                check(checkNotNull(expectedSize) > 0) {
                    "$artifactId.fileSizeBytes must be positive"
                }
            }

            val compatibility = (artifact["runtimeCompatibility"] as? List<*>)
                ?.mapIndexed { compatibilityIndex, value ->
                    value as? Map<*, *>
                        ?: error("$artifactId.runtimeCompatibility[$compatibilityIndex] must be an object")
                }
                ?: error("$artifactId.runtimeCompatibility must be an array")
            check(compatibility.isNotEmpty()) {
                "$artifactId.runtimeCompatibility must not be empty"
            }
            compatibility.forEachIndexed { compatibilityIndex, record ->
                val owner = "$artifactId.runtimeCompatibility[$compatibilityIndex]"
                check(record.requiredString("artifactId", owner) == artifactId) {
                    "$owner must reference artifactId=$artifactId"
                }
                val runtimeId = record.requiredString("runtimeId", owner)
                check(runtimeId in runtimeIds) {
                    "$owner references unknown runtimeId=$runtimeId"
                }
                record.requiredString("abi", owner)
                check((record["minApi"] as? Number)?.toInt()?.let { it >= 1 } == true) {
                    "$owner.minApi must be positive"
                }
                val status = record.requiredString("status", owner)
                check(status in allowedStatuses) { "$owner has unknown status=$status" }
                val reason = record["reason"] as? String
                val evidence = record["evidence"] as? String
                if (status == "VERIFIED") {
                    check(expectedHash != null) {
                        "$owner VERIFIED status requires an artifact hash"
                    }
                    check(runtimeVersions.getValue(runtimeId).isNotBlank())
                    check(!evidence.isNullOrBlank()) {
                        "$owner VERIFIED status requires evidence"
                    }
                }
                if (status == "BLOCKED" || status == "UNSUPPORTED") {
                    check(!reason.isNullOrBlank()) { "$owner $status status requires a reason" }
                }
            }

            val conversion = artifact["conversion"]
            if (conversion != null) {
                val record = conversion as? Map<*, *>
                    ?: error("$artifactId.conversion must be an object or null")
                val sourceArtifactId = record.requiredString("sourceArtifactId", "$artifactId.conversion")
                check(sourceArtifactId != artifactId && sourceArtifactId in artifactIds) {
                    "$artifactId conversion references invalid sourceArtifactId=$sourceArtifactId"
                }
                record.requiredString("tool", "$artifactId.conversion")
                record.requiredString("toolRevision", "$artifactId.conversion")
                record.requiredString("environmentDigest", "$artifactId.conversion")
                record.requiredString("command", "$artifactId.conversion")
                val outputHash = record.requiredString("outputSha256", "$artifactId.conversion")
                check(outputHash.matches(sha256Pattern)) {
                    "$artifactId.conversion.outputSha256 must be 64 lowercase hex digits"
                }
                check(!(record["tensorMetadata"] as? Map<*, *>).isNullOrEmpty()) {
                    "$artifactId.conversion.tensorMetadata must be nonempty"
                }
                check(record["warnings"] is List<*>) {
                    "$artifactId.conversion.warnings must be an array"
                }
            }

            val companionFiles = (artifact["companionFiles"] as? List<*>).orEmpty()
            companionFiles.forEachIndexed { companionIndex, value ->
                val companion = value as? Map<*, *>
                    ?: error("$artifactId.companionFiles[$companionIndex] must be an object")
                check(companion.keys == setOf("filename", "sha256", "fileSizeBytes")) {
                    "$artifactId.companionFiles[$companionIndex] must contain exactly " +
                        "filename, sha256, and fileSizeBytes"
                }
                companion.requiredString("filename", "$artifactId.companionFiles[$companionIndex]")
                val companionHash = companion.requiredString(
                    "sha256",
                    "$artifactId.companionFiles[$companionIndex]",
                )
                check(companionHash.matches(sha256Pattern)) {
                    "$artifactId.companionFiles[$companionIndex].sha256 is invalid"
                }
                check((companion["fileSizeBytes"] as? Number)?.toLong()?.let { it > 0 } == true) {
                    "$artifactId.companionFiles[$companionIndex].fileSizeBytes must be positive"
                }
            }
        }

        val intermediateFilenames = mutableSetOf<String>()
        generatedIntermediates.forEachIndexed { index, intermediate ->
            val owner = "generatedIntermediates[$index]"
            check(
                intermediate.keys ==
                    setOf("filename", "sha256", "fileSizeBytes", "status", "distribution"),
            ) {
                "$owner must contain exactly filename, sha256, fileSizeBytes, status, and distribution"
            }
            val filename = intermediate.requiredString("filename", owner)
            check(intermediateFilenames.add(filename)) {
                "generatedIntermediates filenames must be unique: $filename"
            }
            val hash = intermediate.requiredString("sha256", owner)
            check(hash.matches(sha256Pattern)) { "$owner.sha256 is invalid" }
            check((intermediate["fileSizeBytes"] as? Number)?.toLong()?.let { it > 0 } == true) {
                "$owner.fileSizeBytes must be positive"
            }
            check(intermediate.requiredString("status", owner) == "NOT_RUNTIME_ARTIFACT") {
                "$owner.status must be NOT_RUNTIME_ARTIFACT"
            }
            check(intermediate.requiredString("distribution", owner) == "EXCLUDE") {
                "$owner.distribution must be EXCLUDE"
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
    val modelAssetsDirectory = project.file("src/benchmark/assets/models")
    inputs.file(provenanceFile)
    inputs.file(modelOptionsFile)

    doLast {
        val requiredCatalogIds = setOf(
            "campplus-zh-en",
            "wespeaker-resnet34-lm",
        )
        val requiredBundledModels = mapOf(
            "campplus-zh-en" to "3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
            "wespeaker-resnet34-lm" to "wespeaker_en_voxceleb_resnet34_LM.onnx",
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
    if (name.startsWith("preCandidate") && name.endsWith("Build")) {
        dependsOn("prepareCandidateModelAssets")
    }
    if (name.startsWith("preBenchmark") && name.endsWith("Build")) {
        dependsOn(generateModelCatalogAsset)
    }
}

dependencies {
    implementation(project(":speaker-core"))
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
    implementation(libs.kotlinx.serialization.json)

    val sherpaAar = files("libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar")
    compileOnly(sherpaAar)
    compileOnly(libs.opencv)
    compileOnly(libs.mlkit.face.detection)
    compileOnly("com.google.ai.edge.litert:litert:$liteRtVersion")
    add("benchmarkImplementation", sherpaAar)
    add("benchmarkImplementation", libs.opencv)
    add("benchmarkImplementation", libs.mlkit.face.detection)
    add("benchmarkImplementation", "com.google.ai.edge.litert:litert:$liteRtVersion")
    if ("sherpa-onnx-1.13.4-android-cpu" in selectedCandidateRuntimeIds) {
        add("candidateImplementation", sherpaAar)
    }
    if ("opencv-5.0.0-android-cpu" in selectedCandidateRuntimeIds) {
        add("candidateImplementation", libs.opencv)
    }
    if ("mlkit-face-16.1.7-bundled" in selectedCandidateRuntimeIds) {
        add("candidateImplementation", libs.mlkit.face.detection)
    }
    if ("litert-2.1.6-android-cpu" in selectedCandidateRuntimeIds) {
        add("candidateImplementation", "com.google.ai.edge.litert:litert:$liteRtVersion")
    }

    val customOnnxRuntimeAar = file("libs/onnxruntime-mobile-1.27.0.aar")
    val onnxRuntimeDependency: Any =
        if (customOnnxRuntimeAar.isFile) files(customOnnxRuntimeAar)
        else "com.microsoft.onnxruntime:onnxruntime-android:1.20.0"
    compileOnly(onnxRuntimeDependency)
    add("benchmarkImplementation", onnxRuntimeDependency)
    if (customOnnxRuntimeAar.isFile) {
        if ("onnxruntime-mobile-1.27.0-android-cpu" in selectedCandidateRuntimeIds) {
            add("candidateImplementation", onnxRuntimeDependency)
        }
    } else if ("onnxruntime-android-1.20.0-cpu" in selectedCandidateRuntimeIds) {
        add("candidateImplementation", onnxRuntimeDependency)
    }
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

val verifyLiteRtRuntimeArtifact by tasks.registering {
    group = "verification"
    description = "Verifies the exact LiteRT version and requested Android ABI before packaging."
    val runtimeClasspath = configurations.named("benchmarkDebugRuntimeClasspath")
    inputs.property("liteRtVersion", liteRtVersion)
    inputs.property("targetAbi", requestedTargetAbi)

    doLast {
        val artifacts = runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filter { it.moduleVersion.id.group == "com.google.ai.edge.litert" }
        val expectedModules = setOf("litert", "litert-api")
        val resolvedModules = artifacts.map { it.name }.toSet()
        check(resolvedModules == expectedModules) {
            "Expected only official LiteRT modules $expectedModules, resolved $resolvedModules"
        }
        artifacts.forEach { artifact ->
            check(artifact.moduleVersion.id.version == liteRtVersion) {
                "LiteRT module ${artifact.name} resolved unexpected version " +
                    artifact.moduleVersion.id.version
            }
            check(artifact.file.extension == "aar") {
                "LiteRT module ${artifact.name} must resolve to an AAR"
            }
            ZipFile(artifact.file).use { archive ->
                val hasRequestedAbi = archive.entries().asSequence().any { entry ->
                    entry.name.startsWith("jni/$requestedTargetAbi/") &&
                        entry.name.endsWith(".so")
                }
                check(hasRequestedAbi) {
                    "${artifact.file.name} does not contain native libraries for $requestedTargetAbi"
                }
            }
        }
    }
}

tasks.configureEach {
    if (name.startsWith("preBenchmark") && name.endsWith("Build")) {
        dependsOn(verifyLiteRtRuntimeArtifact)
    }
}

val verifyCandidateRuntimeDependencies by tasks.registering {
    group = "verification"
    description = "Rejects candidate AAR dependencies that are not selected by approved evidence."
    dependsOn("verifyCandidateSelectionEvidence")
    val runtimeClasspath = configurations.named("candidateDebugRuntimeClasspath")
    inputs.property("candidateRuntimeIds", selectedCandidateRuntimeIds.sorted())

    doLast {
        val supportedAndroidRuntimeIds = setOf(
            "mlkit-face-16.1.7-bundled",
            "opencv-5.0.0-android-cpu",
            "litert-2.1.6-android-cpu",
            "onnxruntime-android-1.20.0-cpu",
            "onnxruntime-mobile-1.27.0-android-cpu",
            "ncnn-20260526-android-cpu",
            "mnn-3.5.0-android-cpu",
            "sherpa-onnx-1.13.4-android-cpu",
        )
        check(selectedCandidateRuntimeIds.all { it in supportedAndroidRuntimeIds }) {
            "Candidate contains a runtime that cannot be packaged for Android: " +
                (selectedCandidateRuntimeIds - supportedAndroidRuntimeIds)
        }
        val artifacts = runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
        val coordinates = artifacts.map { artifact ->
            "${artifact.moduleVersion.id.group}:${artifact.name}:${artifact.file.name}"
        }
        val files = runtimeClasspath.get().files.map(File::getName)
        val presence = mapOf(
            "mlkit-face-16.1.7-bundled" to
                coordinates.any { it.startsWith("com.google.mlkit:face-detection:") },
            "opencv-5.0.0-android-cpu" to
                coordinates.any { it.startsWith("org.opencv:opencv:") },
            "litert-2.1.6-android-cpu" to
                coordinates.any { it.startsWith("com.google.ai.edge.litert:litert:") },
            "onnxruntime-android-1.20.0-cpu" to
                coordinates.any { it.startsWith("com.microsoft.onnxruntime:onnxruntime-android:") },
            "onnxruntime-mobile-1.27.0-android-cpu" to
                files.any { it == "onnxruntime-mobile-1.27.0.aar" },
            "sherpa-onnx-1.13.4-android-cpu" to
                files.any { it == "sherpa-onnx-static-link-onnxruntime-1.13.4.aar" },
        )
        presence.forEach { (runtimeId, isPresent) ->
            check(isPresent == (runtimeId in selectedCandidateRuntimeIds)) {
                "$runtimeId dependency presence=$isPresent does not match the candidate allowlist"
            }
        }
    }
}

fun locateLlvmReadElf(): File {
    val explicitNdk = System.getenv("ANDROID_NDK_HOME")
        ?.takeIf(String::isNotBlank)
        ?.let(::file)
    val localProperties = rootProject.file("local.properties")
    val sdkDirectory = localProperties.takeIf(File::isFile)?.let { propertiesFile ->
        Properties().apply {
            propertiesFile.inputStream().use(::load)
        }.getProperty("sdk.dir")?.let(::file)
    }
    val ndkRoots = buildList {
        explicitNdk?.let(::add)
        sdkDirectory?.resolve("ndk")?.listFiles()
            .orEmpty()
            .filter(File::isDirectory)
            .sortedByDescending(File::getName)
            .let(::addAll)
    }
    return ndkRoots.asSequence()
        .flatMap { it.walkTopDown().asSequence() }
        .firstOrNull { it.isFile && it.name == "llvm-readelf.exe" }
        ?: error(
            "llvm-readelf.exe was not found. Set ANDROID_NDK_HOME or sdk.dir before APK audit.",
        )
}

fun registerDistributionAudit(
    taskName: String,
    variant: String,
    assembleTask: String,
    apkRelativePath: String,
    candidate: Boolean,
) = tasks.register<Exec>(taskName) {
    group = "verification"
    description = "Builds and audits the $variant APK contents, ABI, native symbols, and licenses."
    dependsOn(assembleTask)
    if (candidate) {
        dependsOn(verifyCandidateRuntimeDependencies)
        dependsOn("prepareCandidateModelAssets")
    }
    val outputDirectory = layout.buildDirectory.dir("reports/apk-audit/$variant")
    outputs.dir(outputDirectory)
    doFirst {
        val arguments = mutableListOf(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            rootProject.file("scripts/apk-audit/Invoke-DistributionAudit.ps1").absolutePath,
            "-ApkPath",
            layout.buildDirectory.file(apkRelativePath).get().asFile.absolutePath,
            "-Variant",
            variant,
            "-OutputDirectory",
            outputDirectory.get().asFile.absolutePath,
            "-CatalogPath",
            rootProject.file("config/models.json").absolutePath,
            "-ReadElfPath",
            locateLlvmReadElf().absolutePath,
            "-AllowedAbis",
            requestedTargetAbi,
        )
        if (candidate) {
            arguments += listOf(
                "-AllowlistPath",
                layout.buildDirectory.file("generated/candidate/model-allowlist.json")
                    .get().asFile.absolutePath,
            )
        }
        commandLine(arguments)
    }
}

val auditBenchmarkDebugApk = registerDistributionAudit(
    taskName = "auditBenchmarkDebugApk",
    variant = "benchmarkDebug",
    assembleTask = "assembleBenchmarkDebug",
    apkRelativePath = "outputs/apk/benchmark/debug/app-benchmark-debug.apk",
    candidate = false,
)
val auditCandidateDebugApk = registerDistributionAudit(
    taskName = "auditCandidateDebugApk",
    variant = "candidateDebug",
    assembleTask = "assembleCandidateDebug",
    apkRelativePath = "outputs/apk/candidate/debug/app-candidate-debug.apk",
    candidate = true,
)

tasks.register("compareDistributionApkSizes") {
    group = "verification"
    description = "Writes the benchmark/candidate APK size delta after both audits pass."
    dependsOn(auditBenchmarkDebugApk, auditCandidateDebugApk)
    val outputFile = layout.buildDirectory.file("reports/apk-audit/size-comparison.json")
    outputs.file(outputFile)
    doLast {
        val benchmarkApk =
            layout.buildDirectory.file("outputs/apk/benchmark/debug/app-benchmark-debug.apk")
                .get().asFile
        val candidateApk =
            layout.buildDirectory.file("outputs/apk/candidate/debug/app-candidate-debug.apk")
                .get().asFile
        val benchmarkBytes = benchmarkApk.length()
        val candidateBytes = candidateApk.length()
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            JsonOutput.prettyPrint(
                JsonOutput.toJson(
                    linkedMapOf(
                        "schemaVersion" to 1,
                        "benchmarkApkSizeBytes" to benchmarkBytes,
                        "candidateApkSizeBytes" to candidateBytes,
                        "candidateReductionBytes" to benchmarkBytes - candidateBytes,
                        "candidatePercentOfBenchmark" to
                            if (benchmarkBytes == 0L) null
                            else candidateBytes.toDouble() * 100.0 / benchmarkBytes,
                    ),
                ),
            ) + System.lineSeparator(),
        )
    }
}
