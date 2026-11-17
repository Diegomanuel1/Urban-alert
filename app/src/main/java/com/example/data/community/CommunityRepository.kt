package com.example.data.community

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.model.CommunityConfirmationOutcome
import com.example.core.model.ConfirmationType
import com.example.core.model.UserIncidentConfirmations
import com.example.data.incidents.IncidentRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class IncidentConfirmationDocument(
    @DocumentId val id: String = "",
    val incidentId: String = "",
    val userId: String = "",
    val confirmationType: String = "",
    val cityCode: String = "",
    val createdAt: Timestamp? = null,
)

/**
 * Repositório comunitário (/incident_confirmations) com unicidade por usuário/ocorrência/tipo
 * e incremento transacional seguro dos contadores em /incidents (sem alterar status oficial).
 */
class CommunityRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    fun observeUserConfirmations(
        incidentId: String,
        userId: String?,
    ): Flow<UserIncidentConfirmations> {
        if (incidentId.isBlank() || userId.isNullOrBlank()) {
            return flowOf(UserIncidentConfirmations())
        }
        return db.collection(COLLECTION_CONFIRMATIONS)
            .whereEqualTo("incidentId", incidentId)
            .whereEqualTo("userId", userId)
            .limit(10L)
            .snapshots()
            .map { snapshot ->
                val docs = snapshot.toObjects(IncidentConfirmationDocument::class.java)
                UserIncidentConfirmations(
                    confirmedStillExists = docs.any { it.confirmationType == ConfirmationType.STILL_EXISTS.wireValue },
                    confirmedSuggestResolved = docs.any { it.confirmationType == ConfirmationType.SUGGEST_RESOLVED.wireValue },
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_CONFIRMATIONS)
                }
                emit(UserIncidentConfirmations())
            }
    }

    /**
     * Registra uma confirmação comunitária única via transação atômica do Firestore.
     * Nunca altera o [com.example.core.model.IncidentStatus] administrativo da ocorrência.
     */
    suspend fun submitConfirmation(
        incidentId: String,
        userId: String?,
        cityCode: String,
        type: ConfirmationType,
    ): Result<CommunityConfirmationOutcome> {
        if (userId.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Entre com sua conta Google para confirmar ocorrências."))
        }
        if (incidentId.isBlank() || cityCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Dados da ocorrência inválidos para confirmação."))
        }

        val confirmationId = deterministicConfirmationId(incidentId, userId, type)
        val incidentRef = db.collection(IncidentRepository.COLLECTION_INCIDENTS).document(incidentId)
        val confirmationRef = db.collection(COLLECTION_CONFIRMATIONS).document(confirmationId)

        return try {
            val outcome = db.runTransaction { transaction ->
                val confirmationSnap = transaction.get(confirmationRef)
                if (confirmationSnap.exists()) {
                    return@runTransaction CommunityConfirmationOutcome.AlreadyConfirmed(type)
                }
                val incidentSnap = transaction.get(incidentRef)
                if (!incidentSnap.exists()) {
                    throw IllegalStateException("Ocorrência não encontrada no servidor.")
                }

                val currentCount = when (type) {
                    ConfirmationType.STILL_EXISTS -> incidentSnap.getLong("stillExistsCount") ?: 0L
                    ConfirmationType.SUGGEST_RESOLVED -> incidentSnap.getLong("suggestResolvedCount") ?: 0L
                }

                val counterField = when (type) {
                    ConfirmationType.STILL_EXISTS -> "stillExistsCount"
                    ConfirmationType.SUGGEST_RESOLVED -> "suggestResolvedCount"
                }

                transaction.set(
                    confirmationRef,
                    mapOf(
                        "incidentId" to incidentId,
                        "userId" to userId,
                        "confirmationType" to type.wireValue,
                        "cityCode" to cityCode,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                transaction.update(
                    incidentRef,
                    mapOf(
                        counterField to currentCount + 1L,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                CommunityConfirmationOutcome.Recorded(type)
            }.await()
            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, confirmationRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_CONFIRMATIONS = "incident_confirmations"

        fun deterministicConfirmationId(
            incidentId: String,
            userId: String,
            type: ConfirmationType,
        ): String = "${incidentId}_${userId}_${type.wireValue}"
    }
}
