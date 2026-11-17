package com.example.feature.report

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.core.agent.AgentChatMessage
import com.example.core.agent.AgentMessageRole
import com.example.core.agent.AgentStep
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportFlowScreen(
    viewModel: ReportViewModel,
    isSignedIn: Boolean,
    currentUserId: String?,
    onRequestSignIn: () -> Unit,
    onOpenOfflineQueue: () -> Unit,
    onExitToMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step by viewModel.step.collectAsStateWithLifecycle()

    when (step) {
        AgentStep.COLLECTING_INPUT,
        AgentStep.ANALYZING_AI,
        -> {
            ReportChatScreen(
                viewModel = viewModel,
                onOpenOfflineQueue = onOpenOfflineQueue,
                onBackToMap = onExitToMap,
                modifier = modifier,
            )
        }
        AgentStep.CITIZEN_REVIEW,
        AgentStep.PUBLISHING,
        AgentStep.QUEUED_OFFLINE,
        AgentStep.PUBLISHED,
        -> {
            IncidentReviewScreen(
                viewModel = viewModel,
                isSignedIn = isSignedIn,
                currentUserId = currentUserId,
                onRequestSignIn = onRequestSignIn,
                onOpenOfflineQueue = onOpenOfflineQueue,
                onBackToMap = onExitToMap,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReportChatScreen(
    viewModel: ReportViewModel,
    onOpenOfflineQueue: () -> Unit,
    onBackToMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackToMap)

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val isBusyTool by viewModel.isBusyTool.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showAddressDialog by rememberSaveable { mutableStateOf(false) }
    var addressInput by rememberSaveable { mutableStateOf("") }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.attachPhotoFromUri(context, uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            viewModel.attachPhotoFromUri(context, uri) {
                file.delete()
            }
        } else {
            file?.delete()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.captureCurrentGps(context)
        }
    }

    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text(stringResource(R.string.report_address_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    placeholder = { Text(stringResource(R.string.report_address_dialog_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_address_dialog_input"),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAddressDialog = false
                        viewModel.resolveAddress(context, addressInput)
                        addressInput = ""
                    },
                    modifier = Modifier.testTag("report_address_dialog_confirm"),
                ) {
                    Text("Buscar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddressDialog = false }) {
                    Text(stringResource(R.string.profile_delete_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_chat_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToMap,
                        modifier = Modifier.testTag("report_chat_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_button),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenOfflineQueue,
                        modifier = Modifier.testTag("report_offline_queue_button"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingSyncCount > 0) {
                                    Badge { Text(pendingSyncCount.toString()) }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudQueue,
                                contentDescription = stringResource(R.string.cd_offline_queue_icon),
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.startFreshReport() },
                        modifier = Modifier.testTag("report_new_session_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.report_new_session),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
            ) {
                if (!isOnline) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                if (errorBanner != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = errorBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.clearError() }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                )
                            }
                        }
                    }
                }

                // Cartão compacto de status do rascunho
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = stringResource(R.string.cd_security_icon),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        val photoLabel = if (draft.sanitizedPhotoPath != null) "Foto OK (EXIF limpo)" else "Sem foto"
                        val locLabel = draft.addressLabel ?: "Local pendente"
                        Text(
                            text = "$photoLabel · $locLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(message = msg)
                    }

                    if (step == AgentStep.ANALYZING_AI || isBusyTool) {
                        item {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = if (step == AgentStep.ANALYZING_AI) {
                                        stringResource(R.string.report_analyzing_label)
                                    } else {
                                        "Processando ferramenta..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Barra inferior com ferramentas do agente + campo de mensagem + CTAs de análise
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = {
                                val tempFile = createTempCameraFile(context)
                                pendingCameraFile = tempFile
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    tempFile,
                                )
                                cameraLauncher.launch(uri)
                            },
                            enabled = step != AgentStep.ANALYZING_AI && !isBusyTool,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(stringResource(R.string.report_take_photo)) },
                            modifier = Modifier.testTag("report_camera_chip"),
                        )

                        AssistChip(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                            enabled = step != AgentStep.ANALYZING_AI && !isBusyTool,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(stringResource(R.string.report_pick_photo)) },
                            modifier = Modifier.testTag("report_gallery_chip"),
                        )

                        AssistChip(
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
                                    viewModel.captureCurrentGps(context)
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                        ),
                                    )
                                }
                            },
                            enabled = step != AgentStep.ANALYZING_AI && !isBusyTool,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(stringResource(R.string.report_use_gps)) },
                            modifier = Modifier.testTag("report_gps_chip"),
                        )

                        AssistChip(
                            onClick = { showAddressDialog = true },
                            enabled = step != AgentStep.ANALYZING_AI && !isBusyTool,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PinDrop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(stringResource(R.string.report_use_address)) },
                            modifier = Modifier.testTag("report_address_chip"),
                        )

                        AssistChip(
                            onClick = { viewModel.useSalvadorCenter() },
                            enabled = step != AgentStep.ANALYZING_AI && !isBusyTool,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.LocationCity,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            label = { Text(stringResource(R.string.report_use_salvador_center)) },
                            modifier = Modifier.testTag("report_center_ssa_chip"),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text(stringResource(R.string.report_message_placeholder)) },
                            maxLines = 3,
                            enabled = step != AgentStep.ANALYZING_AI,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("report_chat_input"),
                        )
                        FilledTonalIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendCitizenMessage(inputText)
                                    inputText = ""
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = inputText.isNotBlank() && step != AgentStep.ANALYZING_AI,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("report_send_message_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.cd_send_message),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.goToManualReview() },
                            enabled = step != AgentStep.ANALYZING_AI,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("report_manual_review_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.report_manual_button))
                        }

                        Button(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendCitizenMessage(inputText)
                                    inputText = ""
                                    focusManager.clearFocus()
                                }
                                viewModel.runAiAnalysis()
                            },
                            enabled = step != AgentStep.ANALYZING_AI && !isBusyTool,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("report_analyze_ai_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.report_analyze_ai_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AgentChatMessage) {
    val isCitizen = message.role == AgentMessageRole.CITIZEN
    val isSystem = message.role == AgentMessageRole.SYSTEM_NOTICE

    val containerColor = when {
        isCitizen -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isCitizen -> MaterialTheme.colorScheme.onPrimaryContainer
        isSystem -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isCitizen) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!message.imagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(message.imagePath),
                        contentDescription = stringResource(R.string.cd_attached_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
    }
}

private fun createTempCameraFile(context: Context): File {
    val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    return File(dir, "capture_${System.currentTimeMillis()}.jpg")
}
