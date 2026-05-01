package com.example.relaychat.data.threads

import com.example.relaychat.core.model.ChatAttachment
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.core.model.ChatThread
import com.example.relaychat.core.model.ImageGenerationMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThreadMappersTest {
    @Test
    fun generatedImageMetadataAndPathRoundTripThroughEntities() {
        val metadata = ImageGenerationMetadata(
            prompt = "Draw a small studio monitor speaker",
            model = "gpt-image-2",
            size = "1024x1024",
            quality = "auto",
            imagePath = "generated-images/image-1760000000000.png",
            createdAt = 1760000000000L,
        )
        val message = ChatMessage(
            role = ChatRole.ASSISTANT,
            text = "Image generated.",
            attachments = listOf(
                ChatAttachment(
                    mimeType = "image/png",
                    data = byteArrayOf(1, 2, 3),
                    filePath = metadata.imagePath,
                )
            ),
            model = metadata.model,
            imageGeneration = metadata,
        )
        val thread = ChatThread(messages = listOf(message))

        val messageEntity = thread.toMessageEntities().single()
        val attachmentEntity = thread.toAttachmentEntities().single()
        val roundTripped = ThreadWithMessages(
            thread = thread.toEntity(),
            messages = listOf(
                MessageWithAttachments(
                    message = messageEntity,
                    attachments = listOf(attachmentEntity),
                )
            ),
        ).toModel()

        val restoredMessage = roundTripped.messages.single()
        assertThat(messageEntity.imageGenerationJson).contains(metadata.prompt)
        assertThat(attachmentEntity.filePath).isEqualTo(metadata.imagePath)
        assertThat(restoredMessage.imageGeneration).isEqualTo(metadata)
        assertThat(restoredMessage.attachments.single().filePath).isEqualTo(metadata.imagePath)
    }
}
