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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.relaychat.R
import com.example.relaychat.app.InFlightAssistantReply
import com.example.relaychat.app.InFlightAssistantStage
import com.example.relaychat.app.PendingReplyVisuals
import com.example.relaychat.app.PendingTimelineStatus
import com.example.relaychat.app.RelayChatUiState
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.app.pendingReplyVisuals
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.RequestTuningPreset
import com.example.relaychat.ui.components.RelayEmptyStateCard
import com.example.relaychat.ui.components.RelaySectionEyebrow
import com.example.relaychat.ui.components.relayOutlinedTextFieldColors
import com.example.relaychat.ui.history.ThreadHistoryRail
import com.example.relaychat.ui.strings.localizedThreadTitle
import com.example.relaychat.ui.strings.rememberPendingReplyTextSet
import com.example.relaychat.ui.strings.stringFor
import com.example.relaychat.ui.strings.titleRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    uiState: RelayChatUiState,
    viewModel: RelayChatViewModel,
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
    val pendingTextSet = rememberPendingReplyTextSet()
    val draftFromUserNote = stringResource(R.string.chat_note_draft_user)
    val draftFromAssistantNote = stringResource(R.string.chat_note_draft_assistant)
    val messageCount = currentThread?.messages?.size ?: 0
    val threadSubtitle = when {
        currentThread == null || messageCount == 0 -> stringResource(R.string.chat_header_subtitle_empty)
        uiState.isSending -> stringResource(R.string.chat_header_subtitle_reply_in_progress, messageCount)
        messageCount == 1 -> stringResource(R.string.chat_header_subtitle_message_one)
        else -> stringResource(R.string.chat_header_subtitle_message_many, messageCount)
    }

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

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val railLayoutMode = historyRailLayoutMode(maxWidth)
        val overlayRailWidth = minOf(maxWidth * 0.88f, 360.dp)
        var historyRailExpanded by rememberSaveable(railLayoutMode) { mutableStateOf(false) }
        val overlayDismissInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = railLayoutMode == HistoryRailLayoutMode.PERSISTENT && historyRailExpanded,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                ) {
                    ThreadHistoryRail(
                        uiState = uiState,
                        viewModel = viewModel,
                        onCopyTranscript = { thread ->
                            onCopyTranscript(buildTranscript(thread.messages, context))
                        },
                        onCollapse = { historyRailExpanded = false },
                        closeAfterThreadSelection = false,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(320.dp)
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RelaySectionEyebrow(text = appName)
                            Text(
                                text = localizedThreadTitle(currentThread?.title.orEmpty()),
                                style = MaterialTheme.typography.displaySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = threadSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!historyRailExpanded) {
                                HeaderChromeIconButton(
                                    icon = Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.history_expand_desc),
                                    onClick = { historyRailExpanded = true },
                                )
                            }
                            Box {
                                HeaderChromeIconButton(
                                    icon = Icons.Outlined.MoreHoriz,
                                    contentDescription = stringResource(R.string.chat_menu_desc),
                                    onClick = { actionMenuExpanded = true },
                                )
                                DropdownMenu(
                                    expanded = actionMenuExpanded,
                                    onDismissRequest = { actionMenuExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_new_chat)) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            viewModel.createThread()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_rename)) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            renameDraft = currentThread?.title.orEmpty()
                                            renameDialogVisible = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_duplicate)) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            viewModel.duplicateCurrentThread()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_copy_transcript)) },
                                        onClick = {
                                            actionMenuExpanded = false
                                            onCopyTranscript(buildTranscript(currentThread?.messages.orEmpty(), context))
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_clear_thread)) },
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
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 10.dp),
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
                                    title = stringResource(R.string.chat_empty_state_title),
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
                                                draftFromUserNote
                                            } else {
                                                draftFromAssistantNote
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
                            Spacer(modifier = Modifier.height(12.dp))
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
                        pendingTextSet = pendingTextSet,
                    )
                }
            }

            AnimatedVisibility(
                visible = railLayoutMode == HistoryRailLayoutMode.OVERLAY && historyRailExpanded,
                enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it / 4 }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it / 5 }) + androidx.compose.animation.fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f))
                            .clickable(
                                interactionSource = overlayDismissInteraction,
                                indication = null,
                            ) {
                                historyRailExpanded = false
                            }
                    )
                    ThreadHistoryRail(
                        uiState = uiState,
                        viewModel = viewModel,
                        onCopyTranscript = { thread ->
                            onCopyTranscript(buildTranscript(thread.messages, context))
                        },
                        onCollapse = { historyRailExpanded = false },
                        closeAfterThreadSelection = true,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(overlayRailWidth)
                            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
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
                        viewModel.renameCurrentThread(renameDraft)
                        renameDialogVisible = false
                    }
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) { Text(stringResource(R.string.action_cancel)) }
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
    val context = LocalContext.current
    val currentThread = uiState.currentThread
    var advancedControlsVisible by rememberSaveable(uiState.settings.provider.presetId) { mutableStateOf(false) }
    val activePreset = remember(uiState.controls, uiState.settings.provider) {
        RequestTuningPreset.entries.firstOrNull { preset ->
            preset.matches(uiState.controls, uiState.settings.provider)
        }
    }
    val threadSummary = when (val count = currentThread?.messages?.size ?: 0) {
        0 -> stringResource(R.string.chat_controls_thread_fresh)
        else -> pluralStringResource(R.plurals.thread_message_count, count, count)
    }
    val providerLabel = uiState.settings.provider.displayName
    val webSearchLabel = if (!uiState.settings.provider.supportsWebSearch) {
        stringResource(R.string.chat_controls_web_unavailable)
    } else if (uiState.controls.webSearchEnabled) {
        stringResource(R.string.chat_controls_web_on)
    } else {
        stringResource(R.string.chat_controls_web_off)
    }
    val tuneLabel = if (advancedControlsVisible) {
        stringResource(R.string.chat_controls_less)
    } else {
        stringResource(R.string.chat_controls_tune)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = androidx.compose.animation.core.tween(280, easing = FastOutSlowInEasing))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ControlStatusPill(
                    text = providerLabel,
                    icon = Icons.Outlined.AutoAwesome,
                    highlight = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                ControlTogglePill(
                    text = webSearchLabel,
                    icon = Icons.Outlined.Public,
                    selected = uiState.controls.webSearchEnabled && uiState.settings.provider.supportsWebSearch,
                    enabled = uiState.settings.provider.supportsWebSearch,
                    onClick = { onWebSearchChanged(!uiState.controls.webSearchEnabled) },
                )
                ControlStatusPill(
                    text = activePreset?.let { stringResource(it.titleRes()) } ?: stringResource(R.string.chat_controls_custom),
                    icon = Icons.Outlined.ChatBubbleOutline,
                    highlight = if (activePreset == RequestTuningPreset.DEEP) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                ControlActionPill(
                    text = tuneLabel,
                    onClick = { advancedControlsVisible = !advancedControlsVisible },
                    highlight = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RequestTuningPreset.entries.forEach { preset ->
                    FilterChip(
                        modifier = Modifier.heightIn(min = CHAT_CONTROL_PILL_MIN_HEIGHT),
                        selected = preset.matches(uiState.controls, uiState.settings.provider),
                        onClick = { onApplyPreset(preset) },
                        label = { Text(stringResource(preset.titleRes())) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (preset.matches(uiState.controls, uiState.settings.provider)) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                            },
                        ),
                    )
                }
            }

            AnimatedVisibility(visible = advancedControlsVisible) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EnumMenuButton(
                            title = stringResource(R.string.chat_controls_reasoning),
                            value = context.stringFor(uiState.controls.reasoningEffort),
                            options = com.example.relaychat.core.model.ReasoningEffort.entries,
                            optionLabel = { context.stringFor(it) },
                            onSelected = onReasoningSelected,
                        )

                        if (uiState.settings.provider.supportsVerbosity) {
                            EnumMenuButton(
                                title = stringResource(R.string.chat_controls_verbosity),
                                value = context.stringFor(uiState.controls.verbosity),
                                options = com.example.relaychat.core.model.VerbosityLevel.entries,
                                optionLabel = { context.stringFor(it) },
                                onSelected = onVerbositySelected,
                            )
                        }

                        if (currentThread?.messages?.lastOrNull()?.role == ChatRole.ASSISTANT && !uiState.isSending) {
                            ControlActionPill(
                                text = stringResource(R.string.action_regenerate),
                                onClick = onRegenerate,
                                highlight = MaterialTheme.colorScheme.tertiary,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.84f),
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }

                    Text(
                        text = "$threadSummary • ${uiState.settings.provider.model} • ${uiState.resolvedEndpoint}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    pendingTextSet: com.example.relaychat.app.PendingReplyTextSet,
) {
    val reply = uiState.visibleInFlightReply
    val pendingVisuals = remember(reply?.threadId, reply?.stage, reply?.detail, reply?.text) {
        reply?.let { pendingReplyVisuals(reply = it, textSet = pendingTextSet) }
    }
    val busyLabel = pendingVisuals?.title ?: stringResource(R.string.pending_title_thinking)
    val busyDetail = pendingVisuals?.detail ?: stringResource(R.string.chat_busy_default_detail)
    val busyAccent = when (reply?.stage) {
        InFlightAssistantStage.SEARCHING -> MaterialTheme.colorScheme.secondary
        InFlightAssistantStage.STREAMING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                                Color.Transparent,
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(visible = uiState.isSending || uiState.composerNote != null) {
                    if (uiState.isSending) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ControlStatusPill(
                                text = busyLabel,
                                icon = pendingStageIcon(reply?.stage ?: InFlightAssistantStage.THINKING),
                                highlight = busyAccent,
                            )
                            Text(
                                text = busyDetail,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = uiState.composerNote.orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onClearComposerContext) {
                                Text(stringResource(R.string.action_clear))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.attachment != null) {
                    uiState.attachment?.let { attachment ->
                        val bitmap = remember(attachment.id) {
                            BitmapFactory.decodeByteArray(attachment.data, 0, attachment.data.size)
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f),
                            tonalElevation = 0.dp,
                            border = BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.chat_composer_attachment_title),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        attachment.mimeType,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = onClearAttachment) {
                                    Text(stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                    tonalElevation = 0.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                        ) {
                            IconButton(onClick = onPickImage) {
                                Icon(
                                    Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = stringResource(R.string.chat_composer_attach_image_desc),
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 56.dp, max = 148.dp),
                            placeholder = {
                                Text(
                                    if (uiState.isSending) {
                                        stringResource(R.string.chat_composer_placeholder_busy)
                                    } else {
                                        stringResource(R.string.chat_composer_placeholder_idle)
                                    }
                                )
                            },
                            minLines = 1,
                            maxLines = 5,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            ),
                        )

                        FilledTonalButton(
                            onClick = {
                                if (!uiState.isSending) {
                                    onSend()
                                }
                            },
                            enabled = uiState.canSend || uiState.isSending,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .animateContentSize(animationSpec = androidx.compose.animation.core.tween(220, easing = FastOutSlowInEasing)),
                            contentPadding = PaddingValues(
                                horizontal = if (uiState.isSending) 16.dp else 14.dp,
                                vertical = 10.dp,
                            ),
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
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                    Text(stringResource(R.string.chat_composer_thinking_button))
                                }
                            } else {
                                Icon(
                                    Icons.Outlined.ArrowUpward,
                                    contentDescription = stringResource(R.string.chat_composer_send_desc),
                                )
                            }
                        }
                    }
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
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 10.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
    }
    val bubbleBrush = if (isOutgoing) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, 0.22f),
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                if (darkTheme) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                } else {
                    Color.White.copy(alpha = 0.94f)
                },
                if (darkTheme) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                },
            )
        )
    }
    val textColor = if (isOutgoing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val bubbleBorder = when {
        isOutgoing -> Color.White.copy(alpha = if (darkTheme) 0.14f else 0.24f)
        isLatestAssistant -> MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.26f else 0.18f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = if (darkTheme) 0.40f else 0.24f)
    }
    val bubbleShadow = when {
        isOutgoing -> MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.22f else 0.16f)
        isLatestAssistant -> MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.18f else 0.10f)
        else -> Color.Black.copy(alpha = if (darkTheme) 0.24f else 0.08f)
    }
    val metadata = remember(message) { messageMetaLine(message) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isOutgoing) 0.84f else 0.94f)
                    .shadow(
                        elevation = if (isOutgoing) 18.dp else if (isLatestAssistant) 18.dp else 10.dp,
                        shape = bubbleShape,
                        ambientColor = bubbleShadow,
                        spotColor = bubbleShadow,
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    ),
                shape = bubbleShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, bubbleBorder),
            ) {
                Box(modifier = Modifier.background(bubbleBrush)) {
                    if (!isOutgoing && isLatestAssistant) {
                        Box(
                            modifier = Modifier
                                .padding(start = 10.dp, top = 12.dp, bottom = 12.dp)
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.32f),
                                        )
                                    )
                                )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = androidx.compose.animation.core.tween(280, easing = FastOutSlowInEasing))
                            .padding(
                                start = if (!isOutgoing && isLatestAssistant) 20.dp else 16.dp,
                                end = 16.dp,
                                top = 15.dp,
                                bottom = 15.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp)),
                                )
                            }
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (message.text.isNotBlank()) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_use_as_draft)) },
                        onClick = {
                            menuExpanded = false
                            onUseAsDraft()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_branch_thread)) },
                    onClick = {
                        menuExpanded = false
                        onBranch()
                    },
                )
                if (!isOutgoing && isLatestAssistant) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_regenerate_last_reply)) },
                        onClick = {
                            menuExpanded = false
                            onRegenerate()
                        },
                    )
                }
            }
        }

        Text(
            text = metadata,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun PendingAssistantBubble(reply: InFlightAssistantReply) {
    val pendingTextSet = rememberPendingReplyTextSet()
    val nowMs by produceState(initialValue = System.currentTimeMillis(), key1 = reply.threadId, key2 = reply.stage, key3 = reply.text.length) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val visuals = remember(reply, nowMs, pendingTextSet) {
        pendingReplyVisuals(reply = reply, textSet = pendingTextSet, nowMs = nowMs)
    }
    val accent = pendingStageAccent(reply.stage)
    val bubbleShape = RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .shadow(
                    elevation = 18.dp,
                    shape = bubbleShape,
                    ambientColor = accent.copy(alpha = 0.14f),
                    spotColor = accent.copy(alpha = 0.14f),
                ),
            shape = bubbleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, top = 12.dp, bottom = 12.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.08f),
                                    accent.copy(alpha = 0.70f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f),
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlStatusPill(
                            text = visuals.stateLabel.replaceFirstChar { it.titlecase() },
                            icon = pendingStageIcon(reply.stage),
                            highlight = accent,
                        )
                        Text(
                            text = visuals.elapsedLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = visuals.title,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = visuals.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = visuals.detail,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    PendingTimeline(
                        visuals = visuals,
                        activeColor = accent,
                    )
                    AnimatedStatusBeam(reply = reply)

                    if (reply.text.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(accent.copy(alpha = 0.12f))
                        )
                        MessageContent(
                            text = reply.text,
                            isOutgoing = false,
                            onCopyCode = {},
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingTimeline(
    visuals: PendingReplyVisuals,
    activeColor: Color,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visuals.timeline.forEach { entry ->
            val background = when (entry.status) {
                PendingTimelineStatus.COMPLETED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                PendingTimelineStatus.ACTIVE -> activeColor.copy(alpha = 0.16f)
                PendingTimelineStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
            }
            val border = when (entry.status) {
                PendingTimelineStatus.COMPLETED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                PendingTimelineStatus.ACTIVE -> activeColor.copy(alpha = 0.28f)
                PendingTimelineStatus.UPCOMING -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            }
            Text(
                text = entry.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(background)
                    .border(1.dp, border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AnimatedStatusBeam(reply: InFlightAssistantReply) {
    val transition = rememberInfiniteTransition(label = "status-beam")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = pendingStatusBeamDurationMillis(reply.stage)
                0f at 0
                1f at durationMillis
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "status-beam-offset",
    )
    val beamWidthFraction = 0.24f
    val accent = pendingStageAccent(reply.stage)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)),
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val beamStartOffsetPx = pendingStatusBeamStartOffsetPx(
            trackWidthPx = trackWidthPx,
            beamWidthFraction = beamWidthFraction,
            progress = progress,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(beamWidthFraction)
                .height(8.dp)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = beamStartOffsetPx
                    alpha = if (reply.stage == InFlightAssistantStage.STREAMING) 0.92f else 1f
                }
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.12f),
                            accent.copy(alpha = 0.22f),
                            accent.copy(alpha = 0.76f),
                            accent.copy(alpha = 0.30f),
                            Color.Transparent,
                        )
                    )
                )
        )
    }
}

