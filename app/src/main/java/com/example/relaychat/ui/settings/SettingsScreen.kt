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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.relaychat.R
import com.example.relaychat.app.ImportedConfigSummary
import com.example.relaychat.app.RelayChatUiState
import com.example.relaychat.app.RelayChatViewModel
import com.example.relaychat.core.model.AppLocale
import com.example.relaychat.core.model.AppThemeMode
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.RequestTuningPreset
import com.example.relaychat.core.model.ResponseFormatMode
import com.example.relaychat.core.model.ToolChoiceMode
import com.example.relaychat.ui.components.RelayGlassCard
import com.example.relaychat.ui.components.RelayInfoPill
import com.example.relaychat.ui.components.RelaySectionEyebrow
import com.example.relaychat.ui.components.relayOutlinedTextFieldColors
import com.example.relaychat.ui.strings.detailFor
import com.example.relaychat.ui.strings.detailRes
import com.example.relaychat.ui.strings.labelRes
import com.example.relaychat.ui.strings.stringFor
import com.example.relaychat.ui.strings.titleRes

@Composable
fun SettingsScreen(
    uiState: RelayChatUiState,
    viewModel: RelayChatViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var importDialogVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsOverview(uiState = uiState)

        SectionCard(
            title = stringResource(R.string.settings_appearance_title),
            accent = MaterialTheme.colorScheme.primary,
        ) {
            LanguageSelector(
                currentLocale = uiState.settings.appLocale,
                onSelected = viewModel::updateAppLocale,
            )
            Text(
                text = stringResource(R.string.settings_language_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemeModeSelector(
                currentMode = uiState.settings.themeMode,
                onSelected = { mode ->
                    viewModel.updateSettings { settings ->
                        settings.copy(themeMode = mode)
                    }
                },
            )
            Text(
                text = stringResource(R.string.settings_theme_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_quick_setup_title)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { importDialogVisible = true },
                    label = { Text(stringResource(R.string.settings_import_codex_config)) },
                )
                AssistChip(
                    onClick = {
                        viewModel.applyProviderPreset(
                            ProviderPreset.INTELALLOC_CODEX,
                            status = context.getString(
                                R.string.status_provider_preset_applied_with_key,
                                context.stringFor(ProviderPreset.INTELALLOC_CODEX),
                            ),
                        )
                    },
                    label = { Text(stringResource(R.string.settings_use_intelalloc_preset)) },
                )
            }
            AssistChip(
                onClick = {
                    viewModel.applyProviderPreset(ProviderPreset.OPENAI_RESPONSES)
                },
                label = { Text(stringResource(R.string.settings_use_openai_responses_preset)) },
            )
            KeyValueRow(label = stringResource(R.string.settings_endpoint_short_label), value = uiState.resolvedEndpoint)
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

        SectionCard(title = stringResource(R.string.settings_preset_title)) {
            EnumSelector(
                title = stringResource(R.string.settings_provider_preset_label),
                currentLabel = context.stringFor(ProviderPreset.fromId(uiState.settings.provider.presetId)),
                options = ProviderPreset.entries,
                labelFor = { preset -> context.stringFor(preset) },
                onSelected = { preset ->
                    viewModel.applyProviderPreset(preset)
                },
            )
            Text(
                text = context.detailFor(ProviderPreset.fromId(uiState.settings.provider.presetId)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_quality_presets_title)) {
            RequestTuningPreset.entries.forEach { preset ->
                TextButton(onClick = { viewModel.applyDefaultTuningPreset(preset) }) {
                    Text(stringResource(preset.titleRes()))
                }
                Text(
                    text = stringResource(preset.detailRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_endpoint_title)) {
            EnumSelector(
                title = stringResource(R.string.settings_api_style_label),
                currentLabel = context.stringFor(uiState.settings.provider.apiStyle),
                options = com.example.relaychat.core.model.ProviderApiStyle.entries,
                labelFor = { context.stringFor(it) },
                onSelected = { style ->
                    viewModel.updateSettings {
                        it.copy(provider = it.provider.copy(apiStyle = style))
                    }
                },
            )
            SimpleTextField(stringResource(R.string.settings_display_name_label), uiState.settings.provider.displayName) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(displayName = it))
                }
            }
            SimpleTextField(stringResource(R.string.settings_base_url_label), uiState.settings.provider.baseUrl) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(baseUrl = it))
                }
            }
            SimpleTextField(stringResource(R.string.settings_path_label), uiState.settings.provider.path) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(path = it))
                }
            }
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = viewModel::writeApiKey,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                shape = RoundedCornerShape(20.dp),
                colors = relayOutlinedTextFieldColors(),
            )
            SimpleTextField(stringResource(R.string.settings_model_label), uiState.settings.provider.model) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(model = it))
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_instructions_title)) {
            SimpleTextField(
                label = stringResource(R.string.settings_instructions_label),
                value = uiState.settings.provider.instructionsPrompt,
                minLines = 3,
                maxLines = 8,
            ) {
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(instructionsPrompt = it))
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_default_controls_title)) {
            EnumSelector(
                title = stringResource(R.string.settings_reasoning_label),
                currentLabel = context.stringFor(uiState.settings.defaultControls.reasoningEffort),
                options = com.example.relaychat.core.model.ReasoningEffort.entries,
                labelFor = { context.stringFor(it) },
                onSelected = { value ->
                    viewModel.updateSettings { settings ->
                        settings.copy(defaultControls = settings.defaultControls.copy(reasoningEffort = value))
                    }
                },
            )
            if (uiState.settings.provider.supportsVerbosity) {
                EnumSelector(
                    title = stringResource(R.string.settings_verbosity_label),
                    currentLabel = context.stringFor(uiState.settings.defaultControls.verbosity),
                    options = com.example.relaychat.core.model.VerbosityLevel.entries,
                    labelFor = { context.stringFor(it) },
                    onSelected = { value ->
                        viewModel.updateSettings { settings ->
                            settings.copy(defaultControls = settings.defaultControls.copy(verbosity = value))
                        }
                    },
                )
            }
            SwitchRow(
                label = stringResource(R.string.settings_web_search_by_default),
                checked = uiState.settings.defaultControls.webSearchEnabled,
                enabled = uiState.settings.provider.supportsWebSearch,
            ) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(webSearchEnabled = checked))
                }
            }
            SwitchRow(stringResource(R.string.settings_response_storage), uiState.settings.defaultControls.responseStorageEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(responseStorageEnabled = checked))
                }
            }
            EnumSelector(
                title = stringResource(R.string.settings_tool_choice_label),
                currentLabel = context.stringFor(uiState.settings.defaultControls.toolChoice),
                options = ToolChoiceMode.entries,
                labelFor = { context.stringFor(it) },
                onSelected = { value ->
                    viewModel.updateSettings { settings ->
                        settings.copy(defaultControls = settings.defaultControls.copy(toolChoice = value))
                    }
                },
            )
        }

        SectionCard(title = stringResource(R.string.settings_sampling_title)) {
            SwitchRow(stringResource(R.string.settings_enable_temperature), uiState.settings.defaultControls.temperatureEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(temperatureEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.temperatureEnabled) {
                NumberField(
                    label = stringResource(R.string.settings_temperature_label),
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
            SwitchRow(stringResource(R.string.settings_enable_top_p), uiState.settings.defaultControls.topPEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(topPEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.topPEnabled) {
                NumberField(
                    label = stringResource(R.string.settings_top_p_label),
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
            SwitchRow(stringResource(R.string.settings_enable_max_output_tokens), uiState.settings.defaultControls.maxOutputTokensEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(maxOutputTokensEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.maxOutputTokensEnabled) {
                NumberField(
                    label = stringResource(R.string.settings_max_output_tokens_label),
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
            SwitchRow(stringResource(R.string.settings_enable_seed), uiState.settings.defaultControls.seedEnabled) { checked ->
                viewModel.updateSettings { settings ->
                    settings.copy(defaultControls = settings.defaultControls.copy(seedEnabled = checked))
                }
            }
            AnimatedVisibility(visible = uiState.settings.defaultControls.seedEnabled) {
                NumberField(
                    label = stringResource(R.string.settings_seed_label),
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

        SectionCard(title = stringResource(R.string.settings_structured_outputs_title)) {
            EnumSelector(
                title = stringResource(R.string.settings_response_format_label),
                currentLabel = context.stringFor(uiState.settings.defaultControls.responseFormat.mode),
                options = ResponseFormatMode.entries,
                labelFor = { context.stringFor(it) },
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
                SimpleTextField(stringResource(R.string.settings_schema_name_label), uiState.settings.defaultControls.responseFormat.schemaName) {
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(schemaName = it)
                            )
                        )
                    }
                }
                SimpleTextField(stringResource(R.string.settings_schema_description_label), uiState.settings.defaultControls.responseFormat.schemaDescription) {
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(schemaDescription = it)
                            )
                        )
                    }
                }
                SwitchRow(stringResource(R.string.settings_schema_strict), uiState.settings.defaultControls.responseFormat.strict) { checked ->
                    viewModel.updateSettings { settings ->
                        settings.copy(
                            defaultControls = settings.defaultControls.copy(
                                responseFormat = settings.defaultControls.responseFormat.copy(strict = checked)
                            )
                        )
                    }
                }
                SimpleTextField(
                    label = stringResource(R.string.settings_json_schema_label),
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

        SectionCard(title = stringResource(R.string.settings_capabilities_title)) {
            CapabilitySwitch(stringResource(R.string.settings_supports_image_input), uiState.settings.provider.supportsImageInput, viewModel) {
                it.copy(supportsImageInput = it.supportsImageInput.not())
            }
            CapabilitySwitch(stringResource(R.string.settings_supports_web_search), uiState.settings.provider.supportsWebSearch, viewModel) {
                it.copy(supportsWebSearch = it.supportsWebSearch.not())
            }
            CapabilitySwitch(stringResource(R.string.settings_supports_streaming), uiState.settings.provider.supportsStreaming, viewModel) {
                it.copy(supportsStreaming = it.supportsStreaming.not())
            }
            CapabilitySwitch(stringResource(R.string.settings_supports_verbosity), uiState.settings.provider.supportsVerbosity, viewModel) {
                it.copy(supportsVerbosity = it.supportsVerbosity.not())
            }
            CapabilitySwitch(stringResource(R.string.settings_supports_structured_outputs), uiState.settings.provider.supportsStructuredOutputs, viewModel) {
                it.copy(supportsStructuredOutputs = it.supportsStructuredOutputs.not())
            }
        }

        SectionCard(title = stringResource(R.string.settings_compat_mapping_title)) {
            SimpleTextField(stringResource(R.string.settings_reasoning_path_label), uiState.settings.provider.reasoningMapping.path) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            reasoningMapping = settings.provider.reasoningMapping.copy(path = value)
                        )
                    )
                }
            }
            SimpleTextField(stringResource(R.string.settings_verbosity_path_label), uiState.settings.provider.verbosityMapping.path) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            verbosityMapping = settings.provider.verbosityMapping.copy(path = value)
                        )
                    )
                }
            }
            SimpleTextField(stringResource(R.string.settings_web_search_path_label), uiState.settings.provider.webSearchMapping.path) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            webSearchMapping = settings.provider.webSearchMapping.copy(path = value)
                        )
                    )
                }
            }
            SimpleTextField(stringResource(R.string.settings_web_enabled_json_label), uiState.settings.provider.webSearchMapping.enabledJson) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            webSearchMapping = settings.provider.webSearchMapping.copy(enabledJson = value)
                        )
                    )
                }
            }
            SimpleTextField(stringResource(R.string.settings_web_disabled_json_label), uiState.settings.provider.webSearchMapping.disabledJson) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(
                        provider = settings.provider.copy(
                            webSearchMapping = settings.provider.webSearchMapping.copy(disabledJson = value)
                        )
                    )
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_advanced_title)) {
            SimpleTextField(
                label = stringResource(R.string.settings_extra_headers_label),
                value = uiState.settings.provider.extraHeaders,
                minLines = 2,
                maxLines = 6,
            ) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(extraHeaders = value))
                }
            }
            SimpleTextField(
                label = stringResource(R.string.settings_extra_body_json_label),
                value = uiState.settings.provider.extraBodyJson,
                minLines = 4,
                maxLines = 10,
            ) { value ->
                viewModel.updateSettings { settings ->
                    settings.copy(provider = settings.provider.copy(extraBodyJson = value))
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_notes_title)) {
            Text(
                text = stringResource(R.string.settings_notes_body_one),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = stringResource(R.string.settings_notes_body_two),
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
        val context = LocalContext.current

        RelaySectionEyebrow(text = stringResource(R.string.settings_configuration_eyebrow))
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
                text = if (uiState.apiKey.isBlank()) {
                    stringResource(R.string.settings_key_missing)
                } else {
                    stringResource(R.string.settings_key_stored)
                },
                icon = Icons.Outlined.Lock,
                highlight = if (uiState.apiKey.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )
            RelayInfoPill(
                text = stringResource(
                    R.string.settings_summary_reasoning,
                    context.stringFor(uiState.settings.defaultControls.reasoningEffort),
                ),
                icon = Icons.Outlined.AutoAwesome,
                highlight = MaterialTheme.colorScheme.secondary,
            )
            RelayInfoPill(
                text = context.stringFor(uiState.settings.provider.apiStyle),
                icon = Icons.Outlined.Api,
                highlight = MaterialTheme.colorScheme.tertiary,
            )
            RelayInfoPill(
                text = stringResource(
                    R.string.settings_summary_theme,
                    stringResource(uiState.settings.themeMode.labelRes()),
                ),
                icon = Icons.Outlined.AutoAwesome,
                highlight = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.settings_configuration_summary_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportSummarySection(summary: ImportedConfigSummary) {
    SectionCard(title = stringResource(R.string.settings_last_imported_title)) {
        KeyValueRow(stringResource(R.string.settings_provider_label), summary.providerName)
        KeyValueRow(stringResource(R.string.settings_model_short_label), summary.model)
        KeyValueRow(stringResource(R.string.settings_endpoint_short_label), summary.endpoint)
        KeyValueRow(stringResource(R.string.settings_reasoning_short_label), summary.reasoning)
        KeyValueRow(stringResource(R.string.settings_verbosity_short_label), summary.verbosity)
        KeyValueRow(stringResource(R.string.settings_web_search_short_label), summary.webSearch)
        KeyValueRow(stringResource(R.string.settings_provider_storage_short_label), summary.responseStorage)
        KeyValueRow(stringResource(R.string.settings_api_key_env_label), summary.apiKeyEnvName)
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
            text = stringResource(R.string.settings_theme_title),
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
                    label = { Text(stringResource(mode.labelRes())) },
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
private fun LanguageSelector(
    currentLocale: AppLocale,
    onSelected: (AppLocale) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_language_title),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLocale.entries.forEach { locale ->
                FilterChip(
                    selected = currentLocale == locale,
                    onClick = { onSelected(locale) },
                    label = { Text(stringResource(locale.labelRes())) },
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
                    TextButton(onClick = { expanded = false }) { Text(stringResource(R.string.action_close)) }
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
            base_url = "https://backend.intelalloc.com"
            wire_api = "responses"
            requires_openai_auth = true
            """.trimIndent()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.settings_import_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_import_dialog_body),
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
            TextButton(onClick = { onImport(rawConfig) }) { Text(stringResource(R.string.action_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}


