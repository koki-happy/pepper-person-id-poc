package com.example.pepper_person_id_poc

import android.app.Application
import com.example.pepper_person_id_poc.application.session.AnonymousIdentificationSessionInitializer
import com.example.pepper_person_id_poc.application.session.AnonymousSessionStartupState
import com.example.pepper_person_id_poc.infrastructure.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PepperPersonIdApplication : Application() {
    lateinit var container: AppContainer
        private set
    private lateinit var sessionInitializer: AnonymousIdentificationSessionInitializer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableStartupState =
        MutableStateFlow<AnonymousSessionStartupState>(AnonymousSessionStartupState.Initializing)
    val startupState: StateFlow<AnonymousSessionStartupState> = mutableStartupState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        sessionInitializer = AnonymousIdentificationSessionInitializer(
            container.anonymousFaceClusterRepository,
            container.anonymousSpeakerClusterRepository,
        )
        initializeSession()
    }

    fun initializeSession() {
        mutableStartupState.value = AnonymousSessionStartupState.Initializing
        scope.launch {
            runCatching {
                sessionInitializer.initializeNewSession()
            }.onSuccess {
                mutableStartupState.value = AnonymousSessionStartupState.Ready
            }.onFailure {
                mutableStartupState.value = AnonymousSessionStartupState.Error(
                    it.message ?: it::class.java.simpleName,
                )
            }
        }
    }
}
