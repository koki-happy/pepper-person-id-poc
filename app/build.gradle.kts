import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val generatedModelCatalogAssets = layout.buildDirectory.dir("generated/modelCatalogAssets/main")
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
    sourceSets.getByName("main").assets.srcDir(generatedModelCatalogAssets.get().asFile)
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
    val modelAssetsDirectory = project.file("src/main/assets/models")
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
    implementation(libs.kotlinx.serialization.json)
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
    androidTestImplementation(libs.truth)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
