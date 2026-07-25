package com.example.pepper_person_id_poc.application.session

import com.example.pepper_person_id_poc.application.contract.AnonymousFaceClusterRepository
import com.example.pepper_person_id_poc.application.contract.AnonymousSpeakerClusterRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnonymousIdentificationSessionInitializer(
    private val faceRepository: AnonymousFaceClusterRepository,
    private val speakerRepository: AnonymousSpeakerClusterRepository,
) {
    private val mutex = Mutex()

    suspend fun initializeNewSession() = mutex.withLock {
        faceRepository.deleteAll()
        speakerRepository.deleteAll()
    }
}

sealed interface AnonymousSessionStartupState {
    data object Initializing : AnonymousSessionStartupState
    data object Ready : AnonymousSessionStartupState
    data class Error(val message: String) : AnonymousSessionStartupState
}
