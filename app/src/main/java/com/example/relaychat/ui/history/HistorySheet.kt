package com.example.relaychat.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.relaychat.app.RelayChatUiState
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.ui.components.RelayEmptyStateCard
import com.example.relaychat.ui.components.RelayGlassCard
import com.example.relaychat.ui.components.RelayInfoPill
import com.example.relaychat.ui.components.RelaySectionEyebrow
import com.example.relaychat.ui.components.relayOutlinedTextFieldColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    uiState: RelayChatUiState,
    viewModel: RelayChatViewModel,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryQuickFilter.ALL) }
    var renameTarget by remember { mutableStateOf<ChatThread?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }

    val sections = remember(uiState.threads, uiState.selectedThreadId, query, filter) {
        buildHistorySections(
            threads = uiState.threads,
            selectedThreadId = uiState.selectedThreadId,
            query = query,
            filter = filter,
        )
    }
    val visibleCount = sections.sumOf { it.items.size }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RelayGlassCard(
                modifier = Modifier.fillMaxWidth(),
                accent = MaterialTheme.colorScheme.secondary,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
                RelaySectionEyebrow(text = "History")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Thread history",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (query.isBlank()) {
                                "${uiState.threads.size} total threads"
                            } else {
                                "$visibleCount matching threads"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RelayInfoPill(
                        text = if (uiState.selectedThreadId == null) "No active thread" else "Current ready",
                        icon = Icons.Outlined.History,
                        highlight = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search threads") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(22.dp),
                    colors = relayOutlinedTextFieldColors(),
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryQuickFilter.entries.forEach { option ->
                        AssistChip(
                            onClick = { filter = option },
                            label = { Text(option.label) },
                            leadingIcon = if (filter == option) {
                                { Icon(Icons.Outlined.History, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                    AssistChip(
                        onClick = {
                            viewModel.createThread()
                            onDismiss()
                        },
                        label = { Text("New chat") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    )
                }
            }

            if (sections.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    RelayEmptyStateCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Search,
                        title = if (query.isBlank()) "No saved threads yet" else "No matching threads",
                        body = if (query.isBlank()) {
                            "Open a new chat and your conversation history will appear here."
                        } else {
                            "Try a different keyword or switch the quick filter to widen the result set."
                        },
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { section ->
                        item(key = "section-${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                        items(section.items, key = { it.thread.id }) { item ->
                            RelayGlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                accent = if (item.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = item.thread.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (item.isSelected) {
                                                RelayInfoPill(
                                                    text = "Current",
                                                    highlight = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            if (item.matchCount > 0 && query.isNotBlank()) {
                                                RelayInfoPill(
                                                    text = "${item.matchCount} hit${if (item.matchCount == 1) "" else "s"}",
                                                    highlight = MaterialTheme.colorScheme.secondary,
                                                )
                                            }
                                        }

                                        Text(
                                            text = item.preview,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )

                                        Text(
                                            text = "${historyTime(item.thread.updatedAt)} | ${item.thread.messages.size} msg",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = {
                                            viewModel.selectThread(item.thread.id)
                                            onDismiss()
                                        }
                                    ) { Text("Open") }
                                    TextButton(onClick = { viewModel.duplicateThread(item.thread.id) }) { Text("Duplicate") }
                                    TextButton(
                                        onClick = {
                                            renameTarget = item.thread
                                            renameDraft = item.thread.title
                                        }
                                    ) { Text("Rename") }
                                    TextButton(onClick = { viewModel.deleteThread(item.thread.id) }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(28.dp),
            title = { Text("Rename thread") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = relayOutlinedTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget?.let { viewModel.renameThread(it.id, renameDraft) }
                        renameTarget = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }
}

private val HistoryQuickFilter.label: String
    get() = when (this) {
        HistoryQuickFilter.ALL -> "All"
        HistoryQuickFilter.TODAY -> "Today"
        HistoryQuickFilter.THIS_WEEK -> "This week"
        HistoryQuickFilter.EARLIER -> "Earlier"
    }

private val historyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun historyTime(epochMillis: Long): String =
    historyFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
