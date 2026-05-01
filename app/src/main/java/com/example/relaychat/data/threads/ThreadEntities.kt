package com.example.relaychat.data.threads

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastResponseId: String?,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["threadId"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val position: Int,
    val role: String,
    val text: String,
    val createdAt: Long,
    val remoteResponseId: String?,
    val requestId: String?,
    val model: String?,
    val imageGenerationJson: String?,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["messageId"])],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val position: Int,
    val mimeType: String,
    val data: ByteArray,
    val filePath: String?,
)

data class MessageWithAttachments(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "messageId",
    )
    val attachments: List<AttachmentEntity>,
)

data class ThreadWithMessages(
    @Embedded val thread: ThreadEntity,
    @Relation(
        entity = MessageEntity::class,
        parentColumn = "id",
        entityColumn = "threadId",
    )
    val messages: List<MessageWithAttachments>,
)
