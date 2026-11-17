package com.example.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.PilotCityConfig
import com.example.core.model.AiTelemetryEntry
import com.example.core.model.AuditLogEntry
import com.example.core.model.CityOperationsReport
import com.example.core.model.IncidentStatus
import com.example.core.model.PublicAgency
import com.example.core.model.Severity
import com.example.core.model.UrbanCategory
import com.example.data.admin.AdminRepository
import com.example.data.incidents.IncidentDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AdminSection {
    REPORTS,
    TRIAGE,
    CATALOG,
    AI_AUDIT,
}

data class AdminFeedback(
    val message: String,
    val isError: Boolean = false,
)

class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val adminUid: String,
    private val cityCode: String = PilotCityConfig.salvador.cityCode,
) : ViewModel() {

    private val _selectedSection = MutableStateFlow(AdminSection.REPORTS)
    val selectedSection: StateFlow<AdminSection> = _selectedSection.asStateFlow()

    private val _statusFilter = MutableStateFlow<IncidentStatus?>(null)
    val statusFilter: StateFlow<IncidentStatus?> = _statusFilter.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedback = MutableStateFlow<AdminFeedback?>(null)
    val feedback: StateFlow<AdminFeedback?> = _feedback.asStateFlow()

    private val allIncidents: StateFlow<List<IncidentDocument>> =
        adminRepository.observeIncidentsForTriage(cityCode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredIncidents: StateFlow<List<IncidentDocument>> = combine(
        allIncidents,
        _statusFilter,
    ) { items, filter ->
        if (filter == null) items else items.filter { it.resolvedStatus() == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<UrbanCategory>> =
        adminRepository.observeCategoriesCatalog()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PilotCityConfig.initialCategories)

    val agencies: StateFlow<List<PublicAgency>> =
        adminRepository.observeAgenciesCatalog(cityCode)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                PilotCityConfig.salvadorAgencies,
            )

    val auditLogs: StateFlow<List<AuditLogEntry>> =
        adminRepository.observeAuditLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val aiTelemetry: StateFlow<List<AiTelemetryEntry>> =
        adminRepository.observeAiTelemetry()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val report: StateFlow<CityOperationsReport> = combine(
        allIncidents,
        aiTelemetry,
    ) { incidents, telemetry ->
        AdminRepository.buildReport(incidents, telemetry)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AdminRepository.buildReport(emptyList(), emptyList()),
    )

    fun selectSection(section: AdminSection) {
        _selectedSection.value = section
    }

    fun selectStatusFilter(status: IncidentStatus?) {
        _statusFilter.value = status
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun updateIncidentStatus(
        incident: IncidentDocument,
        newStatus: IncidentStatus,
        reason: String,
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = adminRepository.transitionIncidentStatus(
                incident = incident,
                newStatus = newStatus,
                reason = reason,
                adminUid = adminUid,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = AdminFeedback("Status oficial atualizado e registrado na auditoria.")
                },
                onFailure = { error ->
                    _feedback.value = AdminFeedback(
                        message = error.localizedMessage ?: "Falha ao atualizar status oficial.",
                        isError = true,
                    )
                },
            )
        }
    }

    fun saveCategory(
        slug: String,
        title: String,
        description: String,
        defaultSeverity: Severity,
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = adminRepository.saveCategory(
                slug = slug,
                title = title,
                description = description,
                defaultSeverity = defaultSeverity,
                adminUid = adminUid,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = AdminFeedback("Categoria salva e registrada na auditoria.")
                },
                onFailure = { error ->
                    _feedback.value = AdminFeedback(
                        message = error.localizedMessage ?: "Falha ao salvar categoria.",
                        isError = true,
                    )
                },
            )
        }
    }

    fun saveAgency(agency: PublicAgency) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = adminRepository.saveAgency(
                agency = agency,
                adminUid = adminUid,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = AdminFeedback("Órgão público salvo e registrado na auditoria.")
                },
                onFailure = { error ->
                    _feedback.value = AdminFeedback(
                        message = error.localizedMessage ?: "Falha ao salvar órgão público.",
                        isError = true,
                    )
                },
            )
        }
    }
}
