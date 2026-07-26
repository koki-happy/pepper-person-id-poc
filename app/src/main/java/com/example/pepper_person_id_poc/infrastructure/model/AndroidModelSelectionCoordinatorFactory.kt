package com.example.pepper_person_id_poc.infrastructure.model

import android.content.Context
import android.os.Build
import com.example.pepper_person_id_poc.application.config.ModelSelectionCoordinator

object AndroidModelSelectionCoordinatorFactory {
    fun create(context: Context): ModelSelectionCoordinator {
        val appContext = context.applicationContext
        val source = appContext.assets.open(CATALOG_ASSET_PATH).bufferedReader().use { it.readText() }
        val catalog = JsonModelCatalogRepository().parse(source)
        val bundledFiles = appContext.assets.list(MODEL_ASSET_DIRECTORY)?.toSet().orEmpty()
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: throw IllegalStateException("Device reports no supported ABI")
        return ModelSelectionCoordinator(
            catalog = catalog,
            bundledArtifactFileNames = bundledFiles,
            abi = abi,
            apiLevel = Build.VERSION.SDK_INT,
        )
    }

    private const val CATALOG_ASSET_PATH = "model-catalog/models.json"
    private const val MODEL_ASSET_DIRECTORY = "models"
}
