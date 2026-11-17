package com.example.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.model.IncidentStatus
import com.example.core.model.PublicAgency
import com.example.core.model.Severity
import com.example.data.incidents.IncidentDocument

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusTransitionDialog(
    incident: IncidentDocument,
    onDismiss: () -> Unit,
    onConfirm: (IncidentStatus, String) -> Unit,
) {
    val current = incident.resolvedStatus()
    val candidates = IncidentStatus.entries.filter { it != current }
    var targetStatus by remember(incident.id) { mutableStateOf(candidates.first()) }
    var reason by remember(incident.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_transition_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = incident.title)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    candidates.forEach { candidate ->
                        FilterChip(
                            selected = targetStatus == candidate,
                            onClick = { targetStatus = candidate },
                            label = { Text(stringResource(candidate.labelRes)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    label = { Text(stringResource(R.string.admin_transition_reason_label)) },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_transition_reason_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(targetStatus, reason) },
                enabled = reason.trim().length >= 3,
                modifier = Modifier.testTag("admin_confirm_transition_button"),
            ) {
                Text(stringResource(R.string.admin_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_delete_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditDialog(
    onDismiss: () -> Unit,
    onConfirm: (slug: String, title: String, description: String, severity: Severity) -> Unit,
) {
    var slug by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(Severity.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_add_category_button)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it.lowercase().take(64) },
                    label = { Text("Slug (ex.: poda-de-arvore)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Título visível") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(300) },
                    label = { Text("Descrição") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Severity.entries.forEach { item ->
                        FilterChip(
                            selected = severity == item,
                            onClick = { severity = item },
                            label = { Text(stringResource(item.labelRes)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(slug, title, description, severity) },
                enabled = slug.trim().length >= 2 && title.trim().length >= 2 && description.trim().length >= 4,
            ) {
                Text(stringResource(R.string.admin_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_delete_cancel))
            }
        },
    )
}

@Composable
fun AgencyEditDialog(
    onDismiss: () -> Unit,
    onConfirm: (PublicAgency) -> Unit,
) {
    var acronym by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var competence by remember { mutableStateOf("") }
    var channelLabel by remember { mutableStateOf("Fala Salvador 156") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.admin_add_agency_button)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = acronym,
                    onValueChange = { acronym = it.uppercase().take(24) },
                    label = { Text("Sigla (ex.: SEMOP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(140) },
                    label = { Text("Nome completo do órgão") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = competence,
                    onValueChange = { competence = it.take(400) },
                    label = { Text("Competências principais") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = channelLabel,
                    onValueChange = { channelLabel = it.take(120) },
                    label = { Text("Canal oficial verificado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PublicAgency(
                            id = "",
                            cityCode = PilotCityConfig.salvador.cityCode,
                            acronym = acronym,
                            name = name,
                            competenceSummary = competence,
                            categorySlugs = listOf("buracos-em-vias"),
                            serviceAreaLabel = "${PilotCityConfig.salvador.displayName} (${PilotCityConfig.salvador.stateCode})",
                            officialChannelLabel = channelLabel,
                            lastVerifiedAtLabel = "2026-11-17",
                            active = true,
                        ),
                    )
                },
                enabled = acronym.trim().length >= 2 && name.trim().length >= 3 && competence.trim().length >= 10,
            ) {
                Text(stringResource(R.string.admin_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_delete_cancel))
            }
        },
    )
}
