package com.example.pepper_person_id_poc.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IdentificationHistoryTableTest {
    @Test
    fun calculatesAgreementFromMostFrequentIdCount() {
        assertThat(calculateIdentificationAgreement(listOf("001", "001", "002", "001")))
            .isWithin(0.0001)
            .of(0.75)
    }

    @Test
    fun formatsTopThreeIdsAndSimilaritiesInOneCell() {
        val entry = IdentificationTableEntry(
            label = "face-001",
            id = "anonymous-face-001",
            similarity = "0.95",
            candidates = listOf(
                IdentificationCandidateEntry("anonymous-face-001", "0.95"),
                IdentificationCandidateEntry("anonymous-face-002", "0.81"),
                IdentificationCandidateEntry("anonymous-face-003", "0.72"),
                IdentificationCandidateEntry("anonymous-face-004", "0.61"),
            ),
        )

        assertThat(formatTopThreeCandidateCell(entry, "顔ID")).isEqualTo(
            "顔ID:001 (0.95)\n顔ID:002 (0.81)\n顔ID:003 (0.72)",
        )
    }

    @Test
    fun padsMissingCandidatesToThreeLines() {
        val entry = IdentificationTableEntry(
            label = "local-speaker-001",
            id = "anonymous-speaker-001",
            similarity = "0.90",
        )

        assertThat(formatTopThreeCandidateCell(entry, "話者ID")).isEqualTo(
            "話者ID:001 (0.90)\n―\n―",
        )
    }

    @Test
    fun ordersLabelsByTheirLatestIdentificationTime() {
        val ordered = orderLabelsByLatestObservation(
            snapshotsNewestFirst = listOf(
                setOf("face-003"),
                setOf("face-001", "face-010"),
                setOf("face-002", "face-003"),
            ),
        )

        assertThat(ordered).containsExactly(
            "face-003",
            "face-001",
            "face-010",
            "face-002",
        ).inOrder()
    }

    @Test
    fun ordersLabelsNumericallyWhenIdentifiedAtTheSameTime() {
        val ordered = orderLabelsByLatestObservation(
            snapshotsNewestFirst = listOf(setOf("local-speaker-010", "local-speaker-002")),
        )

        assertThat(ordered).containsExactly("local-speaker-002", "local-speaker-010").inOrder()
    }
}
