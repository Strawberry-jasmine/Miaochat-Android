package com.example.relaychat.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.relaychat.R
import com.example.relaychat.app.RelayChatUiState
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.ui.components.RelayEmptyStateCard
import com.example.relaychat.ui.components.RelayInfoPill
import com.example.relaychat.ui.components.RelaySectionEyebrow
import com.example.relaychat.ui.components.relayOutlinedTextFieldColors
import com.example.relaychat.ui.strings.localizedThreadTitle
import com.example.relaychat.ui.strings.rememberHistoryTextSet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadHistoryRail(
    uiState: RelayChatUiState,
    viewModel: RelayChatViewModel,
    onCopyTranscript: (ChatThread) -> Unit,
    onCollapse: () -> Unit,
    closeAfterThreadSelection: Boolean,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryQuickFilter.ALL) }
    var renameTarget by remember { mutableStateOf<ChatThread?>(null) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    val historyTextSet = rememberHistoryTextSet()
    val sections = remember(uiState.threads, uiState.selectedThreadId, query, filter, historyTextSet) {
        buildHistorySections(
            threads = uiState.threads,
            selectedThreadId = uiState.selectedThreadId,
            query = query,
            filter = filter,
            textSet = historyTextSet,
        )
    }
    val visibleCount = sections.sumOf { it.items.size }
    val currentThread = uiState.currentThread

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RelaySectionEyebrow(text = stringResource(R.string.history_rail_eyebrow))
                    Text(
                        text = stringResource(R.string.history_rail_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (query.isBlank()) {
                            pluralStringResource(
                                R.plurals.history_thread_total_count,
                                uiState.threads.size,
                                uiState.threads.size,
                            )
                        } else {
                            pluralStringResource(
                                R.plurals.history_thread_matching_count,
                                visibleCount,
                                visibleCount,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.selectedThreadId != null) {
                        RelayInfoPill(
                            text = if (uiState.visibleInFlightReply != null) {
                                stringResource(R.string.history_badge_replying)
                            } else {
                                stringResource(R.string.history_badge_current)
                            },
                            highlight = if (uiState.visibleInFlightReply != null) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    RailIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = stringResource(R.string.action_new_chat),
                        onClick = {
                            viewModel.createThread()
                            query = ""
                            if (closeAfterThreadSelection) {
                                onCollapse()
                            }
                        },
                    )
                    RailIconButton(
                        icon = Icons.Outlined.Menu,
                        contentDescription = stringResource(R.string.history_collapse_desc),
                        onClick = onCollapse,
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.history_search_label)) },
                singleLine = true,
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
                        label = { Text(option.label()) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (filter == option) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
                            },
                            labelColor = if (filter == option) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ),
                    )
                }
            }

            if (currentThread != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onCopyTranscript(currentThread) },
                        label = { Text(stringResource(R.string.action_copy_transcript)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    )
                    AssistChip(
                        onClick = { viewModel.clearCurrentThread() },
                        label = { Text(stringResource(R.string.action_clear_thread)) },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    )
                }
            }

            if (sections.isEmpty()) {
                RelayEmptyStateCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Search,
                    title = if (query.isBlank()) {
                        stringResource(R.string.history_empty_title)
                    } else {
                        stringResource(R.string.history_no_match_title)
                    },
                    body = if (query.isBlank()) {
                        stringResource(R.string.history_empty_body)
                    } else {
                        stringResource(R.string.history_no_match_body)
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 2.dp),
                ) {
                    sections.forEach { section ->
                        item(key = "section-${section.title}") {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        items(section.items, key = { it.thread.id }) { item ->
                            RailThreadRow(
                                item = item,
                                isBusy = uiState.visibleInFlightReply?.threadId == item.thread.id,
                                onOpen = {
                                    viewModel.selectThread(item.thread.id)
                                    if (closeAfterThreadSelection) {
                                        onCollapse()
                                    }
                                },
                                onCopyTranscript = { onCopyTranscript(item.thread) },
                                onDuplicate = { viewModel.duplicateThread(item.thread.id) },
                                onRename = {
                                    renameTarget = item.thread
                                    renameDraft = item.thread.title
                                },
                                onClear = { viewModel.clearCurrentThread() },
                                onDelete = { viewModel.deleteThread(item.thread.id) },
                            )
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
            title = { Text(stringResource(R.string.thread_rename_title)) },
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
                    },
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun RailThreadRow(
    item: HistoryThreadItem,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onCopyTranscript: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by rememberSaveable(item.thread.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (item.isSelected) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        },
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            if (item.isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = localizedThreadTitle(item.thread.title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.isSelected) {
                            RelayInfoPill(
                                text = stringResource(R.string.history_badge_current),
                                highlight = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (isBusy) {
                            RelayInfoPill(
                                text = stringResource(R.string.history_badge_replying),
                                highlight = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (item.matchCount > 0) {
                            RelayInfoPill(
                                text = pluralStringResource(
                                    R.plurals.history_hits_count,
                                    item.matchCount,
                                    item.matchCount,
                                ),
                                highlight = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Text(
                        text = item.preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = historyTime(item.thread.updatedAt) +
                            " • " +
                            pluralStringResource(
                                R.plurals.history_message_short_count,
                                item.thread.messages.size,
                                item.thread.messages.size,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box {
                    RailIconButton(
                        icon = Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(R.string.history_more_actions_desc),
                        onClick = { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (!item.isSelected) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_open)) },
                                onClick = {
                                    menuExpanded = false
                                    onOpen()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_transcript)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCopyTranscript()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        if (item.isSelected) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_clear_thread)) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onClear()
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        tonalElevation = 0.dp,
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HistoryQuickFilter.label(): String = when (this) {
    HistoryQuickFilter.ALL -> stringResource(R.string.history_filter_all)
    HistoryQuickFilter.TODAY -> stringResource(R.string.history_filter_today)
    HistoryQuickFilter.THIS_WEEK -> stringResource(R.string.history_filter_this_week)
    HistoryQuickFilter.EARLIER -> stringResource(R.string.history_filter_earlier)
}

private val historyFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun historyTime(epochMillis: Long): String =
    historyFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

