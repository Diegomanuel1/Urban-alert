package com.example.data.incidents

import android.content.Context
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.model.GeoBoundingBox
import com.example.core.model.IncidentStatus
import com.example.core.security.IdempotencyKeyGenerator
import com.example.core.security.InputValidator
import com.example.core.security.ValidationResult
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Repositório público e autenticado de ocorrências (/incidents).
 * Consulta apenas a área visível paginada (nunca carrega toda a cidade de uma só vez).
 */
class IncidentRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    /**
     * Observa ocorrências dentro da caixa visível do mapa (máximo 150 por consulta,
     * alinhado à regra `request.query.limit <= 200` do Firestore).
     */
    fun observeIncidentsInBounds(
        bounds: GeoBoundingBox,
        categorySlug: String? = null,
        statusFilter: IncidentStatus? = null,
    ): Flow<List<IncidentDocument>> {
        val south = minOf(bounds.south, bounds.north)
        val north = maxOf(bounds.south, bounds.north)
        val west = minOf(bounds.west, bounds.east)
        val east = maxOf(bounds.west, bounds.east)

        return db.collection(COLLECTION_INCIDENTS)
            .whereGreaterThanOrEqualTo("latitude", south)
            .whereLessThanOrEqualTo("latitude", north)
            .limit(MAX_MAP_QUERY_LIMIT)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    IncidentDocument::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                ).filter { item ->
                    val inLongitude = item.longitude in west..east
                    val matchesCategory = categorySlug == null || item.categorySlug == categorySlug
                    val matchesStatus = statusFilter == null || item.resolvedStatus() == statusFilter
                    inLongitude && matchesCategory && matchesStatus
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_INCIDENTS)
                }
                throw error
            }
    }

    fun observeIncidentById(incidentId: String): Flow<IncidentDocument?> {
        require(incidentId.isNotBlank()) { "incidentId inválido." }
        val docRef = db.collection(COLLECTION_INCIDENTS).document(incidentId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    null
                } else {
                    snapshot.toObject(
                        IncidentDocument::class.java,
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
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    /**
     * Grava uma ocorrência confirmada explicitamente pelo cidadão usando chave de idempotência.
     */
    suspend fun createIncident(incident: IncidentDocument): Result<String> {
        if (incident.authorId.isBlank()) {
            return Result.failure(IllegalStateException("É necessário entrar com sua conta Google para publicar."))
        }
        val titleCheck = InputValidator.validateIncidentTitle(incident.title)
        if (titleCheck is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(titleCheck.message))
        }
        val descCheck = InputValidator.validateIncidentDescription(incident.description)
        if (descCheck is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(descCheck.message))
        }
        val coordCheck = InputValidator.validateCoordinate(incident.coordinate())
        if (coordCheck is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(coordCheck.message))
        }
        if (PilotCityConfig.initialCategories.none { it.slug == incident.categorySlug }) {
            return Result.failure(IllegalArgumentException("Categoria urbana inválida."))
        }
        if (!IdempotencyKeyGenerator.isValid(incident.idempotencyKey)) {
            return Result.failure(IllegalArgumentException("Chave de idempotência inválida."))
        }

        val deterministicId = "inc_${incident.idempotencyKey}"
        val docRef = db.collection(COLLECTION_INCIDENTS).document(deterministicId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.success(deterministicId)
            }
            docRef.set(incident.toCreatePayload()).await()
            Result.success(deterministicId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val COLLECTION_INCIDENTS = "incidents"
        const val MAX_MAP_QUERY_LIMIT = 150L
    }
}
