package com.pratibimb.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pratibimb.app.ui.PratibimbViewModel
import com.pratibimb.app.ui.debug.DebugScreen
import com.pratibimb.app.ui.home.HomeScreen
import com.pratibimb.app.ui.livetwin.LiveTwinScreen
import com.pratibimb.app.ui.onboarding.PermissionChecklistScreen
import com.pratibimb.app.ui.onboarding.SetupResultModeAScreen
import com.pratibimb.app.ui.onboarding.SetupResultModeBScreen
import com.pratibimb.app.ui.settings.SettingsScreen
import com.pratibimb.app.ui.theme.WarmObsidian
import com.pratibimb.app.ui.tray.HeldTrayScreen

object PratibimbRoutes {
    const val CHECKLIST = "checklist"
    const val SETUP_RESULT_MODE_A = "setup_result_mode_a"
    const val SETUP_RESULT_MODE_B = "setup_result_mode_b"
    const val MAIN_TABS = "main_tabs"
}

@Composable
fun PratibimbApp(
    viewModel: PratibimbViewModel,
    onRequestPostNotifications: () -> Unit,
    onSendTestPing: () -> Unit,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val isModeA by viewModel.isModeA.collectAsStateWithLifecycle()
    val isFocusActive by viewModel.isFocusActive.collectAsStateWithLifecycle()
    val selectedDurationMinutes by viewModel.selectedDurationMinutes.collectAsStateWithLifecycle()
    val remainingSeconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val phoneState by viewModel.phoneState.collectAsStateWithLifecycle()
    val fatigueRisk by viewModel.fatigueRisk.collectAsStateWithLifecycle()
    val autonomy by viewModel.autonomy.collectAsStateWithLifecycle()
    val heldTodayCount by viewModel.heldTodayCount.collectAsStateWithLifecycle()
    val netMinutesProtected by viewModel.netMinutesProtected.collectAsStateWithLifecycle()
    val liveNotifications by viewModel.liveNotifications.collectAsStateWithLifecycle()
    val heldTray by viewModel.heldTray.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val useLivePermissions by viewModel.useLivePermissions.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(AppTab.HOME) }

    NavHost(
        navController = navController,
        startDestination = PratibimbRoutes.CHECKLIST,
        modifier = modifier.fillMaxSize().background(WarmObsidian.Background),
    ) {
        // Screen A2: Permission Checklist
        composable(PratibimbRoutes.CHECKLIST) {
            PermissionChecklistScreen(
                status = permissionStatus,
                onRequestPostNotifications = onRequestPostNotifications,
                onContinue = { modeA ->
                    viewModel.setModeA(modeA)
                    if (modeA) {
                        navController.navigate(PratibimbRoutes.SETUP_RESULT_MODE_A)
                    } else {
                        navController.navigate(PratibimbRoutes.SETUP_RESULT_MODE_B)
                    }
                },
            )
        }

        // Screen A3: Setup Result Mode A
        composable(PratibimbRoutes.SETUP_RESULT_MODE_A) {
            SetupResultModeAScreen(
                onStart = {
                    navController.navigate(PratibimbRoutes.MAIN_TABS) {
                        popUpTo(PratibimbRoutes.CHECKLIST) { inclusive = true }
                    }
                },
            )
        }

        // Screen A4: Setup Result Mode B
        composable(PratibimbRoutes.SETUP_RESULT_MODE_B) {
            SetupResultModeBScreen(
                onStart = {
                    navController.navigate(PratibimbRoutes.MAIN_TABS) {
                        popUpTo(PratibimbRoutes.CHECKLIST) { inclusive = true }
                    }
                },
            )
        }

        // Main App Tabs Scaffold (Screen B1 + Live Twin + Tray + Debug + Settings)
        composable(PratibimbRoutes.MAIN_TABS) {
            Scaffold(
                containerColor = WarmObsidian.Background,
                bottomBar = {
                    AppBottomNav(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        trayCount = heldTray.size,
                        modifier = Modifier.navigationBarsPadding(),
                    )
                },
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (selectedTab) {
                        AppTab.HOME -> {
                            HomeScreen(
                                isFocusActive = isFocusActive,
                                selectedDurationMinutes = selectedDurationMinutes,
                                remainingSeconds = remainingSeconds,
                                isModeA = isModeA,
                                phoneState = phoneState,
                                fatigueRisk = fatigueRisk,
                                autonomy = autonomy,
                                heldTodayCount = heldTodayCount,
                                netMinutesProtected = netMinutesProtected,
                                onSelectDuration = viewModel::selectDuration,
                                onStartFocus = viewModel::startFocus,
                                onStopFocus = viewModel::stopFocus,
                                onOpenPersona = { selectedTab = AppTab.SETTINGS },
                            )
                        }
                        AppTab.LIVE_TWIN -> {
                            LiveTwinScreen(
                                items = liveNotifications,
                                onFeedback = viewModel::submitWorthFeedback,
                            )
                        }
                        AppTab.TRAY -> {
                            HeldTrayScreen(
                                items = heldTray,
                                onReleaseNow = viewModel::releaseHeldItem,
                                onAlwaysDeliver = viewModel::alwaysDeliverSender,
                                onMarkWrong = viewModel::markHeldWrong,
                                onReleaseAll = viewModel::releaseAllDigest,
                            )
                        }
                        AppTab.DEBUG -> {
                            DebugScreen(
                                isModeA = isModeA,
                                phoneState = phoneState,
                                autonomy = autonomy,
                                useLivePermissions = useLivePermissions,
                                onToggleModeA = viewModel::setModeA,
                                onSetPhoneState = viewModel::setPhoneState,
                                onSetAutonomy = viewModel::setAutonomy,
                                onToggleLivePermissions = { viewModel.setLivePermissionMode(it) },
                                onTriggerScenario = viewModel::triggerScenario,
                                onSendTestPing = onSendTestPing,
                                onResetTwin = viewModel::resetAllCounts,
                            )
                        }
                        AppTab.SETTINGS -> {
                            SettingsScreen(
                                selectedPersona = selectedPersona,
                                onSelectPersona = viewModel::setPersona,
                                onDeleteAllData = viewModel::resetAllCounts,
                            )
                        }
                    }
                }
            }
        }
    }
}
