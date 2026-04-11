package com.example.relaychat.data.threads

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {
    @Transaction
    @Query("SELECT * FROM threads ORDER BY updatedAt DESC")
    fun observeThreads(): Flow<List<ThreadWithMessages>>

    @Transaction
    @Query("SELECT * FROM threads ORDER BY updatedAt DESC")
    suspend fun getThreads(): List<ThreadWithMessages>

    @Transaction
    @Query("SELECT * FROM threads WHERE id = :threadId")
    suspend fun getThread(threadId: String): ThreadWithMessages?

    @Query("SELECT EXISTS(SELECT 1 FROM threads WHERE id = :threadId)")
    suspend fun threadExists(threadId: String): Boolean

    @Upsert
    suspend fun upsertThread(thread: ThreadEntity)

    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM messages WHERE threadId = :threadId")
    suspend fun deleteMessagesByThreadId(threadId: String)

    @Query("DELETE FROM threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: String)
}
