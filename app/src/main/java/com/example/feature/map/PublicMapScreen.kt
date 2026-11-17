package com.example.feature.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.config.PilotCityConfig
import com.example.core.model.GeoBoundingBox
import com.example.core.model.IncidentStatus
import com.example.core.model.Severity
import com.example.data.incidents.IncidentDocument
import com.example.feature.incidents.DetailBadge
import com.example.feature.incidents.severityAccent
import com.example.feature.incidents.severityVector
import com.example.feature.incidents.statusAccent
import com.example.feature.incidents.statusVector
import com.example.ui.theme.CartographicGridLineLight
import com.example.ui.theme.CartographicSage
import com.example.ui.theme.SeverityCriticalColor
import com.example.ui.theme.SeverityHighColor
import com.example.ui.theme.SeverityLowColor
import com.example.ui.theme.SeverityMediumColor
import com.example.ui.theme.UrbanEmerald
import com.example.ui.theme.UrbanGraphite
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublicMapScreen(
    viewModel: MapViewModel,
    onOpenIncidentDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val contentState by viewModel.mapContentState.collectAsStateWithLifecycle()
    val selectedIncident by viewModel.selectedIncident.collectAsStateWithLifecycle()
    val isBusyLocationOrSearch by viewModel.isBusyLocationOrSearch.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFiltersPanel by rememberSaveable { mutableStateOf(false) }
    var showAreaListSheet by rememberSaveable { mutableStateOf(false) }
    var permissionDeniedNote by rememberSaveable { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            permissionDeniedNote = false
            viewModel.moveToCurrentDeviceLocation(context)
        } else {
            permissionDeniedNote = true
        }
    }

    // Configura cache privado do OSMDroid sem permissões externas
    val mapView = remember(context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles").apply { mkdirs() }
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0
            maxZoomLevel = 19.5
            overlayManager.tilesOverlay.loadingBackgroundColor = CartographicSage.toArgb()
            overlayManager.tilesOverlay.loadingLineColor = CartographicGridLineLight.toArgb()
            controller.setZoom(PilotCityConfig.salvador.defaultZoom.toDouble())
            controller.setCenter(
                GeoPoint(
                    PilotCityConfig.salvador.center.latitude,
                    PilotCityConfig.salvador.center.longitude,
                ),
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                val box = mapView.boundingBox
                viewModel.updateViewport(
                    bounds = GeoBoundingBox(
                        north = box.latNorth,
                        south = box.latSouth,
                        east = box.lonEast,
                        west = box.lonWest,
                    ),
                    zoomLevel = mapView.zoomLevelDouble,
                )
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val box = mapView.boundingBox
                viewModel.updateViewport(
                    bounds = GeoBoundingBox(
                        north = box.latNorth,
                        south = box.latSouth,
                        east = box.lonEast,
                        west = box.lonWest,
                    ),
                    zoomLevel = mapView.zoomLevelDouble,
                )
                return false
            }
        }
        mapView.addMapListener(listener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.removeMapListener(listener)
            mapView.onDetach()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.cameraTarget.collect { (coordinate, targetZoom) ->
            mapView.controller.animateTo(
                GeoPoint(coordinate.latitude, coordinate.longitude),
                targetZoom,
                450L,
            )
        }
    }

    // Atualiza marcadores e clusters no MapView sempre que os nós renderizáveis mudarem
    LaunchedEffect(contentState) {
        val nodes = (contentState as? MapContentState.Ready)?.renderNodes ?: emptyList()
        mapView.overlays.clear()
        nodes.forEach { node ->
            when (node) {
                is MapRenderNode.SinglePin -> {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(node.incident.latitude, node.incident.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = node.incident.title
                        icon = createSinglePinDrawable(
                            context = context,
                            severity = node.incident.resolvedSeverity(),
                        )
                        setOnMarkerClickListener { _, _ ->
                            viewModel.selectIncident(node.incident)
                            mapView.controller.animateTo(position)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }
                is MapRenderNode.ClusterPin -> {
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(node.center.latitude, node.center.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "${node.count} ocorrências"
                        icon = createClusterDrawable(
                            context = context,
                            count = node.count,
                            highestSeverity = node.highestSeverity,
                        )
                        setOnMarkerClickListener { _, _ ->
                            val nextZoom = (mapView.zoomLevelDouble + 2.0).coerceAtMost(17.0)
                            mapView.controller.animateTo(position, nextZoom, 450L)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }
            }
        }
        mapView.invalidate()
    }

    val visibleIncidents = (contentState as? MapContentState.Ready)?.incidents.orEmpty()

    if (showAreaListSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAreaListSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.map_area_list_title, visibleIncidents.size),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (visibleIncidents.isEmpty()) {
                    Text(
                        text = stringResource(R.string.map_empty_area_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(visibleIncidents, key = { it.id }) { incident ->
                            IncidentPreviewRow(
                                incident = incident,
                                onClick = {
                                    showAreaListSheet = false
                                    viewModel.selectIncident(incident)
                                    onOpenIncidentDetail(incident.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Mapa nativo em tela cheia
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("salvador_public_map"),
        )

        // 2. Painel flutuante superior (busca + GPS + filtros)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.map_search_placeholder),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.cd_search_location),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.cd_clear_search),
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                viewModel.searchLocationInSalvador(context, searchQuery)
                            },
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input"),
                    )

                    FilledTonalIconButton(
                        onClick = { showFiltersPanel = !showFiltersPanel },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_toggle_filters_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = stringResource(R.string.cd_toggle_filters),
                            tint = if (filterState.categorySlug != null || filterState.statusFilter != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                viewModel.moveToCurrentDeviceLocation(context)
                            } else {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("map_my_location_button"),
                    ) {
                        if (isBusyLocationOrSearch) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = stringResource(R.string.cd_my_location),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showFiltersPanel) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.map_filters_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (filterState.categorySlug != null || filterState.statusFilter != null) {
                                TextButton(onClick = { viewModel.clearAllFilters() }) {
                                    Text(stringResource(R.string.map_clear_filters))
                                }
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PilotCityConfig.initialCategories, key = { it.slug }) { category ->
                                val selected = filterState.categorySlug == category.slug
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectCategoryFilter(category.slug) },
                                    label = { Text(category.title) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                listOf(
                                    IncidentStatus.OPEN,
                                    IncidentStatus.UNDER_REVIEW,
                                    IncidentStatus.IN_PROGRESS,
                                    IncidentStatus.RESOLVED,
                                ),
                            ) { status ->
                                val selected = filterState.statusFilter == status
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.selectStatusFilter(status) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = statusVector(status),
                                            contentDescription = stringResource(R.string.cd_status_icon),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = { Text(stringResource(status.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }

            if (permissionDeniedNote) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.map_location_permission_denied),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { permissionDeniedNote = false }) {
                            Text("OK")
                        }
                    }
                }
            }

            val banner = statusBanner
            if (banner != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    border = BorderStroke(
                        1.dp,
                        if (banner.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = banner.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (banner.isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.clearBanner() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cd_clear_search),
                            )
                        }
                    }
                }
            }
        }

        // 3. Controles laterais direitos de zoom e retorno a Salvador
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                IconButton(
                    onClick = { mapView.controller.zoomIn() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("map_zoom_in_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.cd_zoom_in),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                IconButton(
                    onClick = { mapView.controller.zoomOut() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("map_zoom_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.cd_zoom_out),
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.95f),
                shadowElevation = 4.dp,
            ) {
                IconButton(
                    onClick = { viewModel.recenterOnSalvador() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("map_recenter_salvador_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationSearching,
                        contentDescription = stringResource(R.string.cd_recenter_salvador),
                        tint = MaterialTheme.colorScheme.onSecondary,
                    )
                }
            }
        }

        // 4. Painel flutuante inferior (resumo da área ou ocorrência selecionada)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            val activeIncident = selectedIncident
            if (activeIncident != null) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_incident_card"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = activeIncident.categoryTitle(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            IconButton(
                                onClick = { viewModel.selectIncident(null) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                )
                            }
                        }

                        Text(
                            text = activeIncident.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val severity = activeIncident.resolvedSeverity()
                            val status = activeIncident.resolvedStatus()
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ThumbUpOffAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "${activeIncident.stillExistsCount} confirmações · ${activeIncident.elapsedLabel()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Button(
                                onClick = { onOpenIncidentDetail(activeIncident.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("open_incident_detail_button"),
                            ) {
                                Text(stringResource(R.string.map_view_details_button))
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("map_area_summary_bar"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            when (val current = contentState) {
                                MapContentState.Loading -> {
                                    Text(
                                        text = stringResource(R.string.map_loading_area),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                is MapContentState.Error -> {
                                    Text(
                                        text = current.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                is MapContentState.Ready -> {
                                    Text(
                                        text = if (current.incidents.isEmpty()) {
                                            stringResource(R.string.map_empty_area_title)
                                        } else {
                                            stringResource(
                                                R.string.map_area_count_title,
                                                current.incidents.size,
                                            )
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (current.incidents.isEmpty()) {
                                            stringResource(R.string.map_empty_area_subtitle)
                                        } else {
                                            stringResource(R.string.map_tap_pin_hint)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { showAreaListSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("map_area_list_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.map_list_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentPreviewRow(
    incident: IncidentDocument,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = incident.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${incident.categoryTitle()} · ${incident.elapsedLabel()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        }
    }
}

private fun createSinglePinDrawable(
    context: Context,
    severity: Severity,
): BitmapDrawable {
    val sizePx = (36 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fillColor = when (severity) {
        Severity.LOW -> SeverityLowColor.toArgb()
        Severity.MEDIUM -> SeverityMediumColor.toArgb()
        Severity.HIGH -> SeverityHighColor.toArgb()
        Severity.CRITICAL -> SeverityCriticalColor.toArgb()
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val cx = sizePx / 2f
    val cy = sizePx * 0.42f
    val radius = sizePx * 0.36f
    canvas.drawCircle(cx, cy, radius + 3f, borderPaint)
    canvas.drawCircle(cx, cy, radius, pinPaint)
    canvas.drawCircle(cx, cy, radius * 0.35f, dotPaint)
    return BitmapDrawable(context.resources, bitmap)
}

private fun createClusterDrawable(
    context: Context,
    count: Int,
    highestSeverity: Severity,
): BitmapDrawable {
    val sizePx = (44 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val ringColor = when (highestSeverity) {
        Severity.CRITICAL -> SeverityCriticalColor.toArgb()
        Severity.HIGH -> SeverityHighColor.toArgb()
        else -> UrbanEmerald.toArgb()
    }
    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ringColor
        style = Paint.Style.FILL
    }
    val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = UrbanGraphite.toArgb()
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.34f
        typeface = Typeface.DEFAULT_BOLD
    }
    val center = sizePx / 2f
    canvas.drawCircle(center, center, center - 2f, outerPaint)
    canvas.drawCircle(center, center, center * 0.78f, innerPaint)
    val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(count.toString(), center, textY, textPaint)
    return BitmapDrawable(context.resources, bitmap)
}
