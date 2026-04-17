package com.example.relaychat.ui.strings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.example.relaychat.R
import com.example.relaychat.app.PendingReplyTextSet
import com.example.relaychat.core.model.AppLocale
import com.example.relaychat.core.model.AppThemeMode
import com.example.relaychat.core.model.ProviderApiStyle
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.ReasoningEffort
import com.example.relaychat.core.model.RequestTuningPreset
import com.example.relaychat.core.model.ResponseFormatMode
import com.example.relaychat.core.model.ToolChoiceMode
import com.example.relaychat.core.model.VerbosityLevel
import com.example.relaychat.core.model.presentThreadTitle
import com.example.relaychat.ui.history.HistoryTextSet

@StringRes
fun AppLocale.labelRes(): Int = when (this) {
    AppLocale.SYSTEM -> R.string.app_locale_system
    AppLocale.ENGLISH -> R.string.app_locale_english
    AppLocale.SIMPLIFIED_CHINESE -> R.string.app_locale_simplified_chinese
}

@StringRes
fun AppThemeMode.labelRes(): Int = when (this) {
    AppThemeMode.SYSTEM -> R.string.theme_mode_system
    AppThemeMode.LIGHT -> R.string.theme_mode_light
    AppThemeMode.DARK -> R.string.theme_mode_dark
}

@StringRes
fun ProviderApiStyle.labelRes(): Int = when (this) {
    ProviderApiStyle.RESPONSES -> R.string.provider_api_style_responses
    ProviderApiStyle.CHAT_COMPLETIONS -> R.string.provider_api_style_chat_completions
}

@StringRes
fun ReasoningEffort.labelRes(): Int = when (this) {
    ReasoningEffort.NONE -> R.string.reasoning_none
    ReasoningEffort.MINIMAL -> R.string.reasoning_minimal
    ReasoningEffort.LOW -> R.string.reasoning_low
    ReasoningEffort.MEDIUM -> R.string.reasoning_medium
    ReasoningEffort.HIGH -> R.string.reasoning_high
    ReasoningEffort.XHIGH -> R.string.reasoning_xhigh
}

@StringRes
fun VerbosityLevel.labelRes(): Int = when (this) {
    VerbosityLevel.LOW -> R.string.verbosity_low
    VerbosityLevel.MEDIUM -> R.string.verbosity_medium
    VerbosityLevel.HIGH -> R.string.verbosity_high
}

@StringRes
fun ToolChoiceMode.labelRes(): Int = when (this) {
    ToolChoiceMode.AUTO -> R.string.tool_choice_auto
    ToolChoiceMode.NONE -> R.string.tool_choice_none
    ToolChoiceMode.REQUIRED -> R.string.tool_choice_required
}

@StringRes
fun ResponseFormatMode.labelRes(): Int = when (this) {
    ResponseFormatMode.TEXT -> R.string.response_format_text
    ResponseFormatMode.JSON_OBJECT -> R.string.response_format_json_object
    ResponseFormatMode.JSON_SCHEMA -> R.string.response_format_json_schema
}

@StringRes
fun ProviderPreset.titleRes(): Int = when (this) {
    ProviderPreset.INTELALLOC_CODEX -> R.string.provider_preset_intelalloc_title
    ProviderPreset.OPENAI_RESPONSES -> R.string.provider_preset_openai_responses_title
    ProviderPreset.OPENAI_CHAT_COMPLETIONS -> R.string.provider_preset_openai_chat_title
    ProviderPreset.OPENROUTER_COMPATIBLE -> R.string.provider_preset_openrouter_title
    ProviderPreset.LM_STUDIO_COMPATIBLE -> R.string.provider_preset_lm_studio_title
    ProviderPreset.CUSTOM -> R.string.provider_preset_custom_title
}

@StringRes
fun ProviderPreset.detailRes(): Int = when (this) {
    ProviderPreset.INTELALLOC_CODEX -> R.string.provider_preset_intelalloc_detail
    ProviderPreset.OPENAI_RESPONSES -> R.string.provider_preset_openai_responses_detail
    ProviderPreset.OPENAI_CHAT_COMPLETIONS -> R.string.provider_preset_openai_chat_detail
    ProviderPreset.OPENROUTER_COMPATIBLE -> R.string.provider_preset_openrouter_detail
    ProviderPreset.LM_STUDIO_COMPATIBLE -> R.string.provider_preset_lm_studio_detail
    ProviderPreset.CUSTOM -> R.string.provider_preset_custom_detail
}

