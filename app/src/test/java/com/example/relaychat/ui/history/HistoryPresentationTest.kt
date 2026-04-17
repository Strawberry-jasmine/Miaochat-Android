package com.example.relaychat.ui.history

import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HistoryPresentationTest {
    @Test
    fun buildSectionsPinsCurrentThreadAndGroupsRemainingThreadsByRecency() {
        val nowMs = 1_750_000_000_000L
        val current = thread(
            id = "current",
            title = "Current",
            updatedAt = nowMs - DAY_MS * 10,
            text = "current thread",
        )
        val today = thread(
            id = "today",
            title = "Today",
            updatedAt = nowMs - 1_000L,
            text = "today thread",
        )
        val week = thread(
            id = "week",
            title = "Week",
            updatedAt = nowMs - DAY_MS * 3,
            text = "week thread",
        )
        val earlier = thread(
            id = "earlier",
            title = "Earlier",
            updatedAt = nowMs - DAY_MS * 20,
            text = "earlier thread",
        )

        val sections = buildHistorySections(
            threads = listOf(today, current, earlier, week),
            selectedThreadId = current.id,
            query = "",
            filter = HistoryQuickFilter.ALL,
            nowMs = nowMs,
        )

        assertThat(sections.map { it.title }).containsExactly(
            "Current",
            "Today",
            "This Week",
            "Earlier",
        ).inOrder()
        assertThat(sections.first().items.single().thread.id).isEqualTo("current")
        assertThat(sections[1].items.single().thread.id).isEqualTo("today")
        assertThat(sections[2].items.single().thread.id).isEqualTo("week")
        assertThat(sections[3].items.single().thread.id).isEqualTo("earlier")
    }

    @Test
    fun buildSectionsUseMatchedMessageSnippetWhenSearching() {
        val thread = ChatThread(
            id = "mars",
            title = "Space notes",
            updatedAt = 1_750_000_000_000L,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    text = "A short unrelated opener",
                    createdAt = 1_750_000_000_000L - 10_000L,
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    text = "Latest Mars sample return updates say the schedule is shifting again.",
                    createdAt = 1_750_000_000_000L - 5_000L,
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    text = "Closing note",
                    createdAt = 1_750_000_000_000L,
                ),
            ),
        )

        val sections = buildHistorySections(
            threads = listOf(thread),
            selectedThreadId = null,
            query = "mars",
            filter = HistoryQuickFilter.ALL,
            nowMs = 1_750_000_000_000L,
        )

        assertThat(sections.single().title).isEqualTo("Matches")
        assertThat(sections.single().items.single().preview).contains("Mars sample return")
        assertThat(sections.single().items.single().preview).doesNotContain("Closing note")
    }

    @Test
    fun todayFilterKeepsOnlyTodayThreads() {
        val nowMs = 1_750_000_000_000L
        val today = thread(id = "today", title = "Today", updatedAt = nowMs - 10_000L, text = "today")
        val week = thread(id = "week", title = "Week", updatedAt = nowMs - DAY_MS * 2, text = "week")

        val sections = buildHistorySections(
            threads = listOf(today, week),
            selectedThreadId = null,
            query = "",
            filter = HistoryQuickFilter.TODAY,
            nowMs = nowMs,
        )

        assertThat(sections).hasSize(1)
        assertThat(sections.single().items.map { it.thread.id }).containsExactly("today")
    }

    @Test
    fun buildSectionsUsesInjectedLocalizedLabelsAndEmptyPreview() {
        val localizedLabels = HistoryTextSet(
            matchesTitle = "\u5339\u914d\u7ed3\u679c",
            currentTitle = "\u5f53\u524d\u4f1a\u8bdd",
            todayTitle = "\u4eca\u5929",
            thisWeekTitle = "\u672c\u5468",
            earlierTitle = "\u66f4\u65e9",
            emptyThreadPreview = "\u7a7a\u4f1a\u8bdd",
        )
        val emptyCurrent = ChatThread(
            id = "empty",
            title = "\u5f53\u524d",
            updatedAt = 1_750_000_000_000L,
            messages = emptyList(),
        )

        val sections = buildHistorySections(
            threads = listOf(emptyCurrent),
            selectedThreadId = emptyCurrent.id,
            query = "",
            filter = HistoryQuickFilter.ALL,
            textSet = localizedLabels,
            nowMs = 1_750_000_000_000L,
        )

        assertThat(sections.single().title).isEqualTo("\u5f53\u524d\u4f1a\u8bdd")
        assertThat(sections.single().items.single().preview).isEqualTo("\u7a7a\u4f1a\u8bdd")
    }

    private fun thread(
        id: String,
        title: String,
        updatedAt: Long,
        text: String,
    ): ChatThread = ChatThread(
        id = id,
        title = title,
        updatedAt = updatedAt,
        messages = listOf(ChatMessage(role = ChatRole.USER, text = text, createdAt = updatedAt)),
    )
}

private const val DAY_MS = 24L * 60L * 60L * 1000L
