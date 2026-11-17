package com.example.feature.report

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.agent.AgentStep
import com.example.core.config.PilotCityConfig
import com.example.core.model.Severity
import com.example.feature.incidents.severityAccent
import com.example.feature.incidents.severityVector
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IncidentReviewScreen(
    viewModel: ReportViewModel,
    isSignedIn: Boolean,
    currentUserId: String?,
    onRequestSignIn: () -> Unit,
    onOpenOfflineQueue: () -> Unit,
    onBackToMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val publishedId by viewModel.publishedIncidentId.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    BackHandler {
        if (step == AgentStep.PUBLISHED || step == AgentStep.QUEUED_OFFLINE) {
            viewModel.startFreshReport()
            onBackToMap()
        } else {
            viewModel.returnToChat()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (step == AgentStep.PUBLISHED || step == AgentStep.QUEUED_OFFLINE) {
                                viewModel.startFreshReport()
                                onBackToMap()
                            } else {
                                viewModel.returnToChat()
                            }
                        },
                        modifier = Modifier.testTag("review_back_button"),
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
            if (step == AgentStep.QUEUED_OFFLINE) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudQueue,
                                contentDescription = stringResource(R.string.cd_sync_state_icon),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = stringResource(R.string.offline_queued_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.offline_queued_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.startFreshReport()
                                    onOpenOfflineQueue()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("queued_open_list_button"),
                            ) {
                                Text(stringResource(R.string.offline_open_queue_button))
                            }
                            Button(
                                onClick = {
                                    viewModel.startFreshReport()
                                    onBackToMap()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("queued_view_map_button"),
                            ) {
                                Text(stringResource(R.string.report_view_on_map))
                            }
                        }
                    }
                }
                return@Box
            }

            if (step == AgentStep.PUBLISHED) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .padding(20.dp)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.cd_status_icon),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = stringResource(R.string.report_published_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.report_published_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!publishedId.isNullOrBlank()) {
                            Text(
                                text = "Registro: $publishedId",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.startFreshReport() },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("published_new_report_button"),
                            ) {
                                Text(stringResource(R.string.report_new_session))
                            }
                            Button(
                                onClick = {
                                    viewModel.startFreshReport()
                                    onBackToMap()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("published_view_map_button"),
                            ) {
                                Text(stringResource(R.string.report_view_on_map))
                            }
                        }
                    }
                }
                return@Box
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.review_human_control_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val aiDraft = draft.aiDraft
                            if (aiDraft != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.review_ai_confidence,
                                            (aiDraft.confidence * 100).toInt(),
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (aiDraft.visualEvidence.isNotEmpty()) {
                                    Text(
                                        text = "Indícios: ${aiDraft.visualEvidence.joinToString(" · ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                                if (aiDraft.warnings.isNotEmpty()) {
                                    Text(
                                        text = "Alertas da IA: ${aiDraft.warnings.joinToString(" · ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            } else if (!draft.aiFallbackReason.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = draft.aiFallbackReason.orEmpty(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorBanner != null) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                text = errorBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                            )
                        }
                    }
                }

                if (!draft.sanitizedPhotoPath.isNullOrBlank()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AsyncImage(
                                    model = File(draft.sanitizedPhotoPath.orEmpty()),
                                    contentDescription = stringResource(R.string.cd_sanitized_photo_preview),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = stringResource(R.string.cd_security_icon),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.review_photo_privacy_note),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

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
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.review_category_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PilotCityConfig.initialCategories.forEach { cat ->
                                    FilterChip(
                                        selected = draft.selectedCategorySlug == cat.slug,
                                        onClick = { viewModel.updateReviewFields(categorySlug = cat.slug) },
                                        label = { Text(cat.title) },
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.review_severity_label),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Severity.entries.forEach { severity ->
                                    FilterChip(
                                        selected = draft.selectedSeverity == severity,
                                        onClick = { viewModel.updateReviewFields(severity = severity) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = severityVector(severity),
                                                contentDescription = stringResource(R.string.cd_severity_icon),
                                                tint = severityAccent(severity),
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        label = { Text(stringResource(severity.labelRes)) },
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = draft.title,
                                onValueChange = { viewModel.updateReviewFields(title = it) },
                                label = { Text(stringResource(R.string.review_title_label)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("review_title_input"),
                            )

                            OutlinedTextField(
                                value = draft.description,
                                onValueChange = { viewModel.updateReviewFields(description = it) },
                                label = { Text(stringResource(R.string.review_description_label)) },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("review_description_input"),
                            )

                            OutlinedTextField(
                                value = draft.addressLabel.orEmpty(),
                                onValueChange = { viewModel.updateReviewFields(addressLabel = it) },
                                label = { Text(stringResource(R.string.review_address_label)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = stringResource(R.string.cd_pilot_location),
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("review_address_input"),
                            )

                            val coord = draft.coordinate
                            if (coord != null) {
                                Text(
                                    text = "Coordenadas WGS 84: ${"%.5f".format(coord.latitude)}, ${"%.5f".format(coord.longitude)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (!isOnline) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudOff,
                                    contentDescription = stringResource(R.string.cd_sync_state_icon),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.offline_status_banner_offline),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                if ((!isSignedIn || currentUserId.isNullOrBlank()) && isOnline) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.review_login_required_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = stringResource(R.string.review_login_required_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Button(
                                    onClick = onRequestSignIn,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("review_sign_in_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.review_login_button))
                                }
                            }
                        }
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.returnToChat() },
                                enabled = step != AgentStep.PUBLISHING,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("review_back_to_chat_button"),
                            ) {
                                Text(stringResource(R.string.review_back_to_chat))
                            }

                            Button(
                                onClick = {
                                    if (!isOnline) {
                                        viewModel.saveConfirmedToOfflineQueue()
                                    } else if (!currentUserId.isNullOrBlank()) {
                                        viewModel.publishConfirmedIncident(currentUserId)
                                    } else {
                                        onRequestSignIn()
                                    }
                                },
                                enabled = step != AgentStep.PUBLISHING && (!isOnline || !currentUserId.isNullOrBlank()),
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("review_publish_button"),
                            ) {
                                if (step == AgentStep.PUBLISHING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isOnline) Icons.Rounded.Publish else Icons.Rounded.CloudQueue,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isOnline) {
                                            stringResource(R.string.review_publish_button)
                                        } else {
                                            stringResource(R.string.offline_save_queue_button)
                                        },
                                    )
                                }
                            }
                        }

                        if (isOnline) {
                            OutlinedButton(
                                onClick = { viewModel.saveConfirmedToOfflineQueue() },
                                enabled = step != AgentStep.PUBLISHING,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("review_save_offline_queue_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.offline_save_queue_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
