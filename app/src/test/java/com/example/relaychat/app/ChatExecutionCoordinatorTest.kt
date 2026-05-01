package com.example.relaychat.app

import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatSendResult
import com.example.relaychat.core.model.ChatStreamEvent
import com.example.relaychat.core.model.ChatStreamLifecycleStage
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ImageGenerationMetadata
import com.example.relaychat.core.model.ProviderPreset
import com.example.relaychat.core.model.RequestControls
import com.example.relaychat.core.model.RuntimeChatConfiguration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatExecutionCoordinatorTest {
    @Test
    fun startPersistsCompletedAssistantReplyAndClearsActiveState() = runTest {
        val persisted = mutableListOf<Pair<String, ChatSendResult>>()
        val thread = ChatThread(
            id = "thread-1",
            messages = listOf(ChatMessage(role = ChatRole.USER, text = "Hello")),
        )
        val coordinator = ChatExecutionCoordinator(
            scope = backgroundScope,
            loadRuntimeSnapshot = {
                RuntimeChatConfiguration(
                    settings = AppSettings(provider = ProviderPreset.INTELALLOC_CODEX.profile),
                    apiKey = "test-key",
                )
            },
            loadThread = { threadId -> thread.takeIf { it.id == threadId } },
            saveAssistantResult = { threadId, result -> persisted += threadId to result },
            streamRequest = { _, _, _ ->
                flow {
                    emit(
                        ChatStreamEvent.Lifecycle(
                            type = "response.reasoning_summary_text.delta",
                            stage = ChatStreamLifecycleStage.REASONING,
                            detail = "thinking",
                        ),
                    )
                    emit(ChatStreamEvent.TextDelta("Hel"))
                    emit(ChatStreamEvent.TextDelta("lo"))
                    emit(
                        ChatStreamEvent.Completed(
                            ChatSendResult(
                                assistantText = "Hello",
                                responseId = "resp_1",
                                requestId = "req_1",
                                model = "gpt-test",
                            ),
                        ),
                    )
                }
            },
            sendRequest = { _, _, _ ->
                error("send fallback should not run in the streaming success path")
            },
        )

        val startResult = coordinator.start("thread-1", RequestControls.Standard)

        assertThat(startResult).isInstanceOf(ChatExecutionStartResult.Started::class.java)
        assertThat(coordinator.activeReply.value?.threadId).isEqualTo("thread-1")

        coordinator.awaitIdle()

        assertThat(persisted).hasSize(1)
        assertThat(persisted.single().first).isEqualTo("thread-1")
        assertThat(persisted.single().second.assistantText).isEqualTo("Hello")
        assertThat(coordinator.activeReply.value).isNull()
        assertThat(coordinator.lastFailure.value).isNull()
    }

    @Test
    fun startStoresFailureForLaterUiRecovery() = runTest {
        val coordinator = ChatExecutionCoordinator(
            scope = backgroundScope,
            loadRuntimeSnapshot = {
                RuntimeChatConfiguration(
                    settings = AppSettings(provider = ProviderPreset.OPENAI_CHAT_COMPLETIONS.profile),
                    apiKey = "test-key",
                )
            },
            loadThread = { ChatThread(id = it, messages = listOf(ChatMessage(role = ChatRole.USER, text = "Hi"))) },
            saveAssistantResult = { _, _ -> error("assistant result should not be persisted on failure") },
            streamRequest = { _, _, _ -> flow { } },
            sendRequest = { _, _, _ -> error("network down") },
        )

        coordinator.start("thread-2", RequestControls.Standard)
        coordinator.awaitIdle()

        assertThat(coordinator.activeReply.value).isNull()
        assertThat(coordinator.lastFailure.value?.threadId).isEqualTo("thread-2")
        assertThat(coordinator.lastFailure.value?.message).contains("network down")
    }

    @Test
    fun startRoutesImageProviderThroughNonStreamingImageRequest() = runTest {
        val persisted = mutableListOf<Pair<String, ChatSendResult>>()
        var streamCalls = 0
        var sendCalls = 0
        var imageCalls = 0
        val metadata = ImageGenerationMetadata(
            prompt = "Draw a quiet desk setup",
            model = "gpt-image-2",
            size = "1024x1024",
            quality = "auto",
            imagePath = "generated-images/image-1.png",
            createdAt = 1760000000000L,
        )
        val thread = ChatThread(
            id = "thread-image",
            messages = listOf(ChatMessage(role = ChatRole.USER, text = metadata.prompt)),
        )
        val coordinator = ChatExecutionCoordinator(
            scope = backgroundScope,
            loadRuntimeSnapshot = {
                RuntimeChatConfiguration(
                    settings = AppSettings(provider = ProviderPreset.OPENAI_IMAGE.profile),
                    apiKey = "test-key",
                )
            },
            loadThread = { threadId -> thread.takeIf { it.id == threadId } },
            saveAssistantResult = { threadId, result -> persisted += threadId to result },
            streamRequest = { _, _, _ ->
                streamCalls += 1
                flow { }
            },
            sendRequest = { _, _, _ ->
                sendCalls += 1
                error("chat request should not run for image provider")
            },
            generateImageRequest = { _, _, _ ->
                imageCalls += 1
                ChatSendResult(
                    assistantText = "Image generated.",
                    responseId = null,
                    requestId = "req_img",
                    model = "gpt-image-2",
                    attachments = listOf(
                        ChatAttachment(
                            mimeType = "image/png",
                            data = byteArrayOf(1, 2, 3),
                            filePath = metadata.imagePath,
                        )
                    ),
                    imageGeneration = metadata,
                )
            },
        )

        coordinator.start("thread-image", RequestControls.Standard)
        coordinator.awaitIdle()

        assertThat(streamCalls).isEqualTo(0)
        assertThat(sendCalls).isEqualTo(0)
        assertThat(imageCalls).isEqualTo(1)
        assertThat(persisted).hasSize(1)
        assertThat(persisted.single().second.attachments.single().filePath).isEqualTo(metadata.imagePath)
        assertThat(persisted.single().second.imageGeneration).isEqualTo(metadata)
    }

    @Test
    fun startUsesInjectedLocalizedInProgressMessage() = runTest {
        val coordinator = ChatExecutionCoordinator(
            scope = backgroundScope,
            loadRuntimeSnapshot = {
                RuntimeChatConfiguration(
                    settings = AppSettings(provider = ProviderPreset.INTELALLOC_CODEX.profile),
                    apiKey = "test-key",
                )
            },
            loadThread = { ChatThread(id = it, messages = listOf(ChatMessage(role = ChatRole.USER, text = "Hi"))) },
            saveAssistantResult = { _, _ -> },
            streamRequest = { _, _, _ -> flow { awaitCancellation() } },
            sendRequest = { _, _, _ -> error("should not run") },
            requestInProgressMessage = "\u5df2\u6709\u56de\u590d\u6b63\u5728\u751f\u6210",
        )

        val first = coordinator.start("thread-1", RequestControls.Standard)
        val second = coordinator.start("thread-2", RequestControls.Standard)

        assertThat(first).isInstanceOf(ChatExecutionStartResult.Started::class.java)
        assertThat(second).isEqualTo(
            ChatExecutionStartResult.Rejected("\u5df2\u6709\u56de\u590d\u6b63\u5728\u751f\u6210")
        )
    }
}
