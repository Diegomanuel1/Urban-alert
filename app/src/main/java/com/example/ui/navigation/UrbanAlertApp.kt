package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.admin.AdminRepository
import com.example.data.agencies.AgencyRepository
import com.example.data.auth.AuthRepository
import com.example.data.community.CommunityRepository
import com.example.data.incidents.IncidentRepository
import com.example.data.local.LocalOfflineRepository
import com.example.data.local.UrbanAlertDatabase
import com.example.data.profile.ProfileRepository
import com.example.data.sync.ConnectivityMonitor
import com.example.data.sync.SyncEngine
import com.example.feature.admin.AdminDashboardScreen
import com.example.feature.admin.AdminViewModel
import com.example.feature.auth.AuthScreen
import com.example.feature.auth.ProfileScreen
import com.example.feature.auth.ProfileViewModel
import com.example.feature.foundation.FoundationShellScreen
import com.example.feature.incidents.IncidentDetailScreen
import com.example.feature.map.MapViewModel
import com.example.feature.map.PublicMapScreen
import com.example.feature.offline.OfflineQueueScreen
import com.example.feature.privacy.PrivacyAndMediaSafetyScreen
import com.example.feature.report.ReportFlowScreen
import com.example.feature.report.ReportViewModel
import com.google.firebase.firestore.FirebaseFirestore

private enum class HomeTab {
    MAP,
    PROJECT,
    PRIVACY,
    ACCOUNT,
}

