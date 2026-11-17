package com.example.data.incidents

import androidx.annotation.Keep
import com.example.core.config.PilotCityConfig
import com.example.core.model.GeoCoordinate
import com.example.core.model.IncidentStatus
import com.example.core.model.Severity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import java.util.concurrent.TimeUnit

/**
 * Representação pública de uma ocorrência no Firestore (/incidents/{incidentId}).
 * Nunca contém PII (e-mail, telefone) nem caminhos de mídia original privada.
 */
@Keep
@IgnoreExtraProperties
data class IncidentDocument(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val cityCode: String = PilotCityConfig.salvador.cityCode,
    val categorySlug: String = "",
    val title: String = "",
    val description: String = "",
    val severity: String = Severity.MEDIUM.wireValue,
    val status: String = IncidentStatus.OPEN.wireValue,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val addressLabel: String? = null,
    val publicImageUrl: String? = null,
    val stillExistsCount: Long = 0L,
    val suggestResolvedCount: Long = 0L,
    val idempotencyKey: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun coordinate(): GeoCoordinate = GeoCoordinate(latitude = latitude, longitude = longitude)

    fun resolvedSeverity(): Severity =
        Severity.entries.firstOrNull { it.wireValue == severity } ?: Severity.MEDIUM

    fun resolvedStatus(): IncidentStatus =
        IncidentStatus.entries.firstOrNull { it.wireValue == status } ?: IncidentStatus.OPEN

    fun categoryTitle(): String =
        PilotCityConfig.initialCategories
            .firstOrNull { it.slug == categorySlug }
            ?.title
            ?: categorySlug

    fun elapsedLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val createdMillis = createdAt?.toDate()?.time ?: return "agora mesmo"
        val diffMillis = (nowMillis - createdMillis).coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
        return when {
            minutes < 1 -> "agora mesmo"
            minutes < 60 -> "há $minutes min"
            hours < 24 -> "há $hours h"
            else -> "há $days d"
        }
    }

    fun toCreatePayload(): Map<String, Any> {
        val raw = mapOf(
            "authorId" to authorId,
            "cityCode" to cityCode,
            "categorySlug" to categorySlug,
            "title" to title.trim(),
            "description" to description.trim(),
            "severity" to resolvedSeverity().wireValue,
            "status" to IncidentStatus.OPEN.wireValue,
            "latitude" to latitude,
            "longitude" to longitude,
            "addressLabel" to addressLabel?.trim()?.take(160)?.takeIf { it.isNotBlank() },
            "publicImageUrl" to publicImageUrl?.trim()?.take(500)?.takeIf { it.isNotBlank() },
            "stillExistsCount" to 0L,
            "suggestResolvedCount" to 0L,
            "idempotencyKey" to idempotencyKey,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}
