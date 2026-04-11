package com.example.relaychat.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.relaychat.app.ImportedConfigSummary
import com.example.relaychat.app.RelayChatUiState
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.core.model.AppThemeMode
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.RequestTuningPreset
import com.example.relaychat.core.model.ResponseFormatMode
import com.example.relaychat.core.model.ToolChoiceMode
import com.example.relaychat.ui.components.RelayGlassCard
import com.example.relaychat.ui.components.RelayInfoPill
import com.example.relaychat.ui.components.RelaySectionEyebrow
import com.example.relaychat.ui.components.relayOutlinedTextFieldColors

@Composable
fun SettingsScreen(
    uiState: RelayChatUiState,
    viewModel: RelayChatViewModel,
    modifier: Modifier = Modifier,
) {
    var importDialogVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsOverview(uiState = uiState)

        SectionCard(
            title = "Appearance",
            accent = MaterialTheme.colorScheme.primary,
        ) {
            ThemeModeSelector(
                currentMode = uiState.settings.themeMode,
                onSelected = { mode ->
                    viewModel.updateSettings { settings ->
                        settings.copy(themeMode = mode)
                    }
                },
            )
            Text(
                text = "Theme choice applies immediately to chat, settings, history, cards, controls, and navigation. Follow system tracks Android light/dark mode changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Quick setup") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { importDialogVisible = true },
                    label = { Text("Import Codex config") },
                )
                AssistChip(
                    onClick = {
                        viewModel.applyProviderPreset(
                            ProviderPreset.INTELALLOC_CODEX,
                            status = "Applied the intelalloc Codex preset. Add or confirm the API key before sending.",
                        )
                    },
                    label = { Text("Use intelalloc preset") },
                )
            }
            AssistChip(
                onClick = {
                    viewModel.applyProviderPreset(ProviderPreset.OPENAI_RESPONSES)
                },
                label = { Text("Use OpenAI Responses preset") },
            )
            KeyValueRow(label = "Resolved endpoint", value = uiState.resolvedEndpoint)
            uiState.importStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        uiState.importSummary?.let {
            ImportSummarySection(summary = it)
        }

        SectionCard(title = "Preset") {
            EnumSelector(
                title = "Provider preset",
                currentLabel = ProviderPreset.fromId(uiState.settings.provider.presetId).title,
                options = ProviderPreset.entries,
                labelFor = { preset -> preset.title },
                onSelected = { preset ->
                    viewModel.applyProviderPreset(preset)
                },
            )
            Text(
                text = ProviderPreset.fromId(uiState.settings.provider.presetId).detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "Quality presets") {
            RequestTuningPreset.entries.forEach { preset ->
                TextButton(onClick = { viewModel.applyDefaultTuningPreset(preset) }) {
                    Text(preset.title)
                }
                Text(
                    text = preset.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = "Endpoint") {
            EnumSelector(
                title = "API style",
                currentLabel = uiState.settings.provider.apiStyle.name,
                options = com.example.relaychat.core.model.ProviderApiStyle.entries,
                labelFor = { it.name },
                onSelected = { style ->
                    viewModel.updateSettings {
                        it.copy(provider = it.provider.copy(apiStyle = style))
                    }
                },
            )
            SimpleTextField("Display name", uiState.settings.provider.displayName) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(displayName = it))
                }
            }
            SimpleTextField("Base URL", uiState.settings.provider.baseUrl) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(baseUrl = it))
                }
            }
            SimpleTextField("Path", uiState.settings.provider.path) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(path = it))
                }
            }
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::writeApiKey,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("API key") },
                shape = RoundedCornerShape(20.dp),
                colors = relayOutlinedTextFieldColors(),
            )
            SimpleTextField("Model", uiState.settings.provider.model) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(model = it))
                }
            }
        }

        SectionCard(title = "Instructions") {
            SimpleTextField(
                label = "Instructions / system prompt",
                value = uiState.settings.provider.instructionsPrompt,
                minLines = 3,
                maxLines = 8,
            ) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(instructionsPrompt = it))
                }
            }
        }

        SectionCard(title = "Default chat controls") {
            EnumSelector(
                title = "Reasoning",
                currentLabel = uiState.settings.defaultControls.reasoningEffort.name.lowercase(),
                options = com.example.relaychat.core.model.ReasoningEffort.entries,
                labelFor = { it.name.lowercase() },
                onSelected = { value ->
                    viewModel.updateSettings { settings ->
                        settings.copy(defaultControls = settings.defaultControls.copy(reasoningEffort = value))
                    }
                },
            )
            if (uiState.settings.provider.supportsVerbosity) {
                EnumSelector(
                    title = "Verbosity",
                    currentLabel = uiState.settings.defaultControls.verbosity.name.lowercase(),
                    options = com.example.relaychat.core.model.VerbosityLevel.entries,
                    labelFor = { it.name.lowercase() },
                    onSelected = { value ->
                        viewModel.updateSettings { settings ->
                            settings.copy(defaultControls = settings.defaultControls.copy(verbosity = value))
                        }
                    },
                )
            }
            SwitchRow(
                label = "Web/search by default",
                checked = uiState.settings.defaultControls.webSearchEnabled,
                enabled = uiState.settings.provider.supportsWebSearch,
            ) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(webSearchEnabled = checked))
                }
            }
            SwitchRow("Store responses on provider side", uiState.settings.defaultControls.responseStorageEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(responseStorageEnabled = checked))
                }
            }
            EnumSelector(
                title = "Tool choice",
                currentLabel = uiState.settings.defaultControls.toolChoice.name.lowercase(),
                options = ToolChoiceMode.entries,
                labelFor = { it.name.lowercase() },
                onSelected = { value ->
                    viewModel.updateSettings { settings ->
                        settings.copy(defaultControls = settings.defaultControls.copy(toolChoice = value))
                    }
                },
            )
        }

        SectionCard(title = "Sampling and determinism") {
            SwitchRow("Enable temperature", uiState.settings.defaultControls.temperatureEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(temperatureEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.temperatureEnabled) {
                NumberField(
                    label = "Temperature",
                    value = uiState.settings.defaultControls.temperature.toString(),
                    onValueChanged = { value ->
                        value.toDoubleOrNull()?.let { number ->
                            viewModel.updateSettings { settings ->
                                settings.copy(defaultControls = settings.defaultControls.copy(temperature = number))
                            }
                        }
                    },
                )
            }
            SwitchRow("Enable top_p", uiState.settings.defaultControls.topPEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(topPEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.topPEnabled) {
                NumberField(
                    label = "Top P",
                    value = uiState.settings.defaultControls.topP.toString(),
                    onValueChanged = { value ->
                        value.toDoubleOrNull()?.let { number ->
                            viewModel.updateSettings { settings ->
                                settings.copy(defaultControls = settings.defaultControls.copy(topP = number))
                            }
                        }
                    },
                )
            }
            SwitchRow("Limit max output tokens", uiState.settings.defaultControls.maxOutputTokensEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(maxOutputTokensEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.maxOutputTokensEnabled) {
                NumberField(
                    label = "Max output tokens",
                    value = uiState.settings.defaultControls.maxOutputTokens.toString(),
                    keyboardType = KeyboardType.Number,
                    onValueChanged = { value ->
                        value.toIntOrNull()?.let { number ->
                            viewModel.updateSettings { settings ->
                                settings.copy(defaultControls = settings.defaultControls.copy(maxOutputTokens = number))
                            }
                        }
                    },
                )
            }
            SwitchRow("Enable seed", uiState.settings.defaultControls.seedEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(seedEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.seedEnabled) {
                NumberField(
                    label = "Seed",
                    value = uiState.settings.defaultControls.seed.toString(),
                    keyboardType = KeyboardType.Number,
                    onValueChanged = { value ->
                        value.toIntOrNull()?.let { number ->
                            viewModel.updateSettings { settings ->
                                settings.copy(defaultControls = settings.defaultControls.copy(seed = number))
                            }
                        }
                    },
                )
            }
        }

        SectionCard(title = "Structured outputs") {
            EnumSelector(
                title = "Response format",
                currentLabel = uiState.settings.defaultControls.responseFormat.mode.name.lowercase(),
                options = ResponseFormatMode.entries,
                labelFor = { it.name.lowercase() },
                onSelected = { mode ->
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(mode = mode)
                            )
                        )
                    }
                },
            )

            if (uiState.settings.defaultControls.responseFormat.mode == ResponseFormatMode.JSON_SCHEMA) {
                SimpleTextField("Schema name", uiState.settings.defaultControls.responseFormat.schemaName) {
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(schemaName = it)
                            )
                        )
                    }
                }
                SimpleTextField("Schema description", uiState.settings.defaultControls.responseFormat.schemaDescription) {
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(schemaDescription = it)
                            )
                        )
                    }
                }
                SwitchRow("Strict schema", uiState.settings.defaultControls.responseFormat.strict) { checked ->
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(strict = checked)
                            )
                        )
                    }
                }
                SimpleTextField(
                    label = "JSON schema",
                    value = uiState.settings.defaultControls.responseFormat.schemaJson,
                    minLines = 6,
                    maxLines = 12,
                ) { value ->
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(schemaJson = value)
                            )
                        )
                    }
                }
            }
        }

        SectionCard(title = "Capabilities") {
            CapabilitySwitch("Supports image input", uiState.settings.provider.supportsImageInput, viewModel) {
                it.copy(supportsImageInput = it.supportsImageInput.not())
            }
            CapabilitySwitch("Supports web/search", uiState.settings.provider.supportsWebSearch, viewModel) {
                it.copy(supportsWebSearch = it.supportsWebSearch.not())
            }
            CapabilitySwitch("Supports streaming", uiState.settings.provider.supportsStreaming, viewModel) {
                it.copy(supportsStreaming = it.supportsStreaming.not())
            }
            CapabilitySwitch("Supports verbosity", uiState.settings.provider.supportsVerbosity, viewModel) {
                it.copy(supportsVerbosity = it.supportsVerbosity.not())
            }
            CapabilitySwitch("Supports structured outputs", uiState.settings.provider.supportsStructuredOutputs, viewModel) {
                it.copy(supportsStructuredOutputs = it.supportsStructuredOutputs.not())
            }
        }

        SectionCard(title = "Compatibility mapping") {
            SimpleTextField("Reasoning path", uiState.settings.provider.reasoningMapping.path) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            reasoningMapping = settings.provider.reasoningMapping.copy(path = value)
                        )
                    )
                }
            }
            SimpleTextField("Verbosity path", uiState.settings.provider.verbosityMapping.path) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            verbosityMapping = settings.provider.verbosityMapping.copy(path = value)
                        )
                    )
                }
            }
            SimpleTextField("Web/search path", uiState.settings.provider.webSearchMapping.path) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            webSearchMapping = settings.provider.webSearchMapping.copy(path = value)
                        )
                    )
                }
            }
            SimpleTextField("Web enabled JSON", uiState.settings.provider.webSearchMapping.enabledJson) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            webSearchMapping = settings.provider.webSearchMapping.copy(enabledJson = value)
                        )
                    )
                }
            }
            SimpleTextField("Web disabled JSON", uiState.settings.provider.webSearchMapping.disabledJson) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            webSearchMapping = settings.provider.webSearchMapping.copy(disabledJson = value)
                        )
                    )
                }
            }
        }

        SectionCard(title = "Advanced") {
            SimpleTextField(
                label = "Extra headers, one per line",
                value = uiState.settings.provider.extraHeaders,
                minLines = 2,
                maxLines = 6,
            ) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(extraHeaders = value))
                }
            }
            SimpleTextField(
                label = "Extra body JSON object",
                value = uiState.settings.provider.extraBodyJson,
                minLines = 4,
                maxLines = 10,
            ) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(extraBodyJson = value))
                }
            }
        }

        SectionCard(title = "Notes") {
            Text(
                text = "API keys are stored with Android Keystore-backed encryption. Responses API is the preferred path for reasoning, verbosity, previous_response_id chaining, web search, response storage, and structured outputs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = "For generic compatible endpoints, leave unsupported mappings blank and override edge cases with Extra body JSON.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (importDialogVisible) {
        ImportConfigDialog(
            onDismiss = { importDialogVisible = false },
            onImport = {
                viewModel.importCodexConfig(it)
                importDialogVisible = false
            },
        )
    }
}

@Composable
private fun SettingsOverview(uiState: RelayChatUiState) {
    RelayGlassCard(
        modifier = Modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.primary,
    ) {
        RelaySectionEyebrow(text = "Configuration")
        Text(
            text = uiState.settings.provider.displayName,
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = uiState.settings.provider.model,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RelayInfoPill(
                text = uiState.resolvedEndpoint,
                icon = Icons.Outlined.Link,
                highlight = MaterialTheme.colorScheme.primary,
            )
            RelayInfoPill(
                text = if (uiState.apiKey.isBlank()) "API key missing" else "API key stored",
                icon = Icons.Outlined.Lock,
                highlight = if (uiState.apiKey.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            RelayInfoPill(
                text = "Reasoning ${uiState.settings.defaultControls.reasoningEffort.name.lowercase()}",
                icon = Icons.Outlined.AutoAwesome,
                highlight = MaterialTheme.colorScheme.secondary,
            )
            RelayInfoPill(
                text = uiState.settings.provider.apiStyle.name.lowercase(),
                icon = Icons.Outlined.Api,
                highlight = MaterialTheme.colorScheme.tertiary,
            )
            RelayInfoPill(
                text = "Theme ${uiState.settings.themeMode.label}",
                icon = Icons.Outlined.AutoAwesome,
                highlight = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Chat, history, and settings now share the same live configuration summary so it is obvious which provider and controls are active before you send.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportSummarySection(summary: ImportedConfigSummary) {
    SectionCard(title = "Last imported config") {
        KeyValueRow("Provider", summary.providerName)
        KeyValueRow("Model", summary.model)
        KeyValueRow("Endpoint", summary.endpoint)
        KeyValueRow("Reasoning", summary.reasoning)
        KeyValueRow("Verbosity", summary.verbosity)
        KeyValueRow("Web/search", summary.webSearch)
        KeyValueRow("Provider storage", summary.responseStorage)
        KeyValueRow("API key env", summary.apiKeyEnvName)
    }
}

@Composable
private fun SectionCard(
    title: String,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
    content: @Composable ColumnScope.() -> Unit,
) {
    RelayGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        accent = accent,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeModeSelector(
    currentMode: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Color mode",
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { onSelected(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> EnumSelector(
    title: String,
    currentLabel: String,
    options: List<T>,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(currentLabel) },
            )
        }
        if (expanded) {
            AlertDialog(
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(28.dp),
                title = { Text(title) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { option ->
                            TextButton(
                                onClick = {
                                    expanded = false
                                    onSelected(option)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(labelFor(option))
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { expanded = false }) { Text("Close") }
                },
            )
        }
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.surface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun SimpleTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    maxLines: Int = minLines,
    onValueChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(20.dp),
        colors = relayOutlinedTextFieldColors(),
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    onValueChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(20.dp),
        colors = relayOutlinedTextFieldColors(),
    )
}

@Composable
private fun CapabilitySwitch(
    label: String,
    checked: Boolean,
    viewModel: RelayChatViewModel,
    transform: (com.example.relaychat.core.model.ProviderProfile) -> com.example.relaychat.core.model.ProviderProfile,
) {
    SwitchRow(label = label, checked = checked) {
        viewModel.updateSettings { settings ->
            settings.copy(provider = transform(settings.provider))
        }
    }
}

@Composable
private fun ImportConfigDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var rawConfig by rememberSaveable {
        mutableStateOf(
            """
            approval_policy = "never"
            sandbox_mode = "danger-full-access"
            model_provider = "intelalloc"
            model = "gpt-5.4"
            model_reasoning_effort = "xhigh"
            model_reasoning_summary = "detailed"
            network_access = "enabled"
            disable_response_storage = true
            model_verbosity = "high"

            [model_providers.intelalloc]
            name = "intelalloc"
            base_url = "https://www.intelalloc.com"
            wire_api = "responses"
            requires_openai_auth = true
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(28.dp),
        title = { Text("Import Codex config") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste a Codex CLI style config. The importer recognizes fields like model_provider, model, network_access, and [model_providers.<name>].",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = rawConfig,
                    onValueChange = { rawConfig = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                    maxLines = 18,
                    shape = RoundedCornerShape(20.dp),
                    colors = relayOutlinedTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(rawConfig) }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val AppThemeMode.label: String
    get() = when (this) {
        AppThemeMode.SYSTEM -> "Follow system"
        AppThemeMode.LIGHT -> "Light"
        AppThemeMode.DARK -> "Dark"
    }
