package com.example.core.model

import androidx.annotation.StringRes
import com.example.R

/**
 * Coordenada geográfica em WGS 84 (SRID 4326).
 */
data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Caixa delimitadora geográfica para consultas por área visível no mapa.
 */
data class GeoBoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

/**
 * Gravidade técnica da ocorrência. Independente do campo [IncidentStatus].
 */
enum class Severity(
    val wireValue: String,
    @param:StringRes val labelRes: Int,
) {
    LOW(wireValue = "low", labelRes = R.string.severity_low),
    MEDIUM(wireValue = "medium", labelRes = R.string.severity_medium),
    HIGH(wireValue = "high", labelRes = R.string.severity_high),
    CRITICAL(wireValue = "critical", labelRes = R.string.severity_critical),
}

/**
 * Status público/administrativo no servidor.
 * Nunca deve ser confundido com [SyncState] (estado local do dispositivo).
 */
enum class IncidentStatus(
    val wireValue: String,
    @param:StringRes val labelRes: Int,
) {
    OPEN(wireValue = "open", labelRes = R.string.status_open),
    UNDER_REVIEW(wireValue = "under_review", labelRes = R.string.status_under_review),
    IN_PROGRESS(wireValue = "in_progress", labelRes = R.string.status_in_progress),
    RESOLVED(wireValue = "resolved", labelRes = R.string.status_resolved),
    REJECTED(wireValue = "rejected", labelRes = R.string.status_rejected),
}

/**
 * Estado local de sincronização offline no SQLite (Room).
 */
enum class SyncState(
    val wireValue: String,
    @param:StringRes val labelRes: Int,
) {
    DRAFT(wireValue = "draft", labelRes = R.string.sync_state_draft),
    PENDING_SYNC(wireValue = "pending_sync", labelRes = R.string.sync_state_pending),
    SYNCING(wireValue = "syncing", labelRes = R.string.sync_state_syncing),
    SYNCED(wireValue = "synced", labelRes = R.string.sync_state_synced),
    SYNC_FAILED(wireValue = "sync_failed", labelRes = R.string.sync_state_failed),
}

/**
 * Tipos de confirmação comunitária permitidos (1 por tipo por usuário/ocorrência).
 * Uma confirmação comunitária não equivale a resolução oficial da prefeitura.
 */
enum class ConfirmationType(val wireValue: String) {
    STILL_EXISTS("still_exists"),
    SUGGEST_RESOLVED("suggest_resolved"),
}

/**
 * Perfis previstos no sistema (controle de acesso baseado em função).
 * Login social nunca concede permissão administrativa automaticamente.
 */
enum class UserRole(val wireValue: String) {
    CITIZEN("citizen"),
    OPERATOR("operator"),
    MUNICIPAL_MANAGER("municipal_manager"),
    SUPER_ADMIN("super_admin"),
}

/**
 * Categoria de problema urbano para classificação e filtros.
 */
data class UrbanCategory(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val defaultSeverityHint: Severity,
)

/**
 * Configuração territorial de um município suportado (expansível além de Salvador).
 */
data class CityPilotConfig(
    val cityCode: String,
    val displayName: String,
    val stateCode: String,
    val countryCode: String,
    val center: GeoCoordinate,
    val initialBounds: GeoBoundingBox,
    val defaultZoom: Float,
)

/**
 * Estrutura de resposta da análise multimodal de IA antes da confirmação do cidadão.
 */
data class AiAnalysisDraft(
    val suggestedCategorySlug: String?,
    val suggestedSeverity: Severity?,
    val confidence: Double,
    val technicalDescription: String,
    val visualEvidence: List<String>,
    val warnings: List<String>,
    val modelName: String,
    val modelVersion: String,
)

/**
 * Estado dos marcos do roadmap do Urban Alert AI.
 */
data class RoadmapMilestone(
    val code: String,
    val title: String,
    val summary: String,
    val isCurrent: Boolean = false,
    val isCompleted: Boolean = false,
)

/**
 * Órgão público ou concessionária responsável por atender determinadas categorias urbanas.
 */
data class PublicAgency(
    val id: String,
    val cityCode: String,
    val acronym: String,
    val name: String,
    val competenceSummary: String,
    val categorySlugs: List<String>,
    val serviceAreaLabel: String,
    val officialChannelLabel: String,
    val officialPortalUrl: String? = null,
    val officialPhone: String? = null,
    val lastVerifiedAtLabel: String,
    val active: Boolean = true,
)

/**
 * Estado de confirmações comunitárias do cidadão autenticado para uma ocorrência.
 */
data class UserIncidentConfirmations(
    val confirmedStillExists: Boolean = false,
    val confirmedSuggestResolved: Boolean = false,
) {
    fun hasConfirmed(type: ConfirmationType): Boolean = when (type) {
        ConfirmationType.STILL_EXISTS -> confirmedStillExists
        ConfirmationType.SUGGEST_RESOLVED -> confirmedSuggestResolved
    }
}

/**
 * Resultado de uma tentativa de confirmação comunitária.
 */
sealed interface CommunityConfirmationOutcome {
    data class Recorded(val type: ConfirmationType) : CommunityConfirmationOutcome
    data class AlreadyConfirmed(val type: ConfirmationType) : CommunityConfirmationOutcome
}

/**
 * Conta administrativa verificada na coleção protegida /admins/{uid}.
 */
data class AdminAccountInfo(
    val uid: String,
    val role: UserRole,
    val organizationId: String,
)

/**
 * Registro imutável de transição oficial de status de ocorrência.
 */
data class IncidentStatusHistoryItem(
    val id: String,
    val incidentId: String,
    val previousStatus: IncidentStatus,
    val newStatus: IncidentStatus,
    val reason: String,
    val changedByUid: String,
    val createdAtMillis: Long,
)

/**
 * Entrada imutável de auditoria administrativa (/audit_logs).
 */
data class AuditLogEntry(
    val id: String,
    val action: String,
    val actorUid: String,
    val targetType: String,
    val targetId: String,
    val summary: String,
    val createdAtMillis: Long,
)

/**
 * Evento não sensível de telemetria da IA (/ai_telemetry).
 */
data class AiTelemetryEntry(
    val id: String,
    val mode: String,
    val modelName: String,
    val confidence: Double?,
    val latencyMs: Long,
    val createdAtMillis: Long,
)

/**
 * Resumo operacional consolidado por cidade para o Painel Administrativo.
 */
data class CityOperationsReport(
    val totalIncidents: Int,
    val byStatus: Map<IncidentStatus, Int>,
    val bySeverity: Map<Severity, Int>,
    val byCategorySlug: Map<String, Int>,
    val totalStillExistsConfirmations: Long,
    val totalSuggestResolvedConfirmations: Long,
    val aiSuggestedCount: Int,
    val manualFallbackCount: Int,
    val aiErrorCount: Int,
    val averageAiConfidence: Double?,
)


