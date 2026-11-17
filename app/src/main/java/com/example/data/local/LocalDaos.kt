package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAtMillis DESC")
    fun observeAllDrafts(): Flow<List<DraftEntity>>

    @Query("SELECT * FROM drafts WHERE syncStateWire = 'draft' ORDER BY updatedAtMillis DESC LIMIT 1")
    suspend fun getLatestEditableDraft(): DraftEntity?

    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun getDraftById(id: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: DraftEntity)

    @Query(
        "UPDATE drafts SET syncStateWire = :syncStateWire, retryCount = :retryCount, " +
            "lastError = :lastError, remoteIncidentId = :remoteIncidentId, " +
            "updatedAtMillis = :updatedAtMillis WHERE id = :draftId",
    )
    suspend fun updateSyncStatus(
        draftId: String,
        syncStateWire: String,
        retryCount: Int,
        lastError: String?,
        remoteIncidentId: String?,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteDraftById(id: String)

    @Query("DELETE FROM drafts WHERE syncStateWire = 'synced' AND updatedAtMillis < :cutoffMillis")
    suspend fun deleteSyncedOlderThan(cutoffMillis: Long): Int
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC")
    fun observeMessages(conversationId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestampMillis ASC")
    suspend fun getMessagesOnce(conversationId: String): List<ConversationMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<ConversationMessageEntity>)

    @Query("DELETE FROM conversation_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversations WHERE updatedAtMillis < :cutoffMillis")
    suspend fun deleteConversationsOlderThan(cutoffMillis: Long)
}

@Dao
interface LocalMediaDao {
    @Query("SELECT * FROM local_media ORDER BY createdAtMillis DESC")
    fun observeAllMedia(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media WHERE draftId = :draftId")
    suspend fun getMediaForDraft(draftId: String): List<LocalMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(media: LocalMediaEntity)

    @Query("UPDATE local_media SET isSynced = 1 WHERE draftId = :draftId")
    suspend fun markSyncedForDraft(draftId: String)

    @Query("SELECT * FROM local_media WHERE (isSynced = 1 OR expiresAtMillis < :nowMillis)")
    suspend fun getCleanableMedia(nowMillis: Long): List<LocalMediaEntity>

    @Query("DELETE FROM local_media WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM local_media WHERE draftId = :draftId")
    suspend fun deleteForDraft(draftId: String)
}

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync ORDER BY createdAtMillis ASC")
    fun observeQueue(): Flow<List<PendingSyncEntity>>

    @Query("SELECT COUNT(*) FROM pending_sync WHERE syncStateWire IN ('pending_sync', 'syncing', 'sync_failed')")
    fun observePendingCount(): Flow<Int>

    @Query(
        "SELECT * FROM pending_sync " +
            "WHERE (syncStateWire = 'pending_sync' OR syncStateWire = 'sync_failed') " +
            "AND nextAttemptAtMillis <= :nowMillis ORDER BY createdAtMillis ASC",
    )
    suspend fun getDueItems(nowMillis: Long): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync WHERE syncStateWire IN ('pending_sync', 'sync_failed') ORDER BY createdAtMillis ASC")
    suspend fun getAllPendingOrFailed(): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun deleteById(id: String)
}
