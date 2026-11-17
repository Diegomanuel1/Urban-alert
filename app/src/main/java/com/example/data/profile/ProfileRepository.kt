package com.example.data.profile

import android.content.Context
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Repositório de perfis em /profiles/{userId} utilizando exclusivamente
 * o banco nomeado configurado em R.string.firestore_database_id.
 */
class ProfileRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Usuário precisa estar autenticado com Google.")

    fun observeProfile(userId: String): Flow<UserProfile?> {
        require(userId.isNotBlank()) { "userId não pode estar vazio." }
        val docRef = db.collection(COLLECTION_PROFILES).document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    null
                } else {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    /**
     * Garante que o documento /profiles/{uid} exista após o login Google.
     * Sempre cria como papel "citizen" (regras bloqueiam autoescalonamento).
     */
    suspend fun ensureCitizenProfile(user: FirebaseUser): Result<Unit> {
        val docRef = db.collection(COLLECTION_PROFILES).document(user.uid)
        return try {
            val uid = requireUserId()
            check(uid == user.uid) { "UID autenticado divergiu durante criação de perfil." }
            val existingSnapshot = docRef.get().await()
            if (!existingSnapshot.exists()) {
                val safeName = user.displayName
                    ?.trim()
                    ?.take(80)
                    ?.takeIf { it.isNotBlank() }
                    ?: "Cidadão Urban Alert"
                val newProfile = UserProfile(
                    userId = uid,
                    displayName = safeName,
                    email = user.email,
                    photoUrl = user.photoUrl?.toString(),
                    homeCityCode = PilotCityConfig.salvador.cityCode,
                    role = UserRole.CITIZEN.wireValue,
                    acceptedPrivacyVersion = UserProfile.CURRENT_PRIVACY_VERSION,
                )
                docRef.set(newProfile.toCreatePayload()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Atualização parcial preservando userId, role e createdAt imutáveis.
     */
    suspend fun updateProfile(
        userId: String,
        displayName: String,
        homeCityCode: String,
        acceptedPrivacyVersion: String = UserProfile.CURRENT_PRIVACY_VERSION,
    ): Result<Unit> {
        val docRef = db.collection(COLLECTION_PROFILES).document(userId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Recusada tentativa de editar perfil de outro usuário." }

            val cleanName = displayName.trim()
            require(cleanName.length in 1..80) {
                "O nome de exibição deve ter entre 1 e 80 caracteres."
            }
            val cleanCity = homeCityCode.trim()
            require(cleanCity.length in 2..32) {
                "Código de cidade inválido."
            }
            val cleanPrivacy = acceptedPrivacyVersion.trim()
            require(cleanPrivacy.length in 1..24) {
                "Versão de política de privacidade inválida."
            }

            docRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "homeCityCode" to cleanCity,
                    "acceptedPrivacyVersion" to cleanPrivacy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Exclui o documento de perfil do cidadão no Firestore (fluxo de exclusão LGPD).
     */
    suspend fun deleteProfile(userId: String): Result<Unit> {
        val docRef = db.collection(COLLECTION_PROFILES).document(userId)
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Recusada tentativa de excluir perfil de outro usuário." }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_PROFILES = "profiles"
    }
}
