package com.example.relaychat.data.threads

import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThreadMutationsTest {
    @Test
    fun appendMessageAutoTitlesNewChatFromFirstUserMessage() {
        val thread = ChatThread(title = "New Chat", messages = emptyList())
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            text = "Summarize the latest Android app architecture decisions for me."
        )

        val updated = ThreadMutations.appendMessage(thread, userMessage)

        assertThat(updated.messages).hasSize(1)
        assertThat(updated.title).isEqualTo("Summarize the latest Android")
    }

    @Test
    fun appendMessageAutoTitlesLocalizedUntitledThreadFromFirstUserMessage() {
        val thread = ChatThread(title = "新对话", messages = emptyList())
        val userMessage = ChatMessage(
            role = ChatRole.USER,
            text = "请总结最近的 Android 架构调整。"
        )

        val updated = ThreadMutations.appendMessage(
            thread = thread,
            message = userMessage,
            defaultTitle = "新对话",
        )

        assertThat(updated.messages).hasSize(1)
        assertThat(updated.title).isEqualTo("请总结最近的 Android 架构调整。")
    }

    @Test
    fun removeLastTurnDropsAssistantAndPreviousUser() {
        val thread = ChatThread(
            messages = listOf(
                ChatMessage(role = ChatRole.USER, text = "First"),
                ChatMessage(role = ChatRole.ASSISTANT, text = "Reply", remoteResponseId = "resp_1"),
                ChatMessage(role = ChatRole.USER, text = "Follow up"),
                ChatMessage(role = ChatRole.ASSISTANT, text = "Second reply", remoteResponseId = "resp_2"),
            ),
            lastResponseId = "resp_2",
        )

        val updated = ThreadMutations.removeLastTurn(thread)

        assertThat(updated.messages.map { it.text }).containsExactly("First", "Reply").inOrder()
        assertThat(updated.lastResponseId).isEqualTo("resp_1")
    }

    @Test
    fun branchThreadThroughMessageKeepsPrefixAndLastAssistantResponseId() {
        val firstUser = ChatMessage(role = ChatRole.USER, text = "First")
        val firstAssistant = ChatMessage(
            role = ChatRole.ASSISTANT,
            text = "Reply",
            remoteResponseId = "resp_1"
        )
        val secondUser = ChatMessage(role = ChatRole.USER, text = "Second")
        val thread = ChatThread(
            title = "Main thread",
            messages = listOf(firstUser, firstAssistant, secondUser),
            lastResponseId = "resp_1",
        )

        val branched = ThreadMutations.branchThread(thread, firstAssistant.id)

        requireNotNull(branched)
        assertThat(branched.title).isEqualTo("Main thread Branch")
        assertThat(branched.messages.map { it.id }).containsExactly(firstUser.id, firstAssistant.id).inOrder()
        assertThat(branched.lastResponseId).isEqualTo("resp_1")
    }

    @Test
    fun branchThreadUsesInjectedLocalizedSuffix() {
        val user = ChatMessage(role = ChatRole.USER, text = "第一条")
        val assistant = ChatMessage(role = ChatRole.ASSISTANT, text = "回复")
        val thread = ChatThread(
            title = "当前会话",
            messages = listOf(user, assistant),
        )

        val branched = ThreadMutations.branchThread(
            thread = thread,
            throughMessageId = assistant.id,
            branchSuffix = " 分支",
        )

        requireNotNull(branched)
        assertThat(branched.title).isEqualTo("当前会话 分支")
    }

    @Test
    fun duplicateThreadKeepsContentAndRenamesCopy() {
        val thread = ChatThread(
            title = "Existing",
            messages = listOf(ChatMessage(role = ChatRole.USER, text = "Hello"))
        )

        val duplicated = ThreadMutations.duplicateThread(thread)

        assertThat(duplicated.id).isNotEqualTo(thread.id)
        assertThat(duplicated.title).isEqualTo("Existing Copy")
        assertThat(duplicated.messages).hasSize(1)
        assertThat(duplicated.messages.first().text).isEqualTo("Hello")
    }

    @Test
    fun duplicateThreadUsesInjectedLocalizedSuffix() {
        val thread = ChatThread(
            title = "现有会话",
            messages = listOf(ChatMessage(role = ChatRole.USER, text = "你好"))
        )

        val duplicated = ThreadMutations.duplicateThread(
            thread = thread,
            copySuffix = " 副本",
        )

        assertThat(duplicated.title).isEqualTo("现有会话 副本")
    }

    @Test
    fun removeTrailingEmptyAssistantMessagesDropsIncompletePlaceholder() {
        val user = ChatMessage(role = ChatRole.USER, text = "What changed?")
        val placeholder = ChatMessage(role = ChatRole.ASSISTANT, text = "   ")
        val thread = ChatThread(
            title = "What changed?",
            messages = listOf(user, placeholder),
        )

        val updated = ThreadMutations.removeTrailingEmptyAssistantMessages(thread)

        assertThat(updated.messages.map { it.id }).containsExactly(user.id)
        assertThat(updated.lastResponseId).isNull()
    }
}
