package com.example.core.agent

import android.content.Context
import android.net.Uri
import com.example.core.config.PilotCityConfig
import com.example.core.model.GeoCoordinate
import com.example.core.model.Severity
import com.example.core.security.MediaSanitizer

/**
 * Orquestrador conversacional que coordena coleta de mídia/texto, localização,
 * análise multimodal Gemini com fallback manual e revisão cidadã obrigatória.
 */
class AgentRunner(
    private val memoryManager: MemoryManager = MemoryManager(),
    private val toolRegistry: ToolRegistry = ToolRegistry(),
) {

    val messages = memoryManager.messages
    val draft = memoryManager.draft
    val step = memoryManager.step

    init {
        startNewSession()
    }

    fun startNewSession() {
        val oldPath = memoryManager.draft.value.sanitizedPhotoPath
        if (!oldPath.isNullOrBlank()) {
            MediaSanitizer.deleteLocalMedia(oldPath)
        }
        memoryManager.resetSession(
            welcomeMessage = DEFAULT_WELCOME,
        )
    }

    fun restoreSavedSession(
        draft: CitizenIncidentDraft,
        messages: List<AgentChatMessage>,
    ) {
        memoryManager.restoreSession(
            draft = draft,
            messages = messages,
            fallbackWelcome = DEFAULT_WELCOME,
        )
    }

    suspend fun attachSanitizedPhoto(context: Context, uri: Uri): Result<Unit> {
        val result = toolRegistry.sanitizePhoto(context, uri)
        result.onSuccess { sanitized ->
            val previousPath = memoryManager.draft.value.sanitizedPhotoPath
            if (!previousPath.isNullOrBlank() && previousPath != sanitized.filePath) {
                MediaSanitizer.deleteLocalMedia(previousPath)
            }
            memoryManager.updateDraft { current ->
                current.copy(
                    sanitizedPhotoPath = sanitized.filePath,
                    sanitizedPhotoBytes = sanitized.byteSize,
                    sanitizedPhotoSha256 = sanitized.sha256Hex,
                    strippedSensitiveExif = sanitized.strippedSensitiveExif,
                )
            }
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.CITIZEN,
                    text = "Foto anexada e sanitizada localmente (EXIF/GPS removidos · ${sanitized.byteSize / 1024} KB).",
                    imagePath = sanitized.filePath,
                ),
            )
            promptNextMissingField()
        }.onFailure { err ->
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.SYSTEM_NOTICE,
                    text = err.localizedMessage ?: "Não foi possível processar a imagem selecionada.",
                ),
            )
        }
        return result.map { }
    }

    fun submitCitizenDescription(rawText: String) {
        val cleaned = rawText.trim()
        if (cleaned.isBlank()) return
        memoryManager.appendMessage(
            AgentChatMessage(
                role = AgentMessageRole.CITIZEN,
                text = cleaned,
            ),
        )
        memoryManager.updateDraft { current ->
            val combined = listOf(current.userNarrative, cleaned)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            current.copy(userNarrative = combined)
        }
        promptNextMissingField()
    }

    suspend fun captureCurrentGpsLocation(context: Context): Result<Unit> {
        val result = toolRegistry.captureDeviceLocation(context)
        result.onSuccess { captured ->
            setDraftLocation(
                coordinate = captured.coordinate,
                addressLabel = captured.addressLabel ?: "Localização atual via GPS",
            )
        }.onFailure { err ->
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.SYSTEM_NOTICE,
                    text = err.localizedMessage ?: "GPS indisponível no momento.",
                ),
            )
        }
        return result.map { }
    }

    suspend fun searchAndSetAddress(context: Context, query: String): Result<Unit> {
        val result = toolRegistry.resolveAddressInSalvador(context, query)
        result.onSuccess { captured ->
            setDraftLocation(
                coordinate = captured.coordinate,
                addressLabel = captured.addressLabel,
            )
        }.onFailure { err ->
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.SYSTEM_NOTICE,
                    text = err.localizedMessage ?: "Endereço não localizado.",
                ),
            )
        }
        return result.map { }
    }

    fun setDraftLocation(coordinate: GeoCoordinate, addressLabel: String?) {
        val label = addressLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: "%.5f, %.5f".format(coordinate.latitude, coordinate.longitude)
        memoryManager.updateDraft { current ->
            current.copy(
                coordinate = coordinate,
                addressLabel = label,
            )
        }
        memoryManager.appendMessage(
            AgentChatMessage(
                role = AgentMessageRole.CITIZEN,
                text = "Local definido: $label",
            ),
        )
        promptNextMissingField()
    }

    suspend fun runAiAnalysisOrFallback() {
        val current = memoryManager.draft.value
        val narrative = memoryManager.recentNarrativeSummary()
        if (current.sanitizedPhotoPath.isNullOrBlank() && narrative.length < 4) {
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.AGENT,
                    text = "Antes de analisar, envie uma foto ou escreva uma breve descrição do problema.",
                ),
            )
            return
        }

        memoryManager.setStep(AgentStep.ANALYZING_AI)
        memoryManager.appendMessage(
            AgentChatMessage(
                role = AgentMessageRole.AGENT,
                text = "Analisando os indícios com Gemini multimodal para sugerir categoria, gravidade e descrição...",
            ),
        )

        val aiResult = toolRegistry.analyzeWithAi(
            sanitizedPhotoPath = current.sanitizedPhotoPath,
            citizenText = narrative,
            coordinate = current.coordinate,
            addressLabel = current.addressLabel,
        )

        aiResult.onSuccess { suggestion ->
            val slug = suggestion.draft.suggestedCategorySlug
                ?: PilotCityConfig.initialCategories.first().slug
            val severity = suggestion.draft.suggestedSeverity ?: Severity.MEDIUM
            memoryManager.updateDraft { draft ->
                draft.copy(
                    aiDraft = suggestion.draft,
                    aiFallbackReason = null,
                    selectedCategorySlug = slug,
                    selectedSeverity = severity,
                    title = suggestion.title,
                    description = suggestion.draft.technicalDescription,
                )
            }
            val categoryTitle = PilotCityConfig.initialCategories
                .firstOrNull { it.slug == slug }
                ?.title
                ?: slug
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.AGENT,
                    text = "Sugestão pronta ($categoryTitle · confiança ${(suggestion.draft.confidence * 100).toInt()}%). Revise e edite tudo na próxima etapa antes de publicar.",
                ),
            )
            memoryManager.setStep(AgentStep.CITIZEN_REVIEW)
        }.onFailure { err ->
            val reason = err.localizedMessage
                ?: "IA indisponível no momento. Preencha os campos manualmente."
            openManualReview(fallbackReason = reason)
        }
    }

    fun openManualReview(fallbackReason: String? = null) {
        memoryManager.updateDraft { current ->
            val narrative = memoryManager.recentNarrativeSummary()
            val defaultTitle = current.title.ifBlank {
                narrative.lineSequence().firstOrNull()?.take(80).orEmpty()
            }
            val defaultDescription = current.description.ifBlank {
                narrative.take(500)
            }
            current.copy(
                aiFallbackReason = fallbackReason,
                title = defaultTitle,
                description = defaultDescription,
                coordinate = current.coordinate ?: PilotCityConfig.salvador.center,
                addressLabel = current.addressLabel ?: "Salvador, BA (ajuste se necessário)",
            )
        }
        if (!fallbackReason.isNullOrBlank()) {
            memoryManager.appendMessage(
                AgentChatMessage(
                    role = AgentMessageRole.SYSTEM_NOTICE,
                    text = "Modo manual ativado: $fallbackReason",
                ),
            )
        }
        memoryManager.setStep(AgentStep.CITIZEN_REVIEW)
    }

    fun returnToChat() {
        memoryManager.setStep(AgentStep.COLLECTING_INPUT)
    }

    fun updateReviewFields(
        categorySlug: String? = null,
        severity: Severity? = null,
        title: String? = null,
        description: String? = null,
        addressLabel: String? = null,
    ) {
        memoryManager.updateDraft { current ->
            current.copy(
                selectedCategorySlug = categorySlug ?: current.selectedCategorySlug,
                selectedSeverity = severity ?: current.selectedSeverity,
                title = title ?: current.title,
                description = description ?: current.description,
                addressLabel = addressLabel ?: current.addressLabel,
            )
        }
    }

    fun validateCurrentDraft(): String? = toolRegistry.validateForPublication(memoryManager.draft.value)

    fun setPublishing() {
        memoryManager.setStep(AgentStep.PUBLISHING)
    }

    fun markPublished() {
        val path = memoryManager.draft.value.sanitizedPhotoPath
        if (!path.isNullOrBlank()) {
            MediaSanitizer.deleteLocalMedia(path)
        }
        memoryManager.setStep(AgentStep.PUBLISHED)
    }

    fun markQueuedOffline() {
        memoryManager.setStep(AgentStep.QUEUED_OFFLINE)
    }

    fun restoreReviewOnPublishError() {
        memoryManager.setStep(AgentStep.CITIZEN_REVIEW)
    }

    private fun promptNextMissingField() {
        val current = memoryManager.draft.value
        val hasMediaOrText = !current.sanitizedPhotoPath.isNullOrBlank() ||
            memoryManager.recentNarrativeSummary().length >= 4
        val hasLocation = current.coordinate != null

        val guidance = when {
            !hasMediaOrText ->
                "Anexe uma foto ou descreva o problema para que eu possa sugerir a classificação."
            !hasLocation ->
                "Ótimo! Agora toque em 'Usar GPS atual', busque um bairro/rua ou use o ponto do mapa."
            else ->
                "Temos tudo pronto! Toque em 'Analisar com IA' para gerar a prévia ou vá direto para a revisão manual."
        }
        memoryManager.appendMessage(
            AgentChatMessage(
                role = AgentMessageRole.AGENT,
                text = guidance,
            ),
        )
    }

    private companion object {
        const val DEFAULT_WELCOME =
            "Olá! Sou o assistente do Urban Alert AI. Envie uma foto do problema urbano ou descreva o que aconteceu (por texto ou ditado do teclado) e informe o local."
    }
}
