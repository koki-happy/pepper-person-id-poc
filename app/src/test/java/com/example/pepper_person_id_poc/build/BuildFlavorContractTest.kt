package com.example.pepper_person_id_poc.build

import com.example.pepper_person_id_poc.BuildConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildFlavorContractTest {
    @Test
    fun distributionFlavorHasIsolatedApplicationIdAndMetricsDefault() {
        when (BuildConfig.FLAVOR) {
            "benchmark" -> {
                assertThat(BuildConfig.DISTRIBUTION_FLAVOR).isEqualTo("benchmark")
                assertThat(BuildConfig.APPLICATION_ID)
                    .isEqualTo("com.example.pepper_person_id_poc.benchmark")
                assertThat(BuildConfig.PERSIST_METRICS_BY_DEFAULT).isTrue()
            }

            "candidate" -> {
                assertThat(BuildConfig.DISTRIBUTION_FLAVOR).isEqualTo("candidate")
                assertThat(BuildConfig.APPLICATION_ID)
                    .isEqualTo("com.example.pepper_person_id_poc.candidate")
                assertThat(BuildConfig.PERSIST_METRICS_BY_DEFAULT).isFalse()
            }

            else -> error("Unsupported distribution flavor: ${BuildConfig.FLAVOR}")
        }
    }
}