@StringRes
fun RequestTuningPreset.titleRes(): Int = when (this) {
    RequestTuningPreset.PRECISE -> R.string.tuning_preset_precise_title
    RequestTuningPreset.BALANCED -> R.string.tuning_preset_balanced_title
    RequestTuningPreset.DEEP -> R.string.tuning_preset_deep_title
}

@StringRes
fun RequestTuningPreset.detailRes(): Int = when (this) {
    RequestTuningPreset.PRECISE -> R.string.tuning_preset_precise_detail
    RequestTuningPreset.BALANCED -> R.string.tuning_preset_balanced_detail
    RequestTuningPreset.DEEP -> R.string.tuning_preset_deep_detail
}

fun Context.stringFor(value: AppLocale): String = getString(value.labelRes())

fun Context.stringFor(value: AppThemeMode): String = getString(value.labelRes())

fun Context.stringFor(value: ProviderApiStyle): String = getString(value.labelRes())

fun Context.stringFor(value: ReasoningEffort): String = getString(value.labelRes())

fun Context.stringFor(value: VerbosityLevel): String = getString(value.labelRes())

fun Context.stringFor(value: ToolChoiceMode): String = getString(value.labelRes())

fun Context.stringFor(value: ResponseFormatMode): String = getString(value.labelRes())

fun Context.stringFor(value: ProviderPreset): String = getString(value.titleRes())

fun Context.detailFor(value: ProviderPreset): String = getString(value.detailRes())

fun Context.stringFor(value: RequestTuningPreset): String = getString(value.titleRes())

fun Context.detailFor(value: RequestTuningPreset): String = getString(value.detailRes())

@Composable
internal fun localizedThreadTitle(title: String): String {
    val defaultTitle = stringResource(R.string.thread_new_chat)
    return remember(title, defaultTitle) {
        presentThreadTitle(title = title, defaultTitle = defaultTitle)
    }
}

internal fun Context.localizedThreadTitle(title: String): String =
    presentThreadTitle(title = title, defaultTitle = getString(R.string.thread_new_chat))

@Composable
internal fun rememberPendingReplyTextSet(): PendingReplyTextSet {
    val thinkingTitle = stringResource(R.string.pending_title_thinking)
    val searchingTitle = stringResource(R.string.pending_title_searching)
    val streamingTitle = stringResource(R.string.pending_title_streaming)
    val thinkingSubtitle = stringResource(R.string.pending_subtitle_thinking)
    val postSearchThinkingSubtitle = stringResource(R.string.pending_subtitle_post_search_thinking)
    val searchingSubtitle = stringResource(R.string.pending_subtitle_searching)
    val slowSearchingSubtitle = stringResource(R.string.pending_subtitle_searching_slow)
    val blankStreamingSubtitle = stringResource(R.string.pending_subtitle_streaming_blank)
    val streamingSubtitle = stringResource(R.string.pending_subtitle_streaming)
    val thinkingStateLabel = stringResource(R.string.pending_state_thinking)
    val searchingStateLabel = stringResource(R.string.pending_state_searching)
    val streamingStateLabel = stringResource(R.string.pending_state_streaming)
    val thinkingTimelineLabel = stringResource(R.string.pending_timeline_thinking)
    val searchingTimelineLabel = stringResource(R.string.pending_timeline_searching)
    val streamingTimelineLabel = stringResource(R.string.pending_timeline_answer)
    val thinkingDetail = stringResource(R.string.pending_detail_thinking)
    val reasoningDetail = stringResource(R.string.pending_detail_reasoning)
    val searchingDetail = stringResource(R.string.pending_detail_searching)
    val draftingDetail = stringResource(R.string.pending_detail_drafting)

    return remember(
        thinkingTitle,
        searchingTitle,
        streamingTitle,
        thinkingSubtitle,
        postSearchThinkingSubtitle,
        searchingSubtitle,
        slowSearchingSubtitle,
        blankStreamingSubtitle,
        streamingSubtitle,
        thinkingStateLabel,
        searchingStateLabel,
        streamingStateLabel,
        thinkingTimelineLabel,
        searchingTimelineLabel,
        streamingTimelineLabel,
        thinkingDetail,
        reasoningDetail,
        searchingDetail,
        draftingDetail,
    ) {
        PendingReplyTextSet(
            thinkingTitle = thinkingTitle,
            searchingTitle = searchingTitle,
            streamingTitle = streamingTitle,
            thinkingSubtitle = thinkingSubtitle,
            postSearchThinkingSubtitle = postSearchThinkingSubtitle,
            searchingSubtitle = searchingSubtitle,
            slowSearchingSubtitle = slowSearchingSubtitle,
            blankStreamingSubtitle = blankStreamingSubtitle,
            streamingSubtitle = streamingSubtitle,
            thinkingStateLabel = thinkingStateLabel,
            searchingStateLabel = searchingStateLabel,
            streamingStateLabel = streamingStateLabel,
            thinkingTimelineLabel = thinkingTimelineLabel,
            searchingTimelineLabel = searchingTimelineLabel,
            streamingTimelineLabel = streamingTimelineLabel,
            thinkingDetail = thinkingDetail,
            reasoningDetail = reasoningDetail,
            searchingDetail = searchingDetail,
            draftingDetail = draftingDetail,
        )
    }
}

