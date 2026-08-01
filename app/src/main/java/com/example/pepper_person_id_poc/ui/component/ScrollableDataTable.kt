package com.example.pepper_person_id_poc.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DataTableHeaderGroup(val label: String, val span: Int) {
    init { require(span > 0) }
}

@Composable
fun ScrollableDataTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 116.dp,
    columnWidths: List<Dp>? = null,
    maxDataLines: Int = 1,
    headerGroups: List<DataTableHeaderGroup> = emptyList(),
) {
    require(headers.isNotEmpty())
    require(columnWidths == null || columnWidths.size == headers.size)
    require(headerGroups.isEmpty() || headerGroups.sumOf { it.span } == headers.size)
    val scrollState = remember(headers) { ScrollState(0) }
    Column(modifier.horizontalScroll(scrollState)) {
        if (headerGroups.isNotEmpty()) {
            GroupedHeaderRow(headerGroups, columnWidth, columnWidths)
            HorizontalDivider()
        }
        DataRow(
            values = headers,
            columnWidth = columnWidth,
            columnWidths = columnWidths,
            header = true,
            maxLines = 2,
        )
        HorizontalDivider()
        rows.forEach { row ->
            DataRow(
                values = List(headers.size) { index -> row.getOrElse(index) { "―" } },
                columnWidth = columnWidth,
                columnWidths = columnWidths,
                header = false,
                maxLines = maxDataLines,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun GroupedHeaderRow(
    groups: List<DataTableHeaderGroup>,
    columnWidth: Dp,
    columnWidths: List<Dp>?,
) {
    var columnIndex = 0
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        groups.forEach { group ->
            var groupWidth = 0.dp
            repeat(group.span) {
                val width = columnWidths?.get(columnIndex) ?: columnWidth
                groupWidth += if (columnIndex == 0) width + 16.dp else width
                columnIndex++
            }
            Text(
                text = group.label,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                modifier = Modifier.width(groupWidth),
            )
        }
    }
}

@Composable
private fun DataRow(
    values: List<String>,
    columnWidth: Dp,
    columnWidths: List<Dp>?,
    header: Boolean,
    maxLines: Int,
) {
    Row(
        Modifier.background(
            if (header) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        values.forEachIndexed { index, value ->
            Text(
                text = value,
                fontWeight = if (header || index == 0) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.labelSmall,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(
                    (columnWidths?.get(index) ?: columnWidth) + if (index == 0) 16.dp else 0.dp,
                ),
            )
        }
    }
}
