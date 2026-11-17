package com.example.feature.incidents

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.model.CommunityConfirmationOutcome
import com.example.core.model.ConfirmationType
import com.example.core.model.IncidentStatus
import com.example.core.model.PublicAgency
import com.example.core.model.Severity
import com.example.core.model.UserIncidentConfirmations
import com.example.data.agencies.AgencyRepository
import com.example.data.community.CommunityRepository
import com.example.data.incidents.IncidentDocument
import com.example.data.incidents.IncidentRepository
import com.example.ui.theme.SeverityCriticalColor
import com.example.ui.theme.SeverityHighColor
import com.example.ui.theme.SeverityLowColor
import com.example.ui.theme.SeverityMediumColor
import com.example.ui.theme.StatusInProgressColor
import com.example.ui.theme.StatusOpenColor
import com.example.ui.theme.StatusRejectedColor
import com.example.ui.theme.StatusResolvedColor
import com.example.ui.theme.StatusUnderReviewColor
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private sealed interface DetailLoadState {
    data object Loading : DetailLoadState
    data class Loaded(val incident: IncidentDocument?) : DetailLoadState
    data class Error(val message: String) : DetailLoadState
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentDetailScreen(
    incidentId: String,
    incidentRepository: IncidentRepository,
    communityRepository: CommunityRepository,
    agencyRepository: AgencyRepository,
    currentUserId: String?,
    onRequestSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val stateFlow = remember(incidentId) {
        incidentRepository.observeIncidentById(incidentId)
            .map<IncidentDocument?, DetailLoadState> { DetailLoadState.Loaded(it) }
            .catch { emit(DetailLoadState.Error(it.localizedMessage ?: "Erro ao carregar detalhes.")) }
    }
    val state by stateFlow.collectAsStateWithLifecycle(initialValue = DetailLoadState.Loading)

    val confirmationsFlow = remember(incidentId, currentUserId) {
        communityRepository.observeUserConfirmations(incidentId, currentUserId)
    }
    val userConfirmations by confirmationsFlow.collectAsStateWithLifecycle(
        initialValue = UserIncidentConfirmations(),
    )

    var submittingType by remember { mutableStateOf<ConfirmationType?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }

    val confirmedSuccessText = stringResource(R.string.community_confirmed_success)
    val alreadyConfirmedText = stringResource(R.string.community_already_confirmed)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.incident_detail_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("incident_detail_back_button"),
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
            when (val current = state) {
                DetailLoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is DetailLoadState.Error -> {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Text(
                            text = current.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                is DetailLoadState.Loaded -> {
                    val incident = current.incident
                    if (incident == null) {
                        Card(
                            modifier = Modifier
                                .padding(16.dp)
                                .widthIn(max = 600.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Text(
                                text = stringResource(R.string.incident_not_found),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        val baselineAgencies = remember(incident.cityCode, incident.categorySlug) {
                            PilotCityConfig.findAgenciesForIncident(
                                cityCode = incident.cityCode,
                                categorySlug = incident.categorySlug,
                            )
                        }
                        val agenciesFlow = remember(incident.cityCode, incident.categorySlug) {
                            agencyRepository.observeAgenciesForIncident(
                                cityCode = incident.cityCode,
                                categorySlug = incident.categorySlug,
                            )
                        }
                        val agencies by agenciesFlow.collectAsStateWithLifecycle(
                            initialValue = baselineAgencies,
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 640.dp),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (!incident.publicImageUrl.isNullOrBlank()) {
                                item {
                                    AsyncImage(
                                        model = incident.publicImageUrl,
                                        contentDescription = stringResource(R.string.cd_incident_public_photo),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(20.dp)),
                                    )
                                }
                            }

                            item {
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = incident.categoryTitle(),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = incident.title,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            val severity = incident.resolvedSeverity()
                                            val status = incident.resolvedStatus()
                                            DetailBadge(
                                                label = stringResource(severity.labelRes),
                                                icon = severityVector(severity),
                                                accent = severityAccent(severity),
                                                contentDescription = stringResource(R.string.cd_severity_icon),
                                            )
                                            DetailBadge(
                                                label = stringResource(status.labelRes),
                                                icon = statusVector(status),
                                                accent = statusAccent(status),
                                                contentDescription = stringResource(R.string.cd_status_icon),
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AccessTime,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = "Registrada ${incident.elapsedLabel()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocationOn,
                                                contentDescription = stringResource(R.string.cd_pilot_location),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Text(
                                                text = incident.addressLabel
                                                    ?: "${"%.5f".format(incident.latitude)}, ${"%.5f".format(incident.longitude)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }

                                        Text(
                                            text = incident.description,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }

                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.incident_community_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            CounterBox(
                                                label = stringResource(R.string.incident_still_exists_label),
                                                count = incident.stillExistsCount,
                                                icon = Icons.Rounded.ThumbUpOffAlt,
                                                modifier = Modifier.weight(1f),
                                            )
                                            CounterBox(
                                                label = stringResource(R.string.incident_suggest_resolved_label),
                                                count = incident.suggestResolvedCount,
                                                icon = Icons.Rounded.Verified,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }

                                        if (currentUserId == null) {
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.community_login_prompt),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    OutlinedButton(
                                                        onClick = onRequestSignIn,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .minimumInteractiveComponentSize()
                                                            .testTag("detail_login_to_confirm_button"),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Login,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(stringResource(R.string.community_login_button))
                                                    }
                                                }
                                            }
                                        } else {
                                            val stillConfirmed = userConfirmations.confirmedStillExists
                                            val resolvedConfirmed = userConfirmations.confirmedSuggestResolved
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        feedbackMessage = null
                                                        submittingType = ConfirmationType.STILL_EXISTS
                                                        scope.launch {
                                                            val result = communityRepository.submitConfirmation(
                                                                incidentId = incident.id.ifBlank { incidentId },
                                                                userId = currentUserId,
                                                                cityCode = incident.cityCode,
                                                                type = ConfirmationType.STILL_EXISTS,
                                                            )
                                                            submittingType = null
                                                            result.fold(
                                                                onSuccess = { outcome ->
                                                                    feedbackIsError = false
                                                                    feedbackMessage = when (outcome) {
                                                                        is CommunityConfirmationOutcome.Recorded -> confirmedSuccessText
                                                                        is CommunityConfirmationOutcome.AlreadyConfirmed -> alreadyConfirmedText
                                                                    }
                                                                },
                                                                onFailure = { error ->
                                                                    feedbackIsError = true
                                                                    feedbackMessage = error.localizedMessage
                                                                        ?: "Não foi possível registrar a confirmação."
                                                                },
                                                            )
                                                        }
                                                    },
                                                    enabled = !stillConfirmed && submittingType == null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("confirm_still_exists_button"),
                                                ) {
                                                    if (submittingType == ConfirmationType.STILL_EXISTS) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.onPrimary,
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (stillConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.ThumbUpOffAlt,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(
                                                            if (stillConfirmed) {
                                                                R.string.community_confirm_still_exists_done
                                                            } else {
                                                                R.string.community_confirm_still_exists_cta
                                                            },
                                                        ),
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        feedbackMessage = null
                                                        submittingType = ConfirmationType.SUGGEST_RESOLVED
                                                        scope.launch {
                                                            val result = communityRepository.submitConfirmation(
                                                                incidentId = incident.id.ifBlank { incidentId },
                                                                userId = currentUserId,
                                                                cityCode = incident.cityCode,
                                                                type = ConfirmationType.SUGGEST_RESOLVED,
                                                            )
                                                            submittingType = null
                                                            result.fold(
                                                                onSuccess = { outcome ->
                                                                    feedbackIsError = false
                                                                    feedbackMessage = when (outcome) {
                                                                        is CommunityConfirmationOutcome.Recorded -> confirmedSuccessText
                                                                        is CommunityConfirmationOutcome.AlreadyConfirmed -> alreadyConfirmedText
                                                                    }
                                                                },
                                                                onFailure = { error ->
                                                                    feedbackIsError = true
                                                                    feedbackMessage = error.localizedMessage
                                                                        ?: "Não foi possível enviar a sinalização."
                                                                },
                                                            )
                                                        }
                                                    },
                                                    enabled = !resolvedConfirmed && submittingType == null,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("confirm_suggest_resolved_button"),
                                                ) {
                                                    if (submittingType == ConfirmationType.SUGGEST_RESOLVED) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (resolvedConfirmed) Icons.Rounded.CheckCircle else Icons.Rounded.Verified,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = stringResource(
                                                            if (resolvedConfirmed) {
                                                                R.string.community_confirm_resolved_done
                                                            } else {
                                                                R.string.community_confirm_resolved_cta
                                                            },
                                                        ),
                                                    )
                                                }
                                            }
                                        }

                                        if (!feedbackMessage.isNullOrBlank()) {
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = if (feedbackIsError) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                },
                                            ) {
                                                Text(
                                                    text = feedbackMessage.orEmpty(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (feedbackIsError) {
                                                        MaterialTheme.colorScheme.onErrorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                )
                                            }
                                        }

                                        Text(
                                            text = stringResource(R.string.incident_community_disclaimer),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }

                            if (agencies.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.agency_section_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                }
                                items(agencies, key = { it.id }) { agency ->
                                    PublicAgencyCard(
                                        agency = agency,
                                        onOpenPortal = { url ->
                                            runCatching { uriHandler.openUri(url) }
                                        },
                                        onDialPhone = { phone ->
                                            runCatching {
                                                val dialIntent = Intent(
                                                    Intent.ACTION_DIAL,
                                                    Uri.parse("tel:$phone"),
                                                )
                                                context.startActivity(dialIntent)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicAgencyCard(
    agency: PublicAgency,
    onOpenPortal: (String) -> Unit,
    onDialPhone: (String) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalance,
                    contentDescription = stringResource(R.string.cd_agency_icon),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        text = agency.acronym,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = agency.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Text(
                text = agency.competenceSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.agency_service_area_label, agency.serviceAreaLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.agency_official_channel_label, agency.officialChannelLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.agency_verified_date_label, agency.lastVerifiedAtLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val portalUrl = agency.officialPortalUrl
                if (!portalUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onOpenPortal(portalUrl) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("open_agency_portal_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.agency_open_portal_button))
                    }
                }
                val phone = agency.officialPhone
                if (!phone.isNullOrBlank()) {
                    Button(
                        onClick = { onDialPhone(phone) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("dial_agency_phone_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.agency_call_channel_button, phone))
                    }
                }
            }

            Text(
                text = stringResource(R.string.agency_transparency_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CounterBox(
    label: String,
    count: Long,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun DetailBadge(
    label: String,
    icon: ImageVector,
    accent: Color,
    contentDescription: String,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

fun severityVector(severity: Severity): ImageVector = when (severity) {
    Severity.LOW -> Icons.Rounded.Info
    Severity.MEDIUM -> Icons.Rounded.WarningAmber
    Severity.HIGH -> Icons.Rounded.ReportProblem
    Severity.CRITICAL -> Icons.Rounded.ErrorOutline
}

fun severityAccent(severity: Severity): Color = when (severity) {
    Severity.LOW -> SeverityLowColor
    Severity.MEDIUM -> SeverityMediumColor
    Severity.HIGH -> SeverityHighColor
    Severity.CRITICAL -> SeverityCriticalColor
}

fun statusVector(status: IncidentStatus): ImageVector = when (status) {
    IncidentStatus.OPEN -> Icons.Rounded.RadioButtonChecked
    IncidentStatus.UNDER_REVIEW -> Icons.Rounded.FactCheck
    IncidentStatus.IN_PROGRESS -> Icons.Rounded.HourglassTop
    IncidentStatus.RESOLVED -> Icons.Rounded.CheckCircle
    IncidentStatus.REJECTED -> Icons.Rounded.HighlightOff
}

fun statusAccent(status: IncidentStatus): Color = when (status) {
    IncidentStatus.OPEN -> StatusOpenColor
    IncidentStatus.UNDER_REVIEW -> StatusUnderReviewColor
    IncidentStatus.IN_PROGRESS -> StatusInProgressColor
    IncidentStatus.RESOLVED -> StatusResolvedColor
    IncidentStatus.REJECTED -> StatusRejectedColor
}
