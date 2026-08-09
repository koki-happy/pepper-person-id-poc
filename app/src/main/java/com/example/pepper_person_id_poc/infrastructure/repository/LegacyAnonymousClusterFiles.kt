package com.example.pepper_person_id_poc.infrastructure.repository

import android.content.Context
import java.io.File

/** Deletes biometric files created by the removed persistent anonymous repository. */
object LegacyAnonymousClusterFiles {
    private const val BIOMETRIC_DIRECTORY = "biometric"
    private val legacyFileNames = listOf(
        "anonymous-face-clusters.bin",
        "anonymous-speaker-clusters.bin",
    )

    fun deleteAll(context: Context) {
        val directory = File(context.applicationContext.filesDir, BIOMETRIC_DIRECTORY)
        legacyFileNames.forEach { File(directory, it).delete() }
        directory.delete()
    }
}