internal fun Context.pendingReplyTextSet(): PendingReplyTextSet = PendingReplyTextSet(
    thinkingTitle = getString(R.string.pending_title_thinking),
    searchingTitle = getString(R.string.pending_title_searching),
    streamingTitle = getString(R.string.pending_title_streaming),
    thinkingSubtitle = getString(R.string.pending_subtitle_thinking),
    postSearchThinkingSubtitle = getString(R.string.pending_subtitle_post_search_thinking),
    searchingSubtitle = getString(R.string.pending_subtitle_searching),
    slowSearchingSubtitle = getString(R.string.pending_subtitle_searching_slow),
    blankStreamingSubtitle = getString(R.string.pending_subtitle_streaming_blank),
    streamingSubtitle = getString(R.string.pending_subtitle_streaming),
    thinkingStateLabel = getString(R.string.pending_state_thinking),
    searchingStateLabel = getString(R.string.pending_state_searching),
    streamingStateLabel = getString(R.string.pending_state_streaming),
    thinkingTimelineLabel = getString(R.string.pending_timeline_thinking),
    searchingTimelineLabel = getString(R.string.pending_timeline_searching),
    streamingTimelineLabel = getString(R.string.pending_timeline_answer),
    thinkingDetail = getString(R.string.pending_detail_thinking),
    reasoningDetail = getString(R.string.pending_detail_reasoning),
    searchingDetail = getString(R.string.pending_detail_searching),
    draftingDetail = getString(R.string.pending_detail_drafting),
)

@Composable
internal fun rememberHistoryTextSet(): HistoryTextSet {
    val matchesTitle = stringResource(R.string.history_section_matches)
    val currentTitle = stringResource(R.string.history_section_current)
    val todayTitle = stringResource(R.string.history_filter_today)
    val thisWeekTitle = stringResource(R.string.history_filter_this_week)
    val earlierTitle = stringResource(R.string.history_filter_earlier)
    val emptyThreadPreview = stringResource(R.string.history_empty_thread_preview)

    return remember(
        matchesTitle,
        currentTitle,
        todayTitle,
        thisWeekTitle,
        earlierTitle,
        emptyThreadPreview,
    ) {
        HistoryTextSet(
            matchesTitle = matchesTitle,
            currentTitle = currentTitle,
            todayTitle = todayTitle,
            thisWeekTitle = thisWeekTitle,
            earlierTitle = earlierTitle,
            emptyThreadPreview = emptyThreadPreview,
        )
    }
}

internal fun Context.historyTextSet(): HistoryTextSet = HistoryTextSet(
    matchesTitle = getString(R.string.history_section_matches),
    currentTitle = getString(R.string.history_section_current),
    todayTitle = getString(R.string.history_filter_today),
    thisWeekTitle = getString(R.string.history_filter_this_week),
    earlierTitle = getString(R.string.history_filter_earlier),
    emptyThreadPreview = getString(R.string.history_empty_thread_preview),
)