@Composable
fun UrbanAlertApp(
    authRepository: AuthRepository = remember { AuthRepository() },
) {
    val context = LocalContext.current
    val currentUser by authRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = authRepository.currentUser())

    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.MAP) }
    var selectedDetailIncidentId by rememberSaveable { mutableStateOf<String?>(null) }
    var isReportFlowOpen by rememberSaveable { mutableStateOf(false) }
    var isOfflineQueueOpen by rememberSaveable { mutableStateOf(false) }
    var isAdminDashboardOpen by rememberSaveable { mutableStateOf(false) }
    var returnToReportAfterSignIn by rememberSaveable { mutableStateOf(false) }

    val incidentRepository = remember(context) { IncidentRepository(context) }
    val communityRepository = remember(context) { CommunityRepository(context) }
    val agencyRepository = remember(context) { AgencyRepository(context) }
    val adminRepository = remember(context) { AdminRepository(context) }
    val adminInfoFlow = remember(currentUser?.uid) {
        adminRepository.observeAdminAccess(currentUser?.uid)
    }
    val adminInfo by adminInfoFlow.collectAsStateWithLifecycle(initialValue = null)
    val reportViewModel: ReportViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY ausente em CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val remoteRepo = IncidentRepository(db)
                val offlineRepo = LocalOfflineRepository(UrbanAlertDatabase.getInstance(app))
                val syncEngine = SyncEngine(offlineRepo, remoteRepo)
                val connectivityMonitor = ConnectivityMonitor(app)
                ReportViewModel(
                    incidentRepository = remoteRepo,
                    offlineRepository = offlineRepo,
                    syncEngine = syncEngine,
                    connectivityMonitor = connectivityMonitor,
                )
            }
        },
    )

    val isOnline by reportViewModel.isOnline.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        val credentialManager = CredentialManager.create(context)
        authRepository.attemptSilentSignIn(context, credentialManager)
    }

    LaunchedEffect(currentUser, returnToReportAfterSignIn) {
        if (currentUser != null && returnToReportAfterSignIn) {
            returnToReportAfterSignIn = false
            isReportFlowOpen = true
        }
    }

    LaunchedEffect(isOnline, currentUser?.uid) {
        val uid = currentUser?.uid
        if (isOnline && !uid.isNullOrBlank()) {
            reportViewModel.syncPendingNow(authorUid = uid, forceRetryAll = false)
        }
    }

    val currentAdmin = adminInfo
    if (isAdminDashboardOpen && currentAdmin != null) {
        val adminViewModel: AdminViewModel = viewModel(
            key = "admin_${currentAdmin.uid}",
            factory = viewModelFactory {
                initializer {
                    AdminViewModel(
                        adminRepository = adminRepository,
                        adminUid = currentAdmin.uid,
                    )
                }
            },
        )
        AdminDashboardScreen(
            adminInfo = currentAdmin,
            viewModel = adminViewModel,
            onBack = { isAdminDashboardOpen = false },
        )
        return
    }

    val detailId = selectedDetailIncidentId
    if (detailId != null) {
        IncidentDetailScreen(
            incidentId = detailId,
            incidentRepository = incidentRepository,
            communityRepository = communityRepository,
            agencyRepository = agencyRepository,
            currentUserId = currentUser?.uid,
            onRequestSignIn = {
                selectedDetailIncidentId = null
                selectedTab = HomeTab.ACCOUNT
            },
            onBack = { selectedDetailIncidentId = null },
        )
        return
    }

    if (isOfflineQueueOpen) {
        OfflineQueueScreen(
            viewModel = reportViewModel,
            currentUserId = currentUser?.uid,
            onBack = { isOfflineQueueOpen = false },
        )
        return
    }

    if (isReportFlowOpen) {
        ReportFlowScreen(
            viewModel = reportViewModel,
            isSignedIn = currentUser != null,
            currentUserId = currentUser?.uid,
            onRequestSignIn = {
                returnToReportAfterSignIn = true
                isReportFlowOpen = false
                selectedTab = HomeTab.ACCOUNT
            },
            onOpenOfflineQueue = { isOfflineQueueOpen = true },
            onExitToMap = {
                isReportFlowOpen = false
                selectedTab = HomeTab.MAP
            },
        )
        return
    }

    if (selectedTab != HomeTab.MAP) {
        BackHandler { selectedTab = HomeTab.MAP }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.MAP,
                    onClick = { selectedTab = HomeTab.MAP },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == HomeTab.MAP) {
                                Icons.Rounded.Map
                            } else {
                                Icons.Outlined.Map
                            },
                            contentDescription = stringResource(R.string.cd_nav_map),
                        )
                    },
                    label = { Text(stringResource(R.string.nav_map)) },
                    modifier = Modifier.testTag("nav_tab_map"),
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { isReportFlowOpen = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.AddCircle,
                            contentDescription = stringResource(R.string.cd_nav_report),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    label = { Text(stringResource(R.string.nav_report)) },
                    modifier = Modifier.testTag("nav_tab_report"),
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.PROJECT,
                    onClick = { selectedTab = HomeTab.PROJECT },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == HomeTab.PROJECT) {
                                Icons.Rounded.Info
                            } else {
                                Icons.Outlined.Info
                            },
                            contentDescription = stringResource(R.string.cd_nav_explore),
                        )
                    },
                    label = { Text(stringResource(R.string.nav_project)) },
                    modifier = Modifier.testTag("nav_tab_explore"),
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.PRIVACY,
                    onClick = { selectedTab = HomeTab.PRIVACY },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == HomeTab.PRIVACY) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Outlined.Shield
                            },
                            contentDescription = stringResource(R.string.cd_nav_privacy),
                        )
                    },
                    label = { Text(stringResource(R.string.nav_privacy)) },
                    modifier = Modifier.testTag("nav_tab_privacy"),
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.ACCOUNT,
                    onClick = { selectedTab = HomeTab.ACCOUNT },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == HomeTab.ACCOUNT) {
                                Icons.Rounded.Person
                            } else {
                                Icons.Outlined.PersonOutline
                            },
                            contentDescription = stringResource(R.string.cd_nav_account),
                        )
                    },
                    label = { Text(stringResource(R.string.nav_account)) },
                    modifier = Modifier.testTag("nav_tab_account"),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            when (selectedTab) {
                HomeTab.MAP -> {
                    val mapViewModel: MapViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) {
                                    "APPLICATION_KEY ausente em CreationExtras"
                                }
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                MapViewModel(IncidentRepository(db))
                            }
                        },
                    )
                    PublicMapScreen(
                        viewModel = mapViewModel,
                        onOpenIncidentDetail = { id -> selectedDetailIncidentId = id },
                    )
                }
                HomeTab.PROJECT -> {
                    FoundationShellScreen()
                }
                HomeTab.PRIVACY -> {
                    PrivacyAndMediaSafetyScreen()
                }
                HomeTab.ACCOUNT -> {
                    val user = currentUser
                    if (user == null) {
                        AuthScreen(
                            authRepository = authRepository,
                            onContinueAsGuest = {
                                returnToReportAfterSignIn = false
                                selectedTab = HomeTab.MAP
                            },
                            onAuthSuccess = { },
                        )
                    } else {
                        val uid = user.uid
                        key(uid) {
                            val profileViewModel: ProfileViewModel = viewModel(
                                key = uid,
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY]) {
                                            "APPLICATION_KEY ausente em CreationExtras"
                                        }
                                        val databaseId = app.getString(R.string.firestore_database_id)
                                        val db = FirebaseFirestore.getInstance(databaseId)
                                        ProfileViewModel(
                                            profileRepository = ProfileRepository(db),
                                            authRepository = authRepository,
                                            currentUserId = uid,
                                        )
                                    }
                                },
                            )
                            ProfileScreen(
                                viewModel = profileViewModel,
                                fallbackEmail = user.email,
                                fallbackPhotoUrl = user.photoUrl?.toString(),
                                adminInfo = adminInfo,
                                onOpenAdminPanel = { isAdminDashboardOpen = true },
                            )
                        }
                    }
                }
            }
        }
    }
}
