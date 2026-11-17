package com.example.feature.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.model.AdminAccountInfo
import com.example.core.model.IncidentStatus
import com.example.core.model.Severity
import com.example.data.incidents.IncidentDocument
import com.example.feature.incidents.DetailBadge
import com.example.feature.incidents.severityAccent
import com.example.feature.incidents.severityVector
import com.example.feature.incidents.statusAccent
import com.example.feature.incidents.statusVector

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    adminInfo: AdminAccountInfo,
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val incidents by viewModel.filteredIncidents.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val agencies by viewModel.agencies.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var transitionTarget by remember { mutableStateOf<IncidentDocument?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddAgencyDialog by remember { mutableStateOf(false) }

    transitionTarget?.let { target ->
        StatusTransitionDialog(
            incident = target,
            onDismiss = { transitionTarget = null },
            onConfirm = { newStatus, reason ->
                transitionTarget = null
                viewModel.updateIncidentStatus(target, newStatus, reason)
            },
        )
    }

    if (showAddCategoryDialog) {
        CategoryEditDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { slug, title, desc, sev ->
                showAddCategoryDialog = false
                viewModel.saveCategory(slug, title, desc, sev)
            },
        )
    }

    if (showAddAgencyDialog) {
        AgencyEditDialog(
            onDismiss = { showAddAgencyDialog = false },
            onConfirm = { agency ->
                showAddAgencyDialog = false
                viewModel.saveAgency(agency)
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_panel_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("admin_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .testTag("admin_dashboard_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = stringResource(R.string.cd_admin_icon),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = "${adminInfo.role.wireValue.uppercase()} · ${adminInfo.organizationId}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = stringResource(R.string.admin_panel_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedSection == AdminSection.REPORTS,
                                        onClick = { viewModel.selectSection(AdminSection.REPORTS) },
                                        leadingIcon = { Icon(Icons.Rounded.Assessment, null, Modifier.size(18.dp)) },
                                        label = { Text(stringResource(R.string.admin_tab_reports)) },
                                        modifier = Modifier.testTag("admin_tab_reports"),
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedSection == AdminSection.TRIAGE,
                                        onClick = { viewModel.selectSection(AdminSection.TRIAGE) },
                                        leadingIcon = { Icon(Icons.Rounded.Rule, null, Modifier.size(18.dp)) },
                                        label = { Text(stringResource(R.string.admin_tab_triage)) },
                                        modifier = Modifier.testTag("admin_tab_triage"),
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedSection == AdminSection.CATALOG,
                                        onClick = { viewModel.selectSection(AdminSection.CATALOG) },
                                        leadingIcon = { Icon(Icons.Rounded.Category, null, Modifier.size(18.dp)) },
                                        label = { Text(stringResource(R.string.admin_tab_catalog)) },
                                        modifier = Modifier.testTag("admin_tab_catalog"),
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedSection == AdminSection.AI_AUDIT,
                                        onClick = { viewModel.selectSection(AdminSection.AI_AUDIT) },
                                        leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) },
                                        label = { Text(stringResource(R.string.admin_tab_audit)) },
                                        modifier = Modifier.testTag("admin_tab_audit"),
                                    )
                                }
                            }
                        }
                    }
                }

                feedback?.let { note ->
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (note.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = note.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (note.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            )
                        }
                    }
                }

                when (selectedSection) {
                    AdminSection.REPORTS -> {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "Resumo operacional · ${PilotCityConfig.salvador.displayName}",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text("Ocorrências monitoradas: ${report.totalIncidents}")
                                    Text(
                                        text = "Confirmações: ${report.totalStillExistsConfirmations} 'ainda existe' · ${report.totalSuggestResolvedConfirmations} 'aparenta resolvida'",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        IncidentStatus.entries.forEach { st ->
                                            DetailBadge(
                                                label = "${stringResource(st.labelRes)}: ${report.byStatus[st] ?: 0}",
                                                icon = statusVector(st),
                                                accent = statusAccent(st),
                                                contentDescription = stringResource(R.string.cd_status_icon),
                                            )
                                        }
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Severity.entries.forEach { sev ->
                                            DetailBadge(
                                                label = "${stringResource(sev.labelRes)}: ${report.bySeverity[sev] ?: 0}",
                                                icon = severityVector(sev),
                                                accent = severityAccent(sev),
                                                contentDescription = stringResource(R.string.cd_severity_icon),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminSection.TRIAGE -> {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = statusFilter == null,
                                        onClick = { viewModel.selectStatusFilter(null) },
                                        label = { Text("Todas") },
                                    )
                                }
                                items(IncidentStatus.entries) { st ->
                                    FilterChip(
                                        selected = statusFilter == st,
                                        onClick = { viewModel.selectStatusFilter(st) },
                                        label = { Text(stringResource(st.labelRes)) },
                                    )
                                }
                            }
                        }
                        if (incidents.isEmpty()) {
                            item {
                                Text(
                                    text = "Nenhuma ocorrência encontrada para este filtro.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(incidents, key = { it.id }) { incident ->
                                val st = incident.resolvedStatus()
                                val sev = incident.resolvedSeverity()
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = incident.title,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            DetailBadge(
                                                label = stringResource(st.labelRes),
                                                icon = statusVector(st),
                                                accent = statusAccent(st),
                                                contentDescription = stringResource(R.string.cd_status_icon),
                                            )
                                            DetailBadge(
                                                label = stringResource(sev.labelRes),
                                                icon = severityVector(sev),
                                                accent = severityAccent(sev),
                                                contentDescription = stringResource(R.string.cd_severity_icon),
                                            )
                                        }
                                        Text(
                                            text = "Comunidade: ${incident.stillExistsCount} persiste · ${incident.suggestResolvedCount} resolvido",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Button(
                                            onClick = { transitionTarget = incident },
                                            enabled = !isBusy,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .testTag("admin_change_status_button"),
                                        ) {
                                            Icon(Icons.Rounded.FactCheck, null, Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.admin_change_status_button))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminSection.CATALOG -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showAddCategoryDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_add_category_button"),
                                ) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.admin_add_category_button))
                                }
                                OutlinedButton(
                                    onClick = { showAddAgencyDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_add_agency_button"),
                                ) {
                                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.admin_add_agency_button))
                                }
                            }
                        }
                        items(categories, key = { it.id }) { cat ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${cat.title} (${cat.slug})", style = MaterialTheme.typography.titleSmall)
                                    Text(cat.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        items(agencies, key = { it.id }) { agency ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("${agency.acronym} — ${agency.name}", style = MaterialTheme.typography.titleSmall)
                                    Text("${agency.officialChannelLabel} · ${agency.serviceAreaLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    AdminSection.AI_AUDIT -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                        Text("Monitoramento da IA (Gemini)", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Text("Análises concluídas: ${report.aiSuggestedCount} · Fallbacks manuais: ${report.manualFallbackCount} · Erros: ${report.aiErrorCount}")
                                    val confText = report.averageAiConfidence?.let { "${(it * 100).toInt()}%" } ?: "Sem medições ainda"
                                    Text("Confiança média registrada: $confText", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (auditLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "Nenhum registro administrativo gravado ainda.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(auditLogs, key = { it.id }) { log ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${log.action} · ${log.targetId}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
