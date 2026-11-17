package com.example.feature.foundation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibleForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material.icons.rounded.Traffic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.model.IncidentStatus
import com.example.core.model.RoadmapMilestone
import com.example.core.model.Severity
import com.example.core.model.UrbanCategory
import com.example.ui.theme.SeverityCriticalColor
import com.example.ui.theme.SeverityHighColor
import com.example.ui.theme.SeverityLowColor
import com.example.ui.theme.SeverityMediumColor
import com.example.ui.theme.StatusInProgressColor
import com.example.ui.theme.StatusOpenColor
import com.example.ui.theme.StatusRejectedColor
import com.example.ui.theme.StatusResolvedColor
import com.example.ui.theme.StatusUnderReviewColor

/**
 * Tela inicial do Marco M1 — Fundação.
 * Valida identidade visual, tema claro/escuro, cidade-piloto Salvador (BA),
 * taxonomia de categorias, semântica acessível e próximos marcos sem dados fictícios.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoundationShellScreen(
    modifier: Modifier = Modifier,
) {
    val categories = remember { PilotCityConfig.initialCategories }
    val roadmap = remember { PilotCityConfig.roadmapPreview }
    val pilotCity = remember { PilotCityConfig.salvador }
    var selectedCategory by remember { mutableStateOf(categories.first()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .testTag("foundation_scroll_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FoundationTopBar(
                    cityLabel = "${pilotCity.displayName}, ${pilotCity.stateCode}",
                )
            }

            item {
                CartographicHeroCard(
                    centerLat = pilotCity.center.latitude,
                    centerLng = pilotCity.center.longitude,
                )
            }

            item {
                CategoryTaxonomySection(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                )
            }

            item {
                SemanticReferenceSection()
            }

            item {
                RoadmapSection(milestones = roadmap)
            }
        }
    }
}

@Composable
private fun FoundationTopBar(cityLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = stringResource(R.string.cd_brand_emblem),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column {
                Text(
                    text = stringResource(R.string.foundation_brand_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.foundation_brand_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.testTag("pilot_city_badge"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = stringResource(R.string.cd_pilot_location),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = cityLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CartographicHeroCard(
    centerLat: Double,
    centerLng: Double,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("foundation_hero_card"),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .matchParentSize(),
            ) {
                val step = 36.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += step
                }
                drawCircle(
                    color = accentColor.copy(alpha = 0.10f),
                    radius = 74.dp.toPx(),
                    center = Offset(size.width * 0.88f, size.height * 0.28f),
                )
                drawCircle(
                    color = accentColor.copy(alpha = 0.18f),
                    radius = 36.dp.toPx(),
                    center = Offset(size.width * 0.88f, size.height * 0.28f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondary,
                ) {
                    Text(
                        text = stringResource(R.string.foundation_milestone_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.foundation_hero_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.foundation_hero_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.foundation_stack_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Centro geográfico inicial: $centerLat, $centerLng",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryTaxonomySection(
    categories: List<UrbanCategory>,
    selectedCategory: UrbanCategory,
    onSelectCategory: (UrbanCategory) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.foundation_categories_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.foundation_categories_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val isSelected = category.id == selectedCategory.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCategory(category) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("category_chip_${category.slug}"),
                        leadingIcon = {
                            Icon(
                                imageVector = categoryIcon(category.slug),
                                contentDescription = stringResource(R.string.cd_category_icon),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            AnimatedVisibility(visible = true) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_category_detail"),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = categoryIcon(selectedCategory.slug),
                                contentDescription = stringResource(R.string.cd_category_icon),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = selectedCategory.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = selectedCategory.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Slug: ${selectedCategory.slug} · Gravidade típica: ${
                                stringResource(selectedCategory.defaultSeverityHint.labelRes)
                            }",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SemanticReferenceSection() {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.foundation_semantics_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.foundation_semantics_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.foundation_severity_group),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Severity.entries.forEach { severity ->
                    SemanticPill(
                        label = stringResource(severity.labelRes),
                        icon = severityIcon(severity),
                        accent = severityColor(severity),
                        contentDescription = stringResource(R.string.cd_severity_icon),
                    )
                }
            }

            Text(
                text = stringResource(R.string.foundation_status_group),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IncidentStatus.entries.forEach { status ->
                    SemanticPill(
                        label = stringResource(status.labelRes),
                        icon = statusIcon(status),
                        accent = statusColor(status),
                        contentDescription = stringResource(R.string.cd_status_icon),
                    )
                }
            }
        }
    }
}

@Composable
private fun SemanticPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    contentDescription: String,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RoadmapSection(milestones: List<RoadmapMilestone>) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("roadmap_section_card"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.foundation_roadmap_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.foundation_roadmap_next),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            milestones.forEach { milestone ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (milestone.isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (milestone.isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = when {
                                milestone.isCompleted -> Icons.Rounded.CheckCircle
                                milestone.isCurrent -> Icons.Rounded.RadioButtonChecked
                                else -> Icons.Rounded.Route
                            },
                            contentDescription = stringResource(R.string.cd_roadmap_icon),
                            tint = when {
                                milestone.isCompleted -> MaterialTheme.colorScheme.primary
                                milestone.isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${milestone.code} · ${milestone.title}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (milestone.isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = milestone.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (milestone.isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun categoryIcon(slug: String): ImageVector = when (slug) {
    "buracos-em-vias" -> Icons.Rounded.Construction
    "acumulo-de-lixo" -> Icons.Rounded.DeleteOutline
    "iluminacao-publica" -> Icons.Rounded.Lightbulb
    "vazamentos" -> Icons.Rounded.WaterDrop
    "alagamentos" -> Icons.Rounded.Waves
    "problemas-de-drenagem" -> Icons.Rounded.WaterDrop
    "sinalizacao-danificada" -> Icons.Rounded.Signpost
    "semaforos-quebrados" -> Icons.Rounded.Traffic
    "barreiras-de-acessibilidade" -> Icons.Rounded.AccessibleForward
    else -> Icons.Rounded.Info
}

private fun severityIcon(severity: Severity): ImageVector = when (severity) {
    Severity.LOW -> Icons.Rounded.Info
    Severity.MEDIUM -> Icons.Rounded.WarningAmber
    Severity.HIGH -> Icons.Rounded.ReportProblem
    Severity.CRITICAL -> Icons.Rounded.ErrorOutline
}

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.LOW -> SeverityLowColor
    Severity.MEDIUM -> SeverityMediumColor
    Severity.HIGH -> SeverityHighColor
    Severity.CRITICAL -> SeverityCriticalColor
}

private fun statusIcon(status: IncidentStatus): ImageVector = when (status) {
    IncidentStatus.OPEN -> Icons.Rounded.RadioButtonChecked
    IncidentStatus.UNDER_REVIEW -> Icons.Rounded.FactCheck
    IncidentStatus.IN_PROGRESS -> Icons.Rounded.HourglassTop
    IncidentStatus.RESOLVED -> Icons.Rounded.CheckCircle
    IncidentStatus.REJECTED -> Icons.Rounded.HighlightOff
}

private fun statusColor(status: IncidentStatus): Color = when (status) {
    IncidentStatus.OPEN -> StatusOpenColor
    IncidentStatus.UNDER_REVIEW -> StatusUnderReviewColor
    IncidentStatus.IN_PROGRESS -> StatusInProgressColor
    IncidentStatus.RESOLVED -> StatusResolvedColor
    IncidentStatus.REJECTED -> StatusRejectedColor
}
