package com.example.data.profile

import androidx.annotation.Keep
import com.example.core.config.PilotCityConfig
import com.example.core.model.UserRole
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Modelo Firestore do perfil do cidadão em /profiles/{userId}.
 * Mantém construtor sem argumentos, @DocumentId e nenhum Boolean prefixado por "is".
 */
@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val homeCityCode: String = PilotCityConfig.salvador.cityCode,
    val role: String = UserRole.CITIZEN.wireValue,
    val acceptedPrivacyVersion: String = CURRENT_PRIVACY_VERSION,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun resolvedRole(): UserRole =
        UserRole.entries.firstOrNull { it.wireValue == role } ?: UserRole.CITIZEN

    fun toCreatePayload(): Map<String, Any> {
        val raw = mapOf(
            "userId" to userId,
            "displayName" to displayName.trim().take(80).ifBlank { "Cidadão" },
            "email" to email?.trim()?.take(160)?.takeIf { it.isNotBlank() },
            "photoUrl" to photoUrl?.trim()?.take(500)?.takeIf { it.isNotBlank() },
            "homeCityCode" to homeCityCode,
            "role" to UserRole.CITIZEN.wireValue,
            "acceptedPrivacyVersion" to acceptedPrivacyVersion,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    companion object {
        const val CURRENT_PRIVACY_VERSION = "lgpd-v1"
    }
}
