package com.example.feature.report

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.agent.AgentChatMessage
import com.example.core.agent.AgentRunner
import com.example.core.agent.AgentStep
import com.example.core.agent.CitizenIncidentDraft
import com.example.core.config.PilotCityConfig
import com.example.core.model.GeoCoordinate
import com.example.core.model.IncidentStatus
import com.example.core.model.Severity
import com.example.data.incidents.IncidentDocument
import com.example.data.incidents.IncidentRepository
import com.example.data.local.DraftEntity
import com.example.data.local.LocalOfflineRepository
import com.example.data.sync.ConnectivityMonitor
import com.example.data.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class ReportViewModel(
    private val incidentRepository: IncidentRepository,
    private val offlineRepository: LocalOfflineRepository,
    private val syncEngine: SyncEngine,
    connectivityMonitor: ConnectivityMonitor,
    private val agentRunner: AgentRunner = AgentRunner(),
) : ViewModel() {

    val messages: StateFlow<List<AgentChatMessage>> = agentRunner.messages
    val draft: StateFlow<CitizenIncidentDraft> = agentRunner.draft
    val step: StateFlow<AgentStep> = agentRunner.step

    val isOnline: StateFlow<Boolean> = connectivityMonitor.observeIsOnline()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = connectivityMonitor.isCurrentlyOnline(),
        )

    val localDrafts: StateFlow<List<DraftEntity>> = offlineRepository.observeAllDrafts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
        )

    val pendingSyncCount: StateFlow<Int> = offlineRepository.observePendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0,
        )

    private var currentDraftId: String = "draft_${UUID.randomUUID()}"
    private var currentConversationId: String = "conv_$currentDraftId"
    private var hasRestoredInitialSession = false

    private val _isBusyTool = MutableStateFlow(false)
    val isBusyTool: StateFlow<Boolean> = _isBusyTool.asStateFlow()

    private val _isSyncingQueue = MutableStateFlow(false)
    val isSyncingQueue: StateFlow<Boolean> = _isSyncingQueue.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _syncNoticeBanner = MutableStateFlow<String?>(null)
    val syncNoticeBanner: StateFlow<String?> = _syncNoticeBanner.asStateFlow()

    private val _publishedIncidentId = MutableStateFlow<String?>(null)
    val publishedIncidentId: StateFlow<String?> = _publishedIncidentId.asStateFlow()

    init {
        viewModelScope.launch {
            offlineRepository.runRetentionCleanup()
            val restored = offlineRepository.loadLatestEditableSession()
            if (restored != null) {
                currentDraftId = restored.draftId
                currentConversationId = restored.conversationId
                agentRunner.restoreSavedSession(
                    draft = restored.draft,
                    messages = restored.messages,
                )
            }
            hasRestoredInitialSession = true
        }

        // Persiste alterações do rascunho e conversa automaticamente no SQLite (Room)
        viewModelScope.launch {
            combine(draft, messages, step) { d, m, s -> Triple(d, m, s) }
                .debounce(400L)
                .collect { (currentDraft, currentMessages, currentStep) ->
                    if (!hasRestoredInitialSession) return@collect
                    if (currentStep == AgentStep.COLLECTING_INPUT ||
                        currentStep == AgentStep.CITIZEN_REVIEW
                    ) {
                        offlineRepository.saveActiveDraftAndConversation(
                            draftId = currentDraftId,
                            conversationId = currentConversationId,
                            draft = currentDraft,
                            messages = currentMessages,
                        )
                    }
                }
        }
    }

    fun startFreshReport() {
        _errorBanner.value = null
        _publishedIncidentId.value = null
        currentDraftId = "draft_${UUID.randomUUID()}"
        currentConversationId = "conv_$currentDraftId"
        agentRunner.startNewSession()
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun clearSyncNotice() {
        _syncNoticeBanner.value = null
    }

    fun attachPhotoFromUri(context: Context, uri: Uri, cleanupTempFile: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isBusyTool.value = true
            _errorBanner.value = null
            try {
                val res = agentRunner.attachSanitizedPhoto(context, uri)
                res.onFailure { err ->
                    _errorBanner.value = err.localizedMessage
                        ?: "Erro ao sanitizar a imagem."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                cleanupTempFile?.invoke()
                _isBusyTool.value = false
            }
        }
    }

    fun sendCitizenMessage(text: String) {
        _errorBanner.value = null
        agentRunner.submitCitizenDescription(text)
    }

    fun captureCurrentGps(context: Context) {
        viewModelScope.launch {
            _isBusyTool.value = true
            _errorBanner.value = null
            try {
                val res = agentRunner.captureCurrentGpsLocation(context)
                res.onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Falha ao capturar GPS."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusyTool.value = false
            }
        }
    }

    fun resolveAddress(context: Context, query: String) {
        viewModelScope.launch {
            _isBusyTool.value = true
            _errorBanner.value = null
            try {
                val res = agentRunner.searchAndSetAddress(context, query)
                res.onFailure { err ->
                    _errorBanner.value = err.localizedMessage ?: "Endereço não encontrado."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isBusyTool.value = false
            }
        }
    }

    fun useSalvadorCenter() {
        _errorBanner.value = null
        agentRunner.setDraftLocation(
            coordinate = PilotCityConfig.salvador.center,
            addressLabel = "Centro de Salvador, BA",
        )
    }

    fun runAiAnalysis() {
        viewModelScope.launch {
            _errorBanner.value = null
            try {
                agentRunner.runAiAnalysisOrFallback()
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun goToManualReview() {
        _errorBanner.value = null
        agentRunner.openManualReview(fallbackReason = null)
    }

    fun returnToChat() {
        _errorBanner.value = null
        agentRunner.returnToChat()
    }

    fun updateReviewFields(
        categorySlug: String? = null,
        severity: Severity? = null,
        title: String? = null,
        description: String? = null,
        addressLabel: String? = null,
    ) {
        agentRunner.updateReviewFields(
            categorySlug = categorySlug,
            severity = severity,
            title = title,
            description = description,
            addressLabel = addressLabel,
        )
    }

    fun saveConfirmedToOfflineQueue() {
        val validationError = agentRunner.validateCurrentDraft()
        if (validationError != null) {
            _errorBanner.value = validationError
            return
        }
        viewModelScope.launch {
            _errorBanner.value = null
            offlineRepository.enqueueForSync(
                draftId = currentDraftId,
                conversationId = currentConversationId,
                draft = draft.value,
                messages = messages.value,
                initialError = null,
            )
            agentRunner.markQueuedOffline()
        }
    }

    fun publishConfirmedIncident(authorUid: String) {
        val validationError = agentRunner.validateCurrentDraft()
        if (validationError != null) {
            _errorBanner.value = validationError
            return
        }

        val currentDraft = draft.value
        val coordinate = currentDraft.coordinate ?: GeoCoordinate(0.0, 0.0)
        val doc = IncidentDocument(
            authorId = authorUid,
            cityCode = currentDraft.cityCode,
            categorySlug = currentDraft.selectedCategorySlug,
            title = currentDraft.title.trim(),
            description = currentDraft.description.trim(),
            severity = currentDraft.selectedSeverity.wireValue,
            status = IncidentStatus.OPEN.wireValue,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            addressLabel = currentDraft.addressLabel,
            publicImageUrl = null,
            idempotencyKey = currentDraft.idempotencyKey,
        )

        viewModelScope.launch {
            _errorBanner.value = null
            if (!isOnline.value) {
                offlineRepository.enqueueForSync(
                    draftId = currentDraftId,
                    conversationId = currentConversationId,
                    draft = currentDraft,
                    messages = messages.value,
                    initialError = "Aguardando conexão com a internet.",
                )
                agentRunner.markQueuedOffline()
                return@launch
            }

            agentRunner.setPublishing()
            try {
                val res = incidentRepository.createIncident(doc)
                res.onSuccess { createdId ->
                    offlineRepository.markDraftSyncedImmediately(
                        draftId = currentDraftId,
                        conversationId = currentConversationId,
                        draft = currentDraft,
                        messages = messages.value,
                        remoteIncidentId = createdId,
                    )
                    offlineRepository.runRetentionCleanup()
                    _publishedIncidentId.value = createdId
                    agentRunner.markPublished()
                }.onFailure { err ->
                    Log.w(TAG, "Envio imediato falhou; enfileirando para sincronização offline", err)
                    offlineRepository.enqueueForSync(
                        draftId = currentDraftId,
                        conversationId = currentConversationId,
                        draft = currentDraft,
                        messages = messages.value,
                        initialError = err.localizedMessage ?: "Falha temporária de envio.",
                    )
                    agentRunner.markQueuedOffline()
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    fun syncPendingNow(authorUid: String?, forceRetryAll: Boolean = true) {
        viewModelScope.launch {
            _isSyncingQueue.value = true
            try {
                val result = syncEngine.syncAllDue(
                    authorUid = authorUid,
                    forceRetryAll = forceRetryAll,
                )
                _syncNoticeBanner.value = when {
                    result.deferredAuthCount > 0 ->
                        "${result.deferredAuthCount} item(ns) aguardando login Google para sincronizar."
                    result.syncedCount > 0 && result.failedCount == 0 ->
                        "${result.syncedCount} ocorrência(s) sincronizada(s) com sucesso!"
                    result.failedCount > 0 ->
                        "${result.syncedCount} enviada(s) · ${result.failedCount} com falha temporária."
                    else ->
                        "Nenhum item pendente para sincronizar."
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isSyncingQueue.value = false
            }
        }
    }

    fun deleteStoredDraft(draftId: String) {
        viewModelScope.launch {
            offlineRepository.deleteDraftAndLocalArtifacts(draftId)
            if (draftId == currentDraftId) {
                startFreshReport()
            }
        }
    }

    fun runManualRetentionCleanup() {
        viewModelScope.launch {
            val report = offlineRepository.runRetentionCleanup()
            _syncNoticeBanner.value =
                "Limpeza concluída: ${report.removedMediaFiles} arquivo(s) e ${report.purgedSyncedDrafts} histórico(s) antigos removidos."
        }
    }

    private companion object {
        const val TAG = "ReportVM"
    }
}
