package com.example.relaychat.ui.history

import com.example.relaychat.core.model.ChatThread
import java.time.Instant
import java.time.ZoneId

internal enum class HistoryQuickFilter {
    ALL,
    TODAY,
    THIS_WEEK,
    EARLIER,
}

internal data class HistoryThreadItem(
    val thread: ChatThread,
    val preview: String,
    val matchCount: Int,
    val isSelected: Boolean,
)

internal data class HistorySection(
    val title: String,
    val items: List<HistoryThreadItem>,
)

internal data class HistoryTextSet(
    val matchesTitle: String = "Matches",
    val currentTitle: String = "Current",
    val todayTitle: String = "Today",
    val thisWeekTitle: String = "This Week",
    val earlierTitle: String = "Earlier",
    val emptyThreadPreview: String = "Empty thread",
)

internal fun buildHistorySections(
    threads: List<ChatThread>,
    selectedThreadId: String?,
    query: String,
    filter: HistoryQuickFilter,
    textSet: HistoryTextSet = HistoryTextSet(),
    nowMs: Long = System.currentTimeMillis(),
): List<HistorySection> {
    val normalizedQuery = query.trim().lowercase()
    val preparedItems = threads
        .sortedByDescending { it.updatedAt }
        .mapNotNull { thread ->
            if (!matchesQuickFilter(thread = thread, filter = filter, nowMs = nowMs)) {
                return@mapNotNull null
            }

            createHistoryThreadItem(
                thread = thread,
                normalizedQuery = normalizedQuery,
                isSelected = thread.id == selectedThreadId,
                textSet = textSet,
            )
        }

    if (normalizedQuery.isNotBlank()) {
        return preparedItems
            .sortedWith(compareByDescending<HistoryThreadItem> { it.isSelected }.thenByDescending { it.thread.updatedAt })
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(HistorySection(title = textSet.matchesTitle, items = it)) }
            .orEmpty()
    }

    val sections = mutableListOf<HistorySection>()
    val currentItem = preparedItems.firstOrNull { it.isSelected }
    val remainingItems = preparedItems.filterNot { it.isSelected }

    if (currentItem != null) {
        sections += HistorySection(
            title = textSet.currentTitle,
            items = listOf(currentItem),
        )
    }

    RecencyBucket.entries.forEach { bucket ->
        val bucketItems = remainingItems.filter { item ->
            recencyBucket(item.thread.updatedAt, nowMs) == bucket
        }
        if (bucketItems.isNotEmpty()) {
            sections += HistorySection(title = bucket.title(textSet), items = bucketItems)
        }
    }

    return sections
}

private fun createHistoryThreadItem(
    thread: ChatThread,
    normalizedQuery: String,
    isSelected: Boolean,
    textSet: HistoryTextSet,
): HistoryThreadItem? {
    if (normalizedQuery.isBlank()) {
        return HistoryThreadItem(
            thread = thread,
            preview = defaultPreview(thread, textSet),
            matchCount = 0,
            isSelected = isSelected,
        )
    }

    val titleMatch = thread.title.lowercase().contains(normalizedQuery)
    val matchingMessages = thread.messages.filter { message ->
        message.text.lowercase().contains(normalizedQuery)
    }

    if (!titleMatch && matchingMessages.isEmpty()) {
        return null
    }

    val preview = matchingMessages.lastOrNull()?.text?.let { text ->
        buildSnippet(text = text, normalizedQuery = normalizedQuery, textSet = textSet)
    } ?: defaultPreview(thread, textSet)

    return HistoryThreadItem(
        thread = thread,
        preview = preview,
        matchCount = matchingMessages.size + if (titleMatch) 1 else 0,
        isSelected = isSelected,
    )
}

private fun defaultPreview(
    thread: ChatThread,
    textSet: HistoryTextSet,
): String = thread.messages.lastOrNull()?.text?.trim()?.takeIf { it.isNotEmpty() }
    ?: textSet.emptyThreadPreview

private fun buildSnippet(
    text: String,
    normalizedQuery: String,
    textSet: HistoryTextSet,
    maxLength: Int = 92,
): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) {
        return textSet.emptyThreadPreview
    }

    val normalized = trimmed.lowercase()
    val matchIndex = normalized.indexOf(normalizedQuery)
    if (matchIndex < 0 || trimmed.length <= maxLength) {
        return trimmed.take(maxLength)
    }

    val prefix = maxOf(0, matchIndex - 22)
    val suffix = minOf(trimmed.length, prefix + maxLength)
    val snippet = trimmed.substring(prefix, suffix).trim()
    val leading = if (prefix > 0) "..." else ""
    val trailing = if (suffix < trimmed.length) "..." else ""
    return leading + snippet + trailing
}

private fun matchesQuickFilter(
    thread: ChatThread,
    filter: HistoryQuickFilter,
    nowMs: Long,
): Boolean = when (filter) {
    HistoryQuickFilter.ALL -> true
    HistoryQuickFilter.TODAY -> recencyBucket(thread.updatedAt, nowMs) == RecencyBucket.TODAY
    HistoryQuickFilter.THIS_WEEK -> recencyBucket(thread.updatedAt, nowMs) == RecencyBucket.THIS_WEEK
    HistoryQuickFilter.EARLIER -> recencyBucket(thread.updatedAt, nowMs) == RecencyBucket.EARLIER
}

private enum class RecencyBucket {
    TODAY,
    THIS_WEEK,
    EARLIER,
}

private fun RecencyBucket.title(textSet: HistoryTextSet): String = when (this) {
    RecencyBucket.TODAY -> textSet.todayTitle
    RecencyBucket.THIS_WEEK -> textSet.thisWeekTitle
    RecencyBucket.EARLIER -> textSet.earlierTitle
}

private fun recencyBucket(
    epochMillis: Long,
    nowMs: Long,
): RecencyBucket {
    val zone = ZoneId.systemDefault()
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val targetDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(targetDate, nowDate)

    return when {
        days <= 0L -> RecencyBucket.TODAY
        days <= 6L -> RecencyBucket.THIS_WEEK
        else -> RecencyBucket.EARLIER
    }
}
