package com.example.relaychat.core.model

internal const val LegacyDefaultThreadTitle = "New Chat"

internal fun isDefaultThreadTitle(
    title: String,
    defaultTitle: String,
): Boolean = title.isBlank() || title == defaultTitle || title == LegacyDefaultThreadTitle

internal fun presentThreadTitle(
    title: String,
    defaultTitle: String,
): String = if (isDefaultThreadTitle(title, defaultTitle)) defaultTitle else title
