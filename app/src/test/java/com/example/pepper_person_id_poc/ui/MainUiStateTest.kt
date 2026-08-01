package com.example.pepper_person_id_poc.ui

import com.example.pepper_person_id_poc.ui.navigation.AppScreen
import com.example.pepper_person_id_poc.ui.state.MainUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MainUiStateTest {
    @Test
    fun initialScreenIsAnonymousFaceIdentification() {
        assertThat(MainUiState().activeScreen).isEqualTo(AppScreen.AnonymousFaceIdentification)
    }
}
