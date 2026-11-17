package com.example.data.agencies

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.core.model.PublicAgency
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Keep
@IgnoreExtraProperties
data class AgencyDocument(
    @DocumentId val id: String = "",
    val cityCode: String = PilotCityConfig.salvador.cityCode,
    val acronym: String = "",
    val name: String = "",
    val competenceSummary: String = "",
    val categorySlugs: List<String> = emptyList(),
    val serviceAreaLabel: String = "",
    val officialChannelLabel: String = "",
    val officialPortalUrl: String? = null,
    val officialPhone: String? = null,
    val lastVerifiedAtLabel: String = "",
    val active: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun toDomain(): PublicAgency = PublicAgency(
        id = id,
        cityCode = cityCode,
        acronym = acronym,
        name = name,
        competenceSummary = competenceSummary,
        categorySlugs = categorySlugs,
        serviceAreaLabel = serviceAreaLabel,
        officialChannelLabel = officialChannelLabel,
        officialPortalUrl = officialPortalUrl,
        officialPhone = officialPhone,
        lastVerifiedAtLabel = lastVerifiedAtLabel,
        active = active,
    )
}

/**
 * Repositório de órgãos públicos (/agencies), combinando cadastros atualizados no Firestore
 * com a base verificada da cidade-piloto para evitar tela vazia antes do seed administrativo.
 */
class AgencyRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
    )

    fun observeAgenciesForIncident(
        cityCode: String,
        categorySlug: String,
    ): Flow<List<PublicAgency>> {
        return db.collection(COLLECTION_AGENCIES)
            .whereEqualTo("cityCode", cityCode)
            .limit(50L)
            .snapshots()
            .map { snapshot ->
                val remoteAgencies = snapshot.toObjects(AgencyDocument::class.java)
                    .map { it.toDomain() }
                val merged = mergeRemoteAndBaseline(
                    remote = remoteAgencies,
                    baseline = PilotCityConfig.salvadorAgencies.filter { it.cityCode == cityCode },
                )
                PilotCityConfig.findAgenciesForIncident(
                    cityCode = cityCode,
                    categorySlug = categorySlug,
                    agencies = merged,
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, COLLECTION_AGENCIES)
                }
                emit(
                    PilotCityConfig.findAgenciesForIncident(
                        cityCode = cityCode,
                        categorySlug = categorySlug,
                    ),
                )
            }
    }

    companion object {
        const val COLLECTION_AGENCIES = "agencies"

        fun mergeRemoteAndBaseline(
            remote: List<PublicAgency>,
            baseline: List<PublicAgency>,
        ): List<PublicAgency> {
            if (remote.isEmpty()) return baseline
            val byId = LinkedHashMap<String, PublicAgency>()
            baseline.forEach { byId[it.id] = it }
            remote.forEach { byId[it.id] = it }
            return byId.values.toList()
        }
    }
}
