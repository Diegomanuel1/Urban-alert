package com.example.data.sync

import android.util.Log
import com.example.core.model.IncidentStatus
import com.example.core.model.SyncState
import com.example.data.incidents.IncidentDocument
import com.example.data.incidents.IncidentRepository
import com.example.data.local.LocalOfflineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.pow

data class SyncBatchResult(
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val deferredAuthCount: Int = 0,
    val cleanedMediaFiles: Int = 0,
)

/**
 * Motor de sincronização idempotente para processar a fila offline (`pending_sync`).
 * Garante retentativas com backoff exponencial sem duplicar ocorrências no servidor.
 */
class SyncEngine(
    private val offlineRepository: LocalOfflineRepository,
    private val publishIncident: suspend (IncidentDocument) -> Result<String>,
) {
    constructor(
        offlineRepository: LocalOfflineRepository,
        incidentRepository: IncidentRepository,
    ) : this(
        offlineRepository = offlineRepository,
        publishIncident = { doc -> incidentRepository.createIncident(doc) },
    )

    private val syncMutex = Mutex()

    suspend fun syncAllDue(
        authorUid: String?,
        forceRetryAll: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): SyncBatchResult = syncMutex.withLock {
        val candidates = offlineRepository.getSyncCandidates(
            nowMillis = nowMillis,
            forceRetryAll = forceRetryAll,
        )
        if (candidates.isEmpty()) {
            val cleanup = offlineRepository.runRetentionCleanup(nowMillis)
            return SyncBatchResult(cleanedMediaFiles = cleanup.removedMediaFiles)
        }

        if (authorUid.isNullOrBlank()) {
            candidates.forEach { item ->
                offlineRepository.markQueueItemDeferredOrFailed(
                    item = item,
                    targetState = SyncState.PENDING_SYNC,
                    errorMessage = "Entre com sua conta Google para enviar os itens pendentes.",
                    nextAttemptAtMillis = nowMillis,
                    incrementRetry = false,
                    nowMillis = nowMillis,
                )
            }
            return SyncBatchResult(deferredAuthCount = candidates.size)
        }

        var synced = 0
        var failed = 0

        for (item in candidates) {
            val draft = offlineRepository.getDraftById(item.draftId)
            if (draft == null) {
                offlineRepository.markQueueItemSynced(
                    item = item,
                    remoteIncidentId = "orphaned",
                    nowMillis = nowMillis,
                )
                continue
            }

            val lat = draft.latitude
            val lng = draft.longitude
            if (lat == null || lng == null) {
                offlineRepository.markQueueItemDeferredOrFailed(
                    item = item,
                    targetState = SyncState.SYNC_FAILED,
                    errorMessage = "Localização ausente no rascunho. Edite e defina o ponto no mapa.",
                    nextAttemptAtMillis = Long.MAX_VALUE,
                    incrementRetry = true,
                    nowMillis = nowMillis,
                )
                failed++
                continue
            }

            offlineRepository.markSyncing(item, nowMillis)

            val doc = IncidentDocument(
                authorId = authorUid,
                cityCode = draft.cityCode,
                categorySlug = draft.categorySlug,
                title = draft.title,
                description = draft.description,
                severity = draft.severityWire,
                status = IncidentStatus.OPEN.wireValue,
                latitude = lat,
                longitude = lng,
                addressLabel = draft.addressLabel,
                publicImageUrl = null,
                idempotencyKey = item.idempotencyKey,
            )

            try {
                val publishResult = publishIncident(doc)
                publishResult.onSuccess { remoteId ->
                    offlineRepository.markQueueItemSynced(
                        item = item,
                        remoteIncidentId = remoteId,
                        nowMillis = nowMillis,
                    )
                    synced++
                }.onFailure { err ->
                    val backoffMs = computeBackoffMillis(item.attemptCount)
                    val msg = err.localizedMessage
                        ?: "Falha temporária de rede ao sincronizar."
                    Log.w(TAG, "Falha ao sincronizar rascunho ${item.draftId}", err)
                    offlineRepository.markQueueItemDeferredOrFailed(
                        item = item,
                        targetState = SyncState.SYNC_FAILED,
                        errorMessage = msg,
                        nextAttemptAtMillis = nowMillis + backoffMs,
                        incrementRetry = true,
                        nowMillis = nowMillis,
                    )
                    failed++
                }
            } catch (e: CancellationException) {
                throw e
            }
        }

        val cleanup = offlineRepository.runRetentionCleanup(nowMillis)
        SyncBatchResult(
            syncedCount = synced,
            failedCount = failed,
            cleanedMediaFiles = cleanup.removedMediaFiles,
        )
    }

    companion object {
        private const val TAG = "SyncEngine"
        private const val BASE_BACKOFF_MS = 30_000L
        private const val MAX_BACKOFF_MS = 15L * 60_000L

        fun computeBackoffMillis(previousAttempts: Int): Long {
            val multiplier = 2.0.pow(previousAttempts.coerceIn(0, 6).toDouble()).toLong()
            return min(BASE_BACKOFF_MS * multiplier, MAX_BACKOFF_MS)
        }
    }
}
