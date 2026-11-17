package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.config.PilotCityConfig
import com.example.core.model.Severity
import com.example.core.model.SyncState

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val cityCode: String = PilotCityConfig.salvador.cityCode,
    val categorySlug: String,
    val title: String,
    val description: String,
    val severityWire: String = Severity.MEDIUM.wireValue,
    val latitude: Double?,
    val longitude: Double?,
    val addressLabel: String?,
    val userNarrative: String,
    val sanitizedPhotoPath: String?,
    val sanitizedPhotoBytes: Long = 0L,
    val sanitizedPhotoSha256: String? = null,
    val strippedSensitiveExif: Boolean = false,
    val aiModelName: String? = null,
    val aiConfidence: Double? = null,
    val aiFallbackReason: String? = null,
    val syncStateWire: String = SyncState.DRAFT.wireValue,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val idempotencyKey: String,
    val remoteIncidentId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun resolvedSyncState(): SyncState =
        SyncState.entries.firstOrNull { it.wireValue == syncStateWire } ?: SyncState.DRAFT

    fun resolvedSeverity(): Severity =
        Severity.entries.firstOrNull { it.wireValue == severityWire } ?: Severity.MEDIUM
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "conversation_messages",
    indices = [Index(value = ["conversationId"])],
)
data class ConversationMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val roleWire: String,
    val text: String,
    val imagePath: String? = null,
    val timestampMillis: Long,
)

@Entity(
    tableName = "local_media",
    indices = [Index(value = ["draftId"])],
)
data class LocalMediaEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val filePath: String,
    val sha256Hex: String,
    val byteSize: Long,
    val strippedSensitiveExif: Boolean,
    val isSynced: Boolean = false,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
)

@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey val id: String,
    val draftId: String,
    val operationType: String = "CREATE_INCIDENT",
    val idempotencyKey: String,
    val syncStateWire: String = SyncState.PENDING_SYNC.wireValue,
    val attemptCount: Int = 0,
    val nextAttemptAtMillis: Long = 0L,
    val lastError: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun resolvedSyncState(): SyncState =
        SyncState.entries.firstOrNull { it.wireValue == syncStateWire } ?: SyncState.PENDING_SYNC
}