private val CHAT_CONTROL_PILL_MIN_HEIGHT = 40.dp

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
        ControlActionPill(
            text = "$title • $value",
            onClick = { expanded = true },
        )
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

@Composable
private fun HeaderChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
        ),
    ) {
        IconButton(
            modifier = Modifier.size(40.dp),
            onClick = onClick,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ControlActionPill(
    text: String,
    onClick: () -> Unit,
    highlight: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = highlight.copy(alpha = 0.16f),
        ),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = CHAT_CONTROL_PILL_MIN_HEIGHT)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ControlTogglePill(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlight = if (selected) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
    }
    val contentColor = if (selected && enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected && enabled) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = CHAT_CONTROL_PILL_MIN_HEIGHT)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) highlight else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ControlStatusPill(
    text: String,
    icon: ImageVector,
    highlight: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = highlight.copy(alpha = 0.10f),
        tonalElevation = 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = highlight.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = CHAT_CONTROL_PILL_MIN_HEIGHT)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = highlight,
            )
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun pendingStageAccent(stage: InFlightAssistantStage): Color = when (stage) {
    InFlightAssistantStage.THINKING -> MaterialTheme.colorScheme.primary
    InFlightAssistantStage.SEARCHING -> MaterialTheme.colorScheme.secondary
    InFlightAssistantStage.STREAMING -> MaterialTheme.colorScheme.tertiary
}

