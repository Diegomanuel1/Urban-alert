package com.example.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.PilotCityConfig
import com.example.core.model.GeoBoundingBox
import com.example.core.model.GeoCoordinate
import com.example.core.model.IncidentStatus
import com.example.data.incidents.IncidentDocument
import com.example.data.incidents.IncidentRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class MapFilterState(
    val bounds: GeoBoundingBox = PilotCityConfig.salvador.initialBounds,
    val zoomLevel: Double = PilotCityConfig.salvador.defaultZoom.toDouble(),
    val categorySlug: String? = null,
    val statusFilter: IncidentStatus? = null,
)

sealed interface MapContentState {
    data object Loading : MapContentState
    data class Ready(
        val incidents: List<IncidentDocument>,
        val renderNodes: List<MapRenderNode>,
    ) : MapContentState
    data class Error(val message: String) : MapContentState
}

data class MapUiMessage(
    val text: String,
    val isError: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MapViewModel(
    private val incidentRepository: IncidentRepository,
) : ViewModel() {

    private val _filterState = MutableStateFlow(MapFilterState())
    val filterState: StateFlow<MapFilterState> = _filterState.asStateFlow()

    private val _selectedIncident = MutableStateFlow<IncidentDocument?>(null)
    val selectedIncident: StateFlow<IncidentDocument?> = _selectedIncident.asStateFlow()

    private val _isBusyLocationOrSearch = MutableStateFlow(false)
    val isBusyLocationOrSearch: StateFlow<Boolean> = _isBusyLocationOrSearch.asStateFlow()

    private val _statusBanner = MutableStateFlow<MapUiMessage?>(null)
    val statusBanner: StateFlow<MapUiMessage?> = _statusBanner.asStateFlow()

    private val _cameraTarget = MutableSharedFlow<Pair<GeoCoordinate, Double>>(extraBufferCapacity = 4)
    val cameraTarget = _cameraTarget.asSharedFlow()

    val mapContentState: StateFlow<MapContentState> = _filterState
        .map { state -> Triple(state.bounds, state.categorySlug, state.statusFilter) }
        .distinctUntilChanged()
        .debounce(250L)
        .flatMapLatest { (bounds, categorySlug, statusFilter) ->
            incidentRepository.observeIncidentsInBounds(
                bounds = bounds,
                categorySlug = categorySlug,
                statusFilter = statusFilter,
            ).combine(_filterState.map { it.zoomLevel }.distinctUntilChanged()) { incidents, zoom ->
                MapContentState.Ready(
                    incidents = incidents,
                    renderNodes = IncidentClusterer.cluster(incidents, zoom),
                ) as MapContentState
            }.catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Erro ao consultar área visível no mapa", rootError)
                emit(
                    MapContentState.Error(
                        rootError.message ?: "Não foi possível carregar ocorrências nesta área.",
                    ),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MapContentState.Loading,
        )

    fun updateViewport(bounds: GeoBoundingBox, zoomLevel: Double) {
        _filterState.update { current ->
            current.copy(bounds = bounds, zoomLevel = zoomLevel)
        }
    }

    fun selectCategoryFilter(slug: String?) {
        _filterState.update { current ->
            val next = if (current.categorySlug == slug) null else slug
            current.copy(categorySlug = next)
        }
    }

    fun selectStatusFilter(status: IncidentStatus?) {
        _filterState.update { current ->
            val next = if (current.statusFilter == status) null else status
            current.copy(statusFilter = next)
        }
    }

    fun clearAllFilters() {
        _filterState.update { it.copy(categorySlug = null, statusFilter = null) }
    }

    fun selectIncident(incident: IncidentDocument?) {
        _selectedIncident.value = incident
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun recenterOnSalvador() {
        _statusBanner.value = null
        _cameraTarget.tryEmit(
            PilotCityConfig.salvador.center to PilotCityConfig.salvador.defaultZoom.toDouble(),
        )
    }

    @SuppressLint("MissingPermission")
    fun moveToCurrentDeviceLocation(context: Context) {
        viewModelScope.launch {
            _isBusyLocationOrSearch.value = true
            _statusBanner.value = null
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                val tokenSource = CancellationTokenSource()
                val location = fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    tokenSource.token,
                ).await() ?: fusedClient.lastLocation.await()

                if (location != null) {
                    _cameraTarget.tryEmit(
                        GeoCoordinate(location.latitude, location.longitude) to 16.0,
                    )
                } else {
                    _statusBanner.value = MapUiMessage(
                        text = "Não foi possível obter o GPS agora. Verifique se a localização do aparelho está ativa.",
                        isError = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao obter GPS", e)
                _statusBanner.value = MapUiMessage(
                    text = "Erro ao acessar GPS: ${e.localizedMessage ?: "verifique as permissões."}",
                    isError = true,
                )
            } finally {
                _isBusyLocationOrSearch.value = false
            }
        }
    }

    fun searchLocationInSalvador(context: Context, rawQuery: String) {
        val query = rawQuery.trim()
        if (query.length < 2) {
            _statusBanner.value = MapUiMessage(
                text = "Digite pelo menos 2 caracteres para buscar um bairro ou via.",
                isError = true,
            )
            return
        }
        viewModelScope.launch {
            _isBusyLocationOrSearch.value = true
            _statusBanner.value = null
            try {
                val scopedQuery = if (query.contains("salvador", ignoreCase = true)) {
                    query
                } else {
                    "$query, Salvador, Bahia, Brasil"
                }
                val address = geocodeAddress(context, scopedQuery)
                if (address != null) {
                    _cameraTarget.tryEmit(
                        GeoCoordinate(address.latitude, address.longitude) to 15.5,
                    )
                    val label = address.subLocality ?: address.thoroughfare ?: address.featureName ?: query
                    _statusBanner.value = MapUiMessage("Mapa centralizado em: $label")
                } else {
                    _statusBanner.value = MapUiMessage(
                        text = "Nenhum local encontrado para \"$query\" em Salvador.",
                        isError = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Falha no Geocoder", e)
                _statusBanner.value = MapUiMessage(
                    text = "Busca de endereço indisponível no momento sem conexão.",
                    isError = true,
                )
            } finally {
                _isBusyLocationOrSearch.value = false
            }
        }
    }

    private suspend fun geocodeAddress(context: Context, query: String): Address? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(
                        query,
                        1,
                        PilotCityConfig.salvador.initialBounds.south,
                        PilotCityConfig.salvador.initialBounds.west,
                        PilotCityConfig.salvador.initialBounds.north,
                        PilotCityConfig.salvador.initialBounds.east,
                    ) { addresses ->
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(
                    query,
                    1,
                    PilotCityConfig.salvador.initialBounds.south,
                    PilotCityConfig.salvador.initialBounds.west,
                    PilotCityConfig.salvador.initialBounds.north,
                    PilotCityConfig.salvador.initialBounds.east,
                )?.firstOrNull()
            }
        }

    private companion object {
        const val TAG = "MapVM"
    }
}
