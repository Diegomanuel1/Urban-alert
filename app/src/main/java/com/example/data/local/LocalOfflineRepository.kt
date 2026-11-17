package com.example.data.local

import com.example.core.agent.AgentChatMessage
import com.example.core.agent.AgentMessageRole
import com.example.core.agent.CitizenIncidentDraft
import com.example.core.model.GeoCoordinate
import com.example.core.model.Severity
import com.example.core.model.SyncState
import com.example.core.security.MediaSanitizer
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

data class RestoredDraftSession(
    val draftId: String,
    val conversationId: String,
    val draft: CitizenIncidentDraft,
    val messages: List<AgentChatMessage>,
)

data class RetentionCleanupReport(
    val removedMediaFiles: Int,
    val purgedSyncedDrafts: Int,
)

/**
 * Repositório local (SQLite / Room) para rascunhos, histórico conversacional do agente,
 * mídias sanitizadas e fila de sincronização idempotente.
 */
class LocalOfflineRepository(
    private val database: UrbanAlertDatabase,
) {
    private val draftDao = database.draftDao()
    private val conversationDao = database.conversationDao()
    private val localMediaDao = database.localMediaDao()
    private val pendingSyncDao = database.pendingSyncDao()

    fun observeAllDrafts(): Flow<List<DraftEntity>> = draftDao.observeAllDrafts()

    fun observeSyncQueue(): Flow<List<PendingSyncEntity>> = pendingSyncDao.observeQueue()

    fun observePendingCount(): Flow<Int> = pendingSyncDao.observePendingCount()

    suspend fun saveActiveDraftAndConversation(
        draftId: String,
        conversationId: String,
        draft: CitizenIncidentDraft,
        messages: List<AgentChatMessage>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val hasMeaningfulContent = !draft.sanitizedPhotoPath.isNullOrBlank() ||
            draft.userNarrative.isNotBlank() ||
            draft.title.isNotBlank() ||
            draft.description.isNotBlank() ||
            draft.coordinate != null

        if (!hasMeaningfulContent) return

        val existing = draftDao.getDraftById(draftId)
        val createdAt = existing?.createdAtMillis ?: nowMillis

        val entity = DraftEntity(
            id = draftId,
            cityCode = draft.cityCode,
            categorySlug = draft.selectedCategorySlug,
            title = draft.title,
            description = draft.description,
            severityWire = draft.selectedSeverity.wireValue,
            latitude = draft.coordinate?.latitude,
            longitude = draft.coordinate?.longitude,
            addressLabel = draft.addressLabel,
            userNarrative = draft.userNarrative,
            sanitizedPhotoPath = draft.sanitizedPhotoPath,
            sanitizedPhotoBytes = draft.sanitizedPhotoBytes,
            sanitizedPhotoSha256 = draft.sanitizedPhotoSha256,
            strippedSensitiveExif = draft.strippedSensitiveExif,
            aiModelName = draft.aiDraft?.modelName,
            aiConfidence = draft.aiDraft?.confidence,
            aiFallbackReason = draft.aiFallbackReason,
            syncStateWire = existing?.syncStateWire ?: SyncState.DRAFT.wireValue,
            retryCount = existing?.retryCount ?: 0,
            lastError = existing?.lastError,
            idempotencyKey = draft.idempotencyKey,
            remoteIncidentId = existing?.remoteIncidentId,
            createdAtMillis = createdAt,
            updatedAtMillis = nowMillis,
        )
        draftDao.upsertDraft(entity)

        val photoPath = draft.sanitizedPhotoPath
        val photoSha = draft.sanitizedPhotoSha256
        if (!photoPath.isNullOrBlank() && !photoSha.isNullOrBlank()) {
            localMediaDao.upsertMedia(
                LocalMediaEntity(
                    id = "media_$draftId",
                    draftId = draftId,
                    filePath = photoPath,
                    sha256Hex = photoSha,
                    byteSize = draft.sanitizedPhotoBytes,
                    strippedSensitiveExif = draft.strippedSensitiveExif,
                    isSynced = false,
                    createdAtMillis = createdAt,
                    expiresAtMillis = createdAt + MEDIA_RETENTION_MS,
                ),
            )
        }

        val titlePreview = draft.title.ifBlank { "Rascunho urbano" }.take(60)
        conversationDao.upsertConversation(
            ConversationEntity(
                id = conversationId,
                draftId = draftId,
                title = titlePreview,
                createdAtMillis = createdAt,
                updatedAtMillis = nowMillis,
            ),
        )
        conversationDao.deleteMessagesByConversation(conversationId)
        conversationDao.upsertMessages(
            messages.map { msg ->
                ConversationMessageEntity(
                    id = msg.id,
                    conversationId = conversationId,
                    roleWire = msg.role.name,
                    text = msg.text,
                    imagePath = msg.imagePath,
                    timestampMillis = msg.timestampMillis,
                )
            },
        )
    }

    suspend fun loadLatestEditableSession(): RestoredDraftSession? {
        val draftEntity = draftDao.getLatestEditableDraft() ?: return null
        val conversationId = "conv_${draftEntity.id}"
        val storedMessages = conversationDao.getMessagesOnce(conversationId)

        val coordinate = if (draftEntity.latitude != null && draftEntity.longitude != null) {
            GeoCoordinate(draftEntity.latitude, draftEntity.longitude)
        } else {
            null
        }
        val severity = Severity.entries.firstOrNull { it.wireValue == draftEntity.severityWire }
            ?: Severity.MEDIUM

        val restoredDraft = CitizenIncidentDraft(
            idempotencyKey = draftEntity.idempotencyKey,
            cityCode = draftEntity.cityCode,
            sanitizedPhotoPath = draftEntity.sanitizedPhotoPath,
            sanitizedPhotoBytes = draftEntity.sanitizedPhotoBytes,
            sanitizedPhotoSha256 = draftEntity.sanitizedPhotoSha256,
            strippedSensitiveExif = draftEntity.strippedSensitiveExif,
            userNarrative = draftEntity.userNarrative,
            coordinate = coordinate,
            addressLabel = draftEntity.addressLabel,
            aiDraft = null,
            aiFallbackReason = draftEntity.aiFallbackReason,
            selectedCategorySlug = draftEntity.categorySlug,
            selectedSeverity = severity,
            title = draftEntity.title,
            description = draftEntity.description,
        )

        val restoredMessages = storedMessages.map { msg ->
            AgentChatMessage(
                id = msg.id,
                role = AgentMessageRole.entries.firstOrNull { it.name == msg.roleWire }
                    ?: AgentMessageRole.AGENT,
                text = msg.text,
                imagePath = msg.imagePath,
                timestampMillis = msg.timestampMillis,
            )
        }

        return RestoredDraftSession(
            draftId = draftEntity.id,
            conversationId = conversationId,
            draft = restoredDraft,
            messages = restoredMessages,
        )
    }

    suspend fun enqueueForSync(
        draftId: String,
        conversationId: String,
        draft: CitizenIncidentDraft,
        messages: List<AgentChatMessage>,
        initialError: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        saveActiveDraftAndConversation(
            draftId = draftId,
            conversationId = conversationId,
            draft = draft,
            messages = messages,
            nowMillis = nowMillis,
        )
        draftDao.updateSyncStatus(
            draftId = draftId,
            syncStateWire = SyncState.PENDING_SYNC.wireValue,
            retryCount = 0,
            lastError = initialError,
            remoteIncidentId = null,
            updatedAtMillis = nowMillis,
        )
        pendingSyncDao.upsertItem(
            PendingSyncEntity(
                id = draftId,
                draftId = draftId,
                operationType = "CREATE_INCIDENT",
                idempotencyKey = draft.idempotencyKey,
                syncStateWire = SyncState.PENDING_SYNC.wireValue,
                attemptCount = 0,
                nextAttemptAtMillis = nowMillis,
                lastError = initialError,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
            ),
        )
    }

    suspend fun markDraftSyncedImmediately(
        draftId: String,
        conversationId: String,
        draft: CitizenIncidentDraft,
        messages: List<AgentChatMessage>,
        remoteIncidentId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        saveActiveDraftAndConversation(
            draftId = draftId,
            conversationId = conversationId,
            draft = draft,
            messages = messages,
            nowMillis = nowMillis,
        )
        draftDao.updateSyncStatus(
            draftId = draftId,
            syncStateWire = SyncState.SYNCED.wireValue,
            retryCount = 0,
            lastError = null,
            remoteIncidentId = remoteIncidentId,
            updatedAtMillis = nowMillis,
        )
        pendingSyncDao.deleteById(draftId)
        localMediaDao.markSyncedForDraft(draftId)
    }

    suspend fun deleteDraftAndLocalArtifacts(draftId: String) {
        val mediaList = localMediaDao.getMediaForDraft(draftId)
        mediaList.forEach { media ->
            MediaSanitizer.deleteLocalMedia(media.filePath)
        }
        val draft = draftDao.getDraftById(draftId)
        if (!draft?.sanitizedPhotoPath.isNullOrBlank()) {
            MediaSanitizer.deleteLocalMedia(draft?.sanitizedPhotoPath.orEmpty())
        }
        localMediaDao.deleteForDraft(draftId)
        pendingSyncDao.deleteById(draftId)
        val conversationId = "conv_$draftId"
        conversationDao.deleteMessagesByConversation(conversationId)
        conversationDao.deleteConversation(conversationId)
        draftDao.deleteDraftById(draftId)
    }

    suspend fun getDraftById(draftId: String): DraftEntity? = draftDao.getDraftById(draftId)

    suspend fun getSyncCandidates(nowMillis: Long, forceRetryAll: Boolean): List<PendingSyncEntity> {
        return if (forceRetryAll) {
            pendingSyncDao.getAllPendingOrFailed()
        } else {
            pendingSyncDao.getDueItems(nowMillis)
        }
    }

    suspend fun markSyncing(item: PendingSyncEntity, nowMillis: Long) {
        draftDao.updateSyncStatus(
            draftId = item.draftId,
            syncStateWire = SyncState.SYNCING.wireValue,
            retryCount = item.attemptCount,
            lastError = null,
            remoteIncidentId = null,
            updatedAtMillis = nowMillis,
        )
        pendingSyncDao.upsertItem(
            item.copy(
                syncStateWire = SyncState.SYNCING.wireValue,
                updatedAtMillis = nowMillis,
            ),
        )
    }

    suspend fun markQueueItemSynced(
        item: PendingSyncEntity,
        remoteIncidentId: String,
        nowMillis: Long,
    ) {
        draftDao.updateSyncStatus(
            draftId = item.draftId,
            syncStateWire = SyncState.SYNCED.wireValue,
            retryCount = item.attemptCount,
            lastError = null,
            remoteIncidentId = remoteIncidentId,
            updatedAtMillis = nowMillis,
        )
        pendingSyncDao.deleteById(item.id)
        localMediaDao.markSyncedForDraft(item.draftId)
    }

    suspend fun markQueueItemDeferredOrFailed(
        item: PendingSyncEntity,
        targetState: SyncState,
        errorMessage: String,
        nextAttemptAtMillis: Long,
        incrementRetry: Boolean,
        nowMillis: Long,
    ) {
        val nextCount = if (incrementRetry) item.attemptCount + 1 else item.attemptCount
        draftDao.updateSyncStatus(
            draftId = item.draftId,
            syncStateWire = targetState.wireValue,
            retryCount = nextCount,
            lastError = errorMessage,
            remoteIncidentId = null,
            updatedAtMillis = nowMillis,
        )
        pendingSyncDao.upsertItem(
            item.copy(
                syncStateWire = targetState.wireValue,
                attemptCount = nextCount,
                nextAttemptAtMillis = nextAttemptAtMillis,
                lastError = errorMessage,
                updatedAtMillis = nowMillis,
            ),
        )
    }

    suspend fun runRetentionCleanup(nowMillis: Long = System.currentTimeMillis()): RetentionCleanupReport {
        val cleanableMedia = localMediaDao.getCleanableMedia(nowMillis)
        var removedFiles = 0
        cleanableMedia.forEach { media ->
            if (MediaSanitizer.deleteLocalMedia(media.filePath)) {
                removedFiles++
            }
            localMediaDao.deleteById(media.id)
        }
        val syncedCutoff = nowMillis - SYNCED_DRAFT_RETENTION_MS
        val purgedDrafts = draftDao.deleteSyncedOlderThan(syncedCutoff)
        conversationDao.deleteConversationsOlderThan(syncedCutoff)
        return RetentionCleanupReport(
            removedMediaFiles = removedFiles,
            purgedSyncedDrafts = purgedDrafts,
        )
    }

    companion object {
        val MEDIA_RETENTION_MS = TimeUnit.DAYS.toMillis(7)
        val SYNCED_DRAFT_RETENTION_MS = TimeUnit.DAYS.toMillis(7)
    }
}