private fun pendingStageIcon(stage: InFlightAssistantStage): ImageVector = when (stage) {
    InFlightAssistantStage.THINKING -> Icons.Outlined.AutoAwesome
    InFlightAssistantStage.SEARCHING -> Icons.Outlined.Public
    InFlightAssistantStage.STREAMING -> Icons.Outlined.ChatBubbleOutline
}

private fun messageMetaLine(message: ChatMessage): String {
    val parts = mutableListOf(timeText(message.createdAt))
    if (message.role == ChatRole.ASSISTANT) {
        message.model?.takeIf { it.isNotBlank() }?.let(parts::add)
        message.requestId?.takeIf { it.isNotBlank() }?.let { parts += compactRequestId(it) }
    }
    return parts.joinToString(" • ")
}

private fun compactRequestId(requestId: String): String {
    val compact = if (requestId.length <= 10) {
        requestId
    } else {
        "${requestId.take(4)}…${requestId.takeLast(4)}"
    }
    return "req $compact"
}

private fun buildTranscript(
    messages: List<ChatMessage>,
    context: android.content.Context,
): String = messages.joinToString("\n\n") { message ->
    val role = if (message.role == ChatRole.USER) {
        context.getString(R.string.transcript_role_user)
    } else {
        context.getString(R.string.transcript_role_assistant)
    }
    val text = message.text.trim().ifEmpty { context.getString(R.string.transcript_empty_text) }
    val attachmentNote = if (message.attachments.isEmpty()) {
        ""
    } else {
        "\n" + context.resources.getQuantityString(
            R.plurals.transcript_attachment_count,
            message.attachments.size,
            message.attachments.size,
        )
    }
    "$role:\n$text$attachmentNote"
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun timeText(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

