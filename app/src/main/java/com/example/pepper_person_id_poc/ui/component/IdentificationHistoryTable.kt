package com.example.pepper_person_id_poc.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val IDENTIFICATION_HISTORY_LIMIT = 50

data class IdentificationTableEntry(
    val label: String,
    val id: String,
    val similarity: String,
    val candidates: List<IdentificationCandidateEntry> = emptyList(),
)

data class IdentificationCandidateEntry(
    val id: String,
    val similarity: String,
)

private data class IdentificationSnapshot(
    val time: String,
    val entries: Map<String, IdentificationTableEntry>,
)

@Composable
fun IdentificationHistoryTable(
    entries: List<IdentificationTableEntry>,
    observationSequence: Long,
    labelHeader: String,
    similarityHeader: String,
    labelPrefix: String,
    idPrefix: String,
) {
    val history = remember(labelHeader) { mutableStateListOf<IdentificationSnapshot>() }
    LaunchedEffect(observationSequence) {
        if (entries.isNotEmpty()) {
            history += IdentificationSnapshot(
                time = SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date()),
                entries = entries.associateBy { it.label },
            )
            if (history.size > IDENTIFICATION_HISTORY_LIMIT) history.removeAt(0)
        }
    }

    val snapshots = history.asReversed()
    val labels = orderLabelsByLatestObservation(
        snapshotsNewestFirst = listOf(entries.map { it.label }.toSet()) +
            snapshots.map { it.entries.keys },
    )
    val headers = listOf(labelHeader, similarityHeader) + snapshots.map { it.time }
    val rows = if (labels.isEmpty()) {
        listOf(listOf("$labelPrefix:―", "―") + snapshots.map { "―" })
    } else {
        labels.map { label ->
            val observedIds = snapshots.mapNotNull { it.entries[label]?.id }
            val agreement = calculateIdentificationAgreement(observedIds)
                ?.let { String.format(Locale.US, "%.2f", it) }
                ?: "―"
            listOf(
                "$labelPrefix:${label.numberSuffix()}",
                agreement,
            ) + snapshots.map { snapshot ->
                snapshot.entries[label]?.let { formatTopThreeCandidateCell(it, idPrefix) } ?: "―\n―\n―"
            }
        }
    }
    ScrollableDataTable(headers = headers, rows = rows, maxDataLines = 3)
}

internal fun formatTopThreeCandidateCell(entry: IdentificationTableEntry, idPrefix: String): String {
    val ranked = entry.candidates.take(3).map { "$idPrefix:${it.id.numberSuffix()} (${it.similarity})" }
        .ifEmpty { listOf("$idPrefix:${entry.id.numberSuffix()} (${entry.similarity})") }
    return (ranked + List(3 - ranked.size) { "―" }).joinToString("\n")
}

internal fun calculateIdentificationAgreement(ids: List<String>): Double? {
    if (ids.isEmpty()) return null
    val mostFrequentCount = ids.groupingBy { it }.eachCount().maxOf { it.value }
    return mostFrequentCount.toDouble() / ids.size
}

internal fun orderLabelsByLatestObservation(
    snapshotsNewestFirst: List<Set<String>>,
): List<String> {
    val labels = snapshotsNewestFirst.flatMap(Set<String>::toList).distinct()
    return labels.sortedWith(
        compareBy<String> { label ->
            snapshotsNewestFirst.indexOfFirst { label in it }
                .takeIf { it >= 0 }
                ?: Int.MAX_VALUE
        }.thenBy { it.numberSuffix().toIntOrNull() ?: Int.MAX_VALUE }
            .thenBy { it },
    )
}

private fun String.numberSuffix(): String = substringAfterLast('-').ifBlank { this }
