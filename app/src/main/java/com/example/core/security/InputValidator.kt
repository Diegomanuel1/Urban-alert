package com.example.core.security

import com.example.core.model.GeoCoordinate

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val message: String) : ValidationResult
}

/**
 * Validadores de entrada e proteções contra payloads maliciosos ou prompt injection.
 */
object InputValidator {

    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 1000
    const val MAX_DISPLAY_NAME_LENGTH = 80
    const val MAX_PROMPT_INPUT_LENGTH = 600

    private val controlCharsRegex = Regex("[\\p{Cntrl}&&[^\n\t]]")
    private val promptInjectionPatterns = listOf(
        Regex("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
        Regex("(?i)disregard\\s+(all\\s+)?prior\\s+rules"),
        Regex("(?i)system\\s*:\\s*"),
        Regex("(?i)assistant\\s*:\\s*"),
        Regex("(?i)you\\s+are\\s+now\\s+"),
    )

    fun validateDisplayName(name: String): ValidationResult {
        val cleaned = name.trim()
        return when {
            cleaned.isEmpty() -> ValidationResult.Invalid("Informe seu nome de exibição.")
            cleaned.length > MAX_DISPLAY_NAME_LENGTH ->
                ValidationResult.Invalid("Nome deve ter no máximo $MAX_DISPLAY_NAME_LENGTH caracteres.")
            else -> ValidationResult.Valid
        }
    }

    fun validateIncidentTitle(title: String): ValidationResult {
        val cleaned = title.trim()
        return when {
            cleaned.length < 4 ->
                ValidationResult.Invalid("Descreva um título curto com pelo menos 4 caracteres.")
            cleaned.length > MAX_TITLE_LENGTH ->
                ValidationResult.Invalid("O título pode ter no máximo $MAX_TITLE_LENGTH caracteres.")
            else -> ValidationResult.Valid
        }
    }

    fun validateIncidentDescription(description: String): ValidationResult {
        val cleaned = description.trim()
        return when {
            cleaned.length < 10 ->
                ValidationResult.Invalid("Forneça pelo menos 10 caracteres descrevendo o problema.")
            cleaned.length > MAX_DESCRIPTION_LENGTH ->
                ValidationResult.Invalid("A descrição pode ter no máximo $MAX_DESCRIPTION_LENGTH caracteres.")
            else -> ValidationResult.Valid
        }
    }

    fun validateCoordinate(coordinate: GeoCoordinate): ValidationResult {
        val lat = coordinate.latitude
        val lng = coordinate.longitude
        return when {
            lat.isNaN() || lng.isNaN() ->
                ValidationResult.Invalid("Coordenadas geográficas inválidas.")
            lat !in -90.0..90.0 || lng !in -180.0..180.0 ->
                ValidationResult.Invalid("Coordenadas fora dos limites geográficos WGS 84.")
            lat == 0.0 && lng == 0.0 ->
                ValidationResult.Invalid("Coordenada nula (0, 0) não representa um local urbano válido.")
            else -> ValidationResult.Valid
        }
    }

    /**
     * Limpa caracteres de controle e neutraliza tentativas óbvias de prompt injection
     * antes de enviar texto livre do cidadão para modelos de IA.
     */
    fun sanitizeCitizenTextForAi(rawText: String): String {
        var sanitized = rawText
            .replace(controlCharsRegex, " ")
            .trim()
            .take(MAX_PROMPT_INPUT_LENGTH)

        promptInjectionPatterns.forEach { pattern ->
            sanitized = sanitized.replace(pattern, "[texto-removido]")
        }
        return sanitized
    }
}
