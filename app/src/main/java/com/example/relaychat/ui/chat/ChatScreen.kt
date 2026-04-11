package com.example.relaychat.ui.chat

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.relaychat.R
import com.example.relaychat.app.InFlightAssistantReply
import com.example.relaychat.app.PendingTimelineStatus
import com.example.relaychat.app.RelayChatUiState
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.app.pendingReplyVisuals
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.RequestTuningPreset
import com.example.relaychat.ui.components.RelayEmptyStateCard
import com.example.relaychat.ui.components.RelayGlassCard
import com.example.relaychat.ui.components.RelayInfoPill
import com.example.relaychat.ui.components.RelaySectionEyebrow
import com.example.relaychat.ui.components.relayOutlinedTextFieldColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    uiState: RelayChatUiState,
    viewModel: RelayChatViewModel,
    onShowHistory: () -> Unit,
    onCopyTranscript: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var actionMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var renameDialogVisible by rememberSaveable { mutableStateOf(false) }
    var renameDraft by rememberSaveable { mutableStateOf("") }
    val currentThread = uiState.currentThread
    val messageListState = rememberLazyListState()
    val appName = stringResource(R.string.app_name)

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            viewModel.attachImage(bytes, mimeType)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RelayGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accent = MaterialTheme.colorScheme.primary,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RelaySectionEyebrow(text = appName)
                    Text(
                        text = currentThread?.title ?: "New Chat",
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${uiState.settings.provider.displayName} | ${uiState.settings.provider.model}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = onShowHistory,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(Icons.Outlined.History, contentDescription = "History")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("History")
                        }
                        RelayInfoPill(
                            text = if (uiState.controls.webSearchEnabled) "Web enabled" else "Web off",
                            icon = if (uiState.controls.webSearchEnabled) Icons.Outlined.Public else Icons.Outlined.AutoAwesome,
                            highlight = if (uiState.controls.webSearchEnabled) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }

                Box {
                    IconButton(onClick = { actionMenuExpanded = true }) {
                        Icon(Icons.Outlined.MoreHoriz, contentDescription = "Actions")
                    }
                    DropdownMenu(
                        expanded = actionMenuExpanded,
                        onDismissRequest = { actionMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("New chat") },
                            onClick = {
                                actionMenuExpanded = false
                                viewModel.createThread()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename thread") },
                            onClick = {
                                actionMenuExpanded = false
                                renameDraft = currentThread?.title.orEmpty()
                                renameDialogVisible = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate thread") },
                            onClick = {
                                actionMenuExpanded = false
                                viewModel.duplicateCurrentThread()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Copy thread") },
                            onClick = {
                                actionMenuExpanded = false
                                onCopyTranscript(buildTranscript(currentThread?.messages.orEmpty()))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Clear thread") },
                            onClick = {
                                actionMenuExpanded = false
                                viewModel.clearCurrentThread()
                            },
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "controls") {
                ChatControlPanel(
                    uiState = uiState,
                    onApplyPreset = viewModel::applyTuningPreset,
                    onReasoningSelected = { reasoning ->
                        viewModel.updateControls { it.copy(reasoningEffort = reasoning) }
                    },
                    onVerbositySelected = { verbosity ->
                        viewModel.updateControls { it.copy(verbosity = verbosity) }
                    },
                    onWebSearchChanged = { enabled ->
                        viewModel.updateControls { it.copy(webSearchEnabled = enabled) }
                    },
                    onRegenerate = viewModel::regenerateLastAssistant,
                )
            }

            if (currentThread == null || currentThread.messages.isEmpty()) {
                item(key = "empty-state") {
                    RelayEmptyStateCard(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Start with a clean thread",
                        body = stringResource(R.string.chat_empty_state_body, appName),
                    )
                }
            } else {
                items(currentThread.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isLatestAssistant = message.id == currentThread.messages.lastOrNull()?.id &&
                            message.role == ChatRole.ASSISTANT,
                        onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                        onCopyCode = { clipboard.setText(AnnotatedString(it)) },
                        onUseAsDraft = {
                            viewModel.useMessageAsDraft(
                                message,
                                if (message.role == ChatRole.USER) {
                                    "Draft copied from an earlier user message"
                                } else {
                                    "Draft copied from an assistant reply"
                                },
                            )
                        },
                        onRegenerate = viewModel::regenerateLastAssistant,
                        onBranch = { viewModel.branchThread(message.id) },
                    )
                }
                uiState.visibleInFlightReply?.let { reply ->
                    item(key = "in-flight-${reply.threadId}") {
                        PendingAssistantBubble(reply = reply)
                    }
                }
            }

            item(key = "tail-space") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Composer(
            uiState = uiState,
            onDraftChange = viewModel::updateDraft,
            onSend = viewModel::send,
            onClearAttachment = viewModel::clearAttachment,
            onClearComposerContext = viewModel::clearComposerContext,
            onPickImage = {
                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }

    LaunchedEffect(
        currentThread?.id,
        currentThread?.messages?.size,
        uiState.visibleInFlightReply?.text,
        uiState.visibleInFlightReply?.detail,
    ) {
        val contentItems = (currentThread?.messages?.size ?: 0) + if (uiState.visibleInFlightReply != null) 1 else 0
        if (contentItems > 0) {
            messageListState.animateScrollToItem(contentItems)
        } else {
            messageListState.scrollToItem(0)
        }
    }

    if (renameDialogVisible) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
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
                        viewModel.renameCurrentThread(renameDraft)
                        renameDialogVisible = false
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatControlPanel(
    uiState: RelayChatUiState,
    onApplyPreset: (RequestTuningPreset) -> Unit,
    onReasoningSelected: (com.example.relaychat.core.model.ReasoningEffort) -> Unit,
    onVerbositySelected: (com.example.relaychat.core.model.VerbosityLevel) -> Unit,
    onWebSearchChanged: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
) {
    val currentThread = uiState.currentThread
    RelayGlassCard(
        modifier = Modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.secondary,
    ) {
        RelaySectionEyebrow(text = "Controls")
        Text(
            text = "${currentThread?.messages?.size ?: 0} messages | ${uiState.resolvedEndpoint}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RequestTuningPreset.entries.forEach { preset ->
                FilterChip(
                    selected = preset.matches(uiState.controls, uiState.settings.provider),
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EnumMenuButton(
                title = "Reasoning",
                value = uiState.controls.reasoningEffort.name.lowercase(),
                options = com.example.relaychat.core.model.ReasoningEffort.entries,
                optionLabel = { it.name.lowercase() },
                onSelected = onReasoningSelected,
            )

            if (uiState.settings.provider.supportsVerbosity) {
                EnumMenuButton(
                    title = "Verbosity",
                    value = uiState.controls.verbosity.name.lowercase(),
                    options = com.example.relaychat.core.model.VerbosityLevel.entries,
                    optionLabel = { it.name.lowercase() },
                    onSelected = onVerbositySelected,
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Web/search",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = uiState.controls.webSearchEnabled,
                    onCheckedChange = onWebSearchChanged,
                    enabled = uiState.settings.provider.supportsWebSearch,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        checkedTrackColor = MaterialTheme.colorScheme.secondary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }

            if (currentThread?.messages?.lastOrNull()?.role == ChatRole.ASSISTANT && !uiState.isSending) {
                AssistChip(
                    onClick = onRegenerate,
                    label = { Text("Regenerate") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.74f),
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.tertiary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Composer(
    uiState: RelayChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearAttachment: () -> Unit,
    onClearComposerContext: () -> Unit,
    onPickImage: () -> Unit,
) {
    RelayGlassCard(
        modifier = Modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.primary,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        AnimatedVisibility(visible = uiState.composerNote != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.composerNote.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearComposerContext) {
                    Text("Clear")
                }
            }
        }

        AnimatedVisibility(visible = uiState.attachment != null) {
            uiState.attachment?.let { attachment ->
                val bitmap = remember(attachment.id) {
                    BitmapFactory.decodeByteArray(attachment.data, 0, attachment.data.size)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Image attached", style = MaterialTheme.typography.titleSmall)
                        Text(
                            attachment.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onClearAttachment) {
                        Text("Remove")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            FilledTonalButton(
                onClick = onPickImage,
                modifier = Modifier.padding(bottom = 4.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Attach image")
            }

            OutlinedTextField(
                value = uiState.draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp, max = 160.dp),
                placeholder = { Text("Ask anything, paste code, or attach an image...") },
                shape = RoundedCornerShape(24.dp),
                colors = relayOutlinedTextFieldColors(),
            )

            FilledTonalButton(
                onClick = {
                    if (!uiState.isSending) {
                        onSend()
                    }
                },
                enabled = uiState.canSend || uiState.isSending,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 4.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                if (uiState.isSending) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Sending")
                    }
                } else {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLatestAssistant: Boolean,
    onCopy: () -> Unit,
    onCopyCode: (String) -> Unit,
    onUseAsDraft: () -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
) {
    var menuExpanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val isOutgoing = message.role == ChatRole.USER
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bubbleBrush = if (isOutgoing) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, 0.18f),
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                if (darkTheme) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                } else {
                    Color.White.copy(alpha = 0.92f)
                },
                if (darkTheme) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                },
            )
        )
    }
    val textColor = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (isOutgoing) "You" else "Assistant",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isOutgoing) 0.9f else 0.96f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(bubbleBrush)
                    .border(
                        width = 1.dp,
                        color = if (isOutgoing) {
                            Color.White.copy(alpha = if (darkTheme) 0.14f else 0.24f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = if (darkTheme) 0.58f else 0.34f)
                        },
                        shape = RoundedCornerShape(26.dp),
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    )
                    .animateContentSize(animationSpec = androidx.compose.animation.core.tween(280, easing = FastOutSlowInEasing))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (message.text.isNotBlank()) {
                    Box(modifier = Modifier.alpha(0.98f)) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalContentColor provides textColor,
                        ) {
                            MessageContent(
                                text = message.text,
                                isOutgoing = isOutgoing,
                                onCopyCode = onCopyCode,
                            )
                        }
                    }
                }

                message.attachments.forEach { attachment ->
                    val bitmap = remember(attachment.id) {
                        BitmapFactory.decodeByteArray(attachment.data, 0, attachment.data.size)
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp)),
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Use as draft") },
                        onClick = {
                            menuExpanded = false
                            onUseAsDraft()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Branch thread here") },
                    onClick = {
                        menuExpanded = false
                        onBranch()
                    },
                )
                if (!isOutgoing && isLatestAssistant) {
                    DropdownMenuItem(
                        text = { Text("Regenerate last reply") },
                        onClick = {
                            menuExpanded = false
                            onRegenerate()
                        },
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = timeText(message.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.role == ChatRole.ASSISTANT) {
                message.model?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message.requestId?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "request $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingAssistantBubble(reply: InFlightAssistantReply) {
    val nowMs by produceState(initialValue = System.currentTimeMillis(), key1 = reply.threadId, key2 = reply.stage, key3 = reply.text.length) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val visuals = remember(reply, nowMs) { pendingReplyVisuals(reply = reply, nowMs = nowMs) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Assistant",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        RelayGlassCard(
            modifier = Modifier.fillMaxWidth(),
            accent = when (reply.stage) {
                com.example.relaychat.app.InFlightAssistantStage.THINKING -> MaterialTheme.colorScheme.primary
                com.example.relaychat.app.InFlightAssistantStage.SEARCHING -> MaterialTheme.colorScheme.secondary
                com.example.relaychat.app.InFlightAssistantStage.STREAMING -> MaterialTheme.colorScheme.tertiary
            },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RelayInfoPill(
                    text = visuals.stateLabel,
                    icon = when (reply.stage) {
                        com.example.relaychat.app.InFlightAssistantStage.THINKING -> Icons.Outlined.AutoAwesome
                        com.example.relaychat.app.InFlightAssistantStage.SEARCHING -> Icons.Outlined.Public
                        com.example.relaychat.app.InFlightAssistantStage.STREAMING -> Icons.Outlined.ChatBubbleOutline
                    },
                    highlight = when (reply.stage) {
                        com.example.relaychat.app.InFlightAssistantStage.THINKING -> MaterialTheme.colorScheme.primary
                        com.example.relaychat.app.InFlightAssistantStage.SEARCHING -> MaterialTheme.colorScheme.secondary
                        com.example.relaychat.app.InFlightAssistantStage.STREAMING -> MaterialTheme.colorScheme.tertiary
                    },
                )
                Text(
                    text = visuals.elapsedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = visuals.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = visuals.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = visuals.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PendingTimeline(visuals = visuals)
            AnimatedStatusBeam(reply = reply)

            if (reply.text.isNotBlank()) {
                MessageContent(
                    text = reply.text,
                    isOutgoing = false,
                    onCopyCode = {},
                )
            }
        }
    }
}

@Composable
private fun PendingTimeline(visuals: com.example.relaychat.app.PendingReplyVisuals) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        visuals.timeline.forEach { entry ->
            val background = when (entry.status) {
                PendingTimelineStatus.COMPLETED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                PendingTimelineStatus.ACTIVE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                PendingTimelineStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            }
            val border = when (entry.status) {
                PendingTimelineStatus.COMPLETED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                PendingTimelineStatus.ACTIVE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f)
                PendingTimelineStatus.UPCOMING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
            }
            Text(
                text = entry.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(background)
                    .border(1.dp, border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AnimatedStatusBeam(reply: InFlightAssistantReply) {
    val transition = rememberInfiniteTransition(label = "status-beam")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = when (reply.stage) {
                    com.example.relaychat.app.InFlightAssistantStage.SEARCHING -> 1_350
                    com.example.relaychat.app.InFlightAssistantStage.THINKING -> 1_050
                    com.example.relaychat.app.InFlightAssistantStage.STREAMING -> 850
                }
                0f at 0
                1f at durationMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "status-beam-offset",
    )

    val accent = when (reply.stage) {
        com.example.relaychat.app.InFlightAssistantStage.THINKING -> MaterialTheme.colorScheme.primary
        com.example.relaychat.app.InFlightAssistantStage.SEARCHING -> MaterialTheme.colorScheme.secondary
        com.example.relaychat.app.InFlightAssistantStage.STREAMING -> MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.32f)
                .height(10.dp)
                .align(Alignment.CenterStart)
                .padding(start = (220 * offset).dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            accent.copy(alpha = 0.78f),
                            accent.copy(alpha = 0.12f),
                        )
                    )
                )
        )
    }
}

@Composable
private fun <T> EnumMenuButton(
    title: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(999.dp),
        ) {
            Text("$title: $value")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

private fun buildTranscript(messages: List<ChatMessage>): String = messages.joinToString("\n\n") { message ->
    val role = if (message.role == ChatRole.USER) "You" else "Assistant"
    val text = message.text.trim().ifEmpty { "[No text]" }
    val attachmentNote = if (message.attachments.isEmpty()) {
        ""
    } else {
        "\n[Attachments: ${message.attachments.size} image${if (message.attachments.size == 1) "" else "s"}]"
    }
    "$role:\n$text$attachmentNote"
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun timeText(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
