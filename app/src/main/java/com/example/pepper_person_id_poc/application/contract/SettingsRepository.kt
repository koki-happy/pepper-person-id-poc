package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.config.PocSettings

interface SettingsRepository {
    fun load(): PocSettings
    fun save(settings: PocSettings)
}
