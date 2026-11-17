package com.example.core.agent

import com.example.core.config.PilotCityConfig
import com.example.core.model.AiAnalysisDraft
import com.example.core.model.GeoCoordinate
import com.example.core.model.Severity
import com.example.core.security.IdempotencyKeyGenerator
import java.util.UUID

enum class AgentStep {
    COLLECTING_INPUT,
    ANALYZING_AI,
    CITIZEN_REVIEW,
    PUBLISHING,
    QUEUED_OFFLINE,
    PUBLISHED,
}

enum class AgentMessageRole {
    AGENT,
    CITIZEN,
    SYSTEM_NOTICE,
}

data class AgentChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: AgentMessageRole,
    val text: String,
    val imagePath: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)

/**
 * Rascunho guiado pelo agente antes da revisão e confirmação explícita do cidadão.
 */
data class CitizenIncidentDraft(
    val idempotencyKey: String = IdempotencyKeyGenerator.generate(),
    val cityCode: String = PilotCityConfig.salvador.cityCode,
    val sanitizedPhotoPath: String? = null,
    val sanitizedPhotoBytes: Long = 0L,
    val sanitizedPhotoSha256: String? = null,
    val strippedSensitiveExif: Boolean = false,
    val userNarrative: String = "",
    val coordinate: GeoCoordinate? = null,
    val addressLabel: String? = null,
    val aiDraft: AiAnalysisDraft? = null,
    val aiFallbackReason: String? = null,
    val selectedCategorySlug: String = PilotCityConfig.initialCategories.first().slug,
    val selectedSeverity: Severity = Severity.MEDIUM,
    val title: String = "",
    val description: String = "",
)

data class AiIncidentSuggestion(
    val title: String,
    val draft: AiAnalysisDraft,
)
