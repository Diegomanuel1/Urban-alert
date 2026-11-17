package com.example.data.admin

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.model.AdminAccountInfo
import com.example.core.model.AiTelemetryEntry
import com.example.core.model.AuditLogEntry
import com.example.core.model.CityOperationsReport
import com.example.core.model.IncidentStatus
import com.example.core.model.PublicAgency
import com.example.core.model.Severity
import com.example.core.model.UrbanCategory
import com.example.core.model.UserRole
import com.example.data.agencies.AgencyDocument
import com.example.data.agencies.AgencyRepository
import com.example.data.incidents.IncidentDocument
import com.example.data.incidents.IncidentRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Keep
@IgnoreExtraProperties
data class AdminDoc(
    @DocumentId val id: String = "",
    val role: String = "operator",
    val organizationId: String = PilotCityConfig.salvador.cityCode,
) {
    fun toDomain(uid: String): AdminAccountInfo = AdminAccountInfo(
        uid = uid,
        role = when (role) {
            UserRole.OPERATOR.wireValue -> UserRole.OPERATOR
            UserRole.MUNICIPAL_MANAGER.wireValue -> UserRole.MUNICIPAL_MANAGER
            UserRole.SUPER_ADMIN.wireValue -> UserRole.SUPER_ADMIN
            else -> UserRole.OPERATOR
        },
        organizationId = organizationId,
    )
}

@Keep
@IgnoreExtraProperties
data class CategoryDoc(
    @DocumentId val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val defaultSeverity: String = Severity.MEDIUM.wireValue,
    val active: Boolean = true,
) {
    fun toDomain(): UrbanCategory = UrbanCategory(
        id = id.ifBlank { "cat_$slug" },
        slug = slug,
        title = title,
        description = description,
        defaultSeverityHint = Severity.entries.firstOrNull { it.wireValue == defaultSeverity }
            ?: Severity.MEDIUM,
    )
}

@Keep
@IgnoreExtraProperties
data class AuditLogDoc(
    @DocumentId val id: String = "",
    val action: String = "",
    val actorUid: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val summary: String = "",
    val createdAt: Timestamp? = null,
) {
    fun toDomain(): AuditLogEntry = AuditLogEntry(
        id = id,
        action = action,
        actorUid = actorUid,
        targetType = targetType,
        targetId = targetId,
        summary = summary,
        createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    )
}

@Keep
@IgnoreExtraProperties
data class AiTelemetryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = "",
    val modelName: String = "",
    val confidence: Double? = null,
    val latencyMs: Long = 0L,
    val createdAt: Timestamp? = null,
) {
    fun toDomain(): AiTelemetryEntry = AiTelemetryEntry(
        id = id,
        mode = mode,
        modelName = modelName,
        confidence = confidence,
        latencyMs = latencyMs,
        createdAtMillis = createdAt?.toDate()?.time ?: 0L,
    )
}

/**
 * Repositório de operações administrativas protegidas pelo nó /admins/{uid}
 * e regras imutáveis de histórico e auditoria no Firestore.
 */
class AdminRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    fun observeAdminAccess(uid: String?): Flow<AdminAccountInfo?> {
        if (uid.isNullOrBlank()) return flowOf(null)
        val docRef = db.collection(COLLECTION_ADMINS).document(uid)
        return docRef.snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(AdminDoc::class.java)?.toDomain(uid)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(null)
            }
    }

    fun observeIncidentsForTriage(cityCode: String): Flow<List<IncidentDocument>> {
        return db.collection(IncidentRepository.COLLECTION_INCIDENTS)
            .whereEqualTo("cityCode", cityCode)
            .limit(120L)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(IncidentDocument::class.java)
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, IncidentRepository.COLLECTION_INCIDENTS)
                emit(emptyList())
            }
    }

    fun observeCategoriesCatalog(): Flow<List<UrbanCategory>> {
        return db.collection(COLLECTION_CATEGORIES)
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(CategoryDoc::class.java)
                    .filter { it.active }
                    .map { it.toDomain() }
                if (remote.isEmpty()) PilotCityConfig.initialCategories
                else {
                    val bySlug = LinkedHashMap<String, UrbanCategory>()
                    PilotCityConfig.initialCategories.forEach { bySlug[it.slug] = it }
                    remote.forEach { bySlug[it.slug] = it }
                    bySlug.values.toList()
                }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, COLLECTION_CATEGORIES)
                emit(PilotCityConfig.initialCategories)
            }
    }

    fun observeAgenciesCatalog(cityCode: String): Flow<List<PublicAgency>> {
        return db.collection(AgencyRepository.COLLECTION_AGENCIES)
            .whereEqualTo("cityCode", cityCode)
            .limit(50L)
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toObjects(AgencyDocument::class.java).map { it.toDomain() }
                AgencyRepository.mergeRemoteAndBaseline(
                    remote = remote,
                    baseline = PilotCityConfig.salvadorAgencies.filter { it.cityCode == cityCode },
                )
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, AgencyRepository.COLLECTION_AGENCIES)
                emit(PilotCityConfig.salvadorAgencies.filter { it.cityCode == cityCode })
            }
    }

    fun observeAuditLogs(): Flow<List<AuditLogEntry>> {
        return db.collection(COLLECTION_AUDIT_LOGS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50L)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AuditLogDoc::class.java).map { it.toDomain() }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, COLLECTION_AUDIT_LOGS)
                emit(emptyList())
            }
    }

    fun observeAiTelemetry(): Flow<List<AiTelemetryEntry>> {
        return db.collection(COLLECTION_AI_TELEMETRY)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100L)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AiTelemetryDoc::class.java).map { it.toDomain() }
            }
            .catch { error ->
                handleStreamError(error, OperationType.LIST, COLLECTION_AI_TELEMETRY)
                emit(emptyList())
            }
    }

    /**
     * Atualiza oficialmente o status da ocorrência e registra simultaneamente
     * o histórico imutável (/incident_status_history) e o log de auditoria (/audit_logs).
     */
    suspend fun transitionIncidentStatus(
        incident: IncidentDocument,
        newStatus: IncidentStatus,
        reason: String,
        adminUid: String,
    ): Result<Unit> {
        val previous = incident.resolvedStatus()
        if (previous == newStatus) {
            return Result.failure(IllegalArgumentException("Selecione um status diferente do atual."))
        }
        val cleanReason = reason.trim()
        if (cleanReason.length < 3 || cleanReason.length > 300) {
            return Result.failure(IllegalArgumentException("Informe uma justificativa entre 3 e 300 caracteres."))
        }

        val ts = FieldValue.serverTimestamp()
        val historyId = "hist_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}"

        val batch = db.batch()
        batch.update(
            db.collection(IncidentRepository.COLLECTION_INCIDENTS).document(incident.id),
            mapOf(
                "status" to newStatus.wireValue,
                "updatedAt" to ts,
            ),
        )
        batch.set(
            db.collection(COLLECTION_STATUS_HISTORY).document(historyId),
            mapOf(
                "incidentId" to incident.id,
                "previousStatus" to previous.wireValue,
                "newStatus" to newStatus.wireValue,
                "reason" to cleanReason,
                "changedByUid" to adminUid,
                "createdAt" to ts,
            ),
        )
        batch.set(
            db.collection(COLLECTION_AUDIT_LOGS).document(logId),
            mapOf(
                "action" to "incident_status_updated",
                "actorUid" to adminUid,
                "targetType" to "incident",
                "targetId" to incident.id,
                "summary" to "Status de '${incident.title.take(50)}' alterado de ${previous.wireValue} para ${newStatus.wireValue}: ${cleanReason.take(120)}",
                "createdAt" to ts,
            ),
        )

        return try {
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, IncidentRepository.COLLECTION_INCIDENTS)
            Result.failure(e)
        }
    }

    suspend fun saveCategory(
        slug: String,
        title: String,
        description: String,
        defaultSeverity: Severity,
        adminUid: String,
    ): Result<Unit> {
        val cleanSlug = slug.trim().lowercase().replace(" ", "-")
        val cleanTitle = title.trim()
        val cleanDesc = description.trim()
        if (!cleanSlug.matches(Regex("^[a-z0-9\\-]{2,64}$"))) {
            return Result.failure(IllegalArgumentException("Slug inválido (use letras minúsculas, números e hífen)."))
        }
        if (cleanTitle.length < 2 || cleanDesc.length < 4) {
            return Result.failure(IllegalArgumentException("Preencha título e descrição válidos."))
        }

        val docId = "cat_${cleanSlug.replace("-", "_")}"
        val catRef = db.collection(COLLECTION_CATEGORIES).document(docId)
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val ts = FieldValue.serverTimestamp()

        return try {
            val exists = catRef.get().await().exists()
            val batch = db.batch()
            if (exists) {
                batch.update(
                    catRef,
                    mapOf(
                        "slug" to cleanSlug,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "defaultSeverity" to defaultSeverity.wireValue,
                        "active" to true,
                        "updatedAt" to ts,
                    ),
                )
            } else {
                batch.set(
                    catRef,
                    mapOf(
                        "slug" to cleanSlug,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "defaultSeverity" to defaultSeverity.wireValue,
                        "active" to true,
                        "createdAt" to ts,
                        "updatedAt" to ts,
                    ),
                )
            }
            batch.set(
                db.collection(COLLECTION_AUDIT_LOGS).document(logId),
                mapOf(
                    "action" to "category_saved",
                    "actorUid" to adminUid,
                    "targetType" to "category",
                    "targetId" to docId,
                    "summary" to "Categoria '$cleanTitle' ($cleanSlug) salva no catálogo.",
                    "createdAt" to ts,
                ),
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, catRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveAgency(
        agency: PublicAgency,
        adminUid: String,
    ): Result<Unit> {
        if (agency.acronym.trim().length < 2 || agency.name.trim().length < 3 || agency.competenceSummary.trim().length < 10) {
            return Result.failure(IllegalArgumentException("Preencha sigla, nome e resumo de competência válidos."))
        }
        val docId = agency.id.ifBlank {
            "agency_${agency.acronym.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
        }
        val agencyRef = db.collection(AgencyRepository.COLLECTION_AGENCIES).document(docId)
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val ts = FieldValue.serverTimestamp()

        return try {
            val exists = agencyRef.get().await().exists()
            val batch = db.batch()
            val common = buildMap<String, Any> {
                put("cityCode", agency.cityCode)
                put("acronym", agency.acronym.trim())
                put("name", agency.name.trim())
                put("competenceSummary", agency.competenceSummary.trim())
                put("categorySlugs", agency.categorySlugs.ifEmpty { listOf("buracos-em-vias") })
                put("serviceAreaLabel", agency.serviceAreaLabel.trim())
                put("officialChannelLabel", agency.officialChannelLabel.trim())
                agency.officialPortalUrl?.trim()?.takeIf { it.isNotBlank() }?.let { put("officialPortalUrl", it) }
                agency.officialPhone?.trim()?.takeIf { it.isNotBlank() }?.let { put("officialPhone", it) }
                put("lastVerifiedAtLabel", agency.lastVerifiedAtLabel)
                put("active", agency.active)
                put("updatedAt", ts)
            }
            if (exists) {
                batch.update(agencyRef, common)
            } else {
                batch.set(agencyRef, common + ("createdAt" to ts))
            }
            batch.set(
                db.collection(COLLECTION_AUDIT_LOGS).document(logId),
                mapOf(
                    "action" to "agency_saved",
                    "actorUid" to adminUid,
                    "targetType" to "agency",
                    "targetId" to docId,
                    "summary" to "Órgão '${agency.acronym.trim()}' atualizado para ${agency.cityCode}.",
                    "createdAt" to ts,
                ),
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, agencyRef.path)
            Result.failure(e)
        }
    }

    private fun handleStreamError(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    companion object {
        const val COLLECTION_ADMINS = "admins"
        const val COLLECTION_CATEGORIES = "categories"
        const val COLLECTION_STATUS_HISTORY = "incident_status_history"
        const val COLLECTION_AUDIT_LOGS = "audit_logs"
        const val COLLECTION_AI_TELEMETRY = "ai_telemetry"

        fun buildReport(
            incidents: List<IncidentDocument>,
            telemetry: List<AiTelemetryEntry>,
        ): CityOperationsReport {
            val byStatus = IncidentStatus.entries.associateWith { status ->
                incidents.count { it.resolvedStatus() == status }
            }
            val bySeverity = Severity.entries.associateWith { sev ->
                incidents.count { it.resolvedSeverity() == sev }
            }
            val byCategory = incidents.groupingBy { it.categorySlug }.eachCount()
            val confidences = telemetry.mapNotNull { it.confidence }
            return CityOperationsReport(
                totalIncidents = incidents.size,
                byStatus = byStatus,
                bySeverity = bySeverity,
                byCategorySlug = byCategory,
                totalStillExistsConfirmations = incidents.sumOf { it.stillExistsCount },
                totalSuggestResolvedConfirmations = incidents.sumOf { it.suggestResolvedCount },
                aiSuggestedCount = telemetry.count { it.mode == "ai_suggested" },
                manualFallbackCount = telemetry.count { it.mode == "manual_fallback" },
                aiErrorCount = telemetry.count { it.mode == "ai_error" },
                averageAiConfidence = confidences.takeIf { it.isNotEmpty() }?.average(),
            )
        }
    }
}
