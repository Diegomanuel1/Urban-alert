package com.example.core.agent

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import com.example.core.config.PilotCityConfig
import com.example.core.model.GeoCoordinate
import com.example.core.security.IdempotencyKeyGenerator
import com.example.core.security.InputValidator
import com.example.core.security.MediaSanitizer
import com.example.core.security.SanitizedMediaResult
import com.example.core.security.ValidationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class CapturedLocationResult(
    val coordinate: GeoCoordinate,
    val addressLabel: String?,
)

/**
 * Registro explícito de ferramentas executáveis pelo agente conversacional.
 */
class ToolRegistry(
    private val aiAnalyzer: IncidentAiAnalyzer = IncidentAiAnalyzer(),
) {

    suspend fun sanitizePhoto(context: Context, uri: Uri): Result<SanitizedMediaResult> =
        MediaSanitizer.sanitizeFromUri(context, uri)

    @SuppressLint("MissingPermission")
    suspend fun captureDeviceLocation(context: Context): Result<CapturedLocationResult> {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val tokenSource = CancellationTokenSource()
            val location = fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tokenSource.token,
            ).await() ?: fusedClient.lastLocation.await()

            if (location == null) {
                return Result.failure(
                    IllegalStateException("GPS indisponível no momento. Ative a localização ou use o centro de Salvador/endereço."),
                )
            }
            val coordinate = GeoCoordinate(location.latitude, location.longitude)
            val label = reverseGeocodeLabel(context, coordinate)
            Result.success(CapturedLocationResult(coordinate = coordinate, addressLabel = label))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(e.localizedMessage ?: "Não foi possível obter a localização atual."),
            )
        }
    }

    suspend fun resolveAddressInSalvador(context: Context, rawQuery: String): Result<CapturedLocationResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = rawQuery.trim()
                require(query.length >= 3) { "Digite pelo menos 3 caracteres do endereço ou bairro." }
                if (!Geocoder.isPresent()) {
                    throw IllegalStateException("Serviço de geocodificação indisponível neste aparelho.")
                }
                val scoped = if (query.contains("salvador", ignoreCase = true)) {
                    query
                } else {
                    "$query, Salvador, Bahia, Brasil"
                }
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                val bounds = PilotCityConfig.salvador.initialBounds
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocationName(
                            scoped,
                            1,
                            bounds.south,
                            bounds.west,
                            bounds.north,
                            bounds.east,
                        ) { addresses ->
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(
                        scoped,
                        1,
                        bounds.south,
                        bounds.west,
                        bounds.north,
                        bounds.east,
                    )?.firstOrNull()
                } ?: throw IllegalStateException("Endereço não encontrado em Salvador.")

                val coordinate = GeoCoordinate(address.latitude, address.longitude)
                val label = listOfNotNull(
                    address.thoroughfare,
                    address.subLocality,
                    address.locality ?: "Salvador",
                ).distinct().joinToString(", ").ifBlank { query }

                CapturedLocationResult(coordinate = coordinate, addressLabel = label)
            }
        }

    suspend fun analyzeWithAi(
        sanitizedPhotoPath: String?,
        citizenText: String,
        coordinate: GeoCoordinate?,
        addressLabel: String?,
    ): Result<AiIncidentSuggestion> = aiAnalyzer.analyze(
        sanitizedPhotoPath = sanitizedPhotoPath,
        rawCitizenText = citizenText,
        coordinate = coordinate,
        addressLabel = addressLabel,
    )

    fun validateForPublication(draft: CitizenIncidentDraft): String? {
        val titleResult = InputValidator.validateIncidentTitle(draft.title)
        if (titleResult is ValidationResult.Invalid) return titleResult.message

        val descResult = InputValidator.validateIncidentDescription(draft.description)
        if (descResult is ValidationResult.Invalid) return descResult.message

        val coordinate = draft.coordinate ?: return "Informe uma localização válida antes de publicar."
        val coordResult = InputValidator.validateCoordinate(coordinate)
        if (coordResult is ValidationResult.Invalid) return coordResult.message

        val validCategory = PilotCityConfig.initialCategories.any { it.slug == draft.selectedCategorySlug }
        if (!validCategory) return "Selecione uma categoria urbana válida."

        if (!IdempotencyKeyGenerator.isValid(draft.idempotencyKey)) {
            return "Chave de idempotência inválida."
        }
        return null
    }

    private suspend fun reverseGeocodeLabel(context: Context, coordinate: GeoCoordinate): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1) { list ->
                            if (cont.isActive) cont.resume(list.firstOrNull())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)?.firstOrNull()
                } ?: return@runCatching null

                listOfNotNull(
                    address.thoroughfare,
                    address.subLocality,
                    address.locality ?: "Salvador",
                ).distinct().joinToString(", ").takeIf { it.isNotBlank() }
            }.getOrNull()
        }
}
