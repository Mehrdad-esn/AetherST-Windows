package io.github.immaghzbad.aetherst.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.model.OnboardingStep
import io.github.immaghzbad.aetherst.ui.AetherViewModel
import io.github.immaghzbad.aetherst.ui.OnboardingViewModel
import io.github.immaghzbad.aetherst.ui.components.IosToast
import kotlin.math.roundToInt

private val IosNavBackground = Color(0xFF1C1C1E)
private val IosNavActiveBlue = Color(0xFF007AFF)
private val IosNavInactiveGrey = Color(0xFF8E8E93)
private val BarContentHeight = 80.dp
private val ButtonSize = 56.dp
private val ButtonCenterY = 22.dp
private val CircleGap = 6.dp
private val BarTopY = 20.dp
private val ItemBottomPadding = 10.dp

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(packageName) ?: true
}

@SuppressLint("BatteryLife")
@Composable
fun MainScreen(viewModel: AetherViewModel) {
    val context = LocalContext.current
    val activity = context.findComponentActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val onboardingViewModel: OnboardingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return OnboardingViewModel(context.applicationContext) as T
            }
        },
    )

    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsStateWithLifecycle()
    val onboardingState by onboardingViewModel.state.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val crashLog by viewModel.crashLog.collectAsStateWithLifecycle()
    val currentStep by rememberUpdatedState(onboardingState.currentStep)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (currentStep == OnboardingStep.BATTERY_OPTIMIZATION && context.isIgnoringBatteryOptimizations()) {
                    onboardingViewModel.moveToNextStep()
                }
                viewModel.checkBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val toastState by viewModel.toastState.collectAsStateWithLifecycle()

    SideEffect {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity?.window?.isNavigationBarContrastEnforced = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)

        if (!isOnboardingComplete) {
            val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                val intent = VpnService.prepare(context)
                if (intent == null) onboardingViewModel.moveToNextStep()
            }
            val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) onboardingViewModel.moveToNextStep() else onboardingViewModel.showNotificationError()
            }

            OnboardingScreen(
                state = onboardingState,
                onGetStarted = { onboardingViewModel.moveToNextStep() },
                onRetryRegistration = { onboardingViewModel.startProtocolTests() },
                onCancelRegistration = { onboardingViewModel.cancelTests() },
                onUpdateScanMode = { onboardingViewModel.updateScanMode(it) },
                onRequestVpnPermission = {
                    val intent = VpnService.prepare(context)
                    if (intent != null) vpnLauncher.launch(intent) else onboardingViewModel.moveToNextStep()
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onboardingViewModel.moveToNextStep()
                    }
                },
                onRequestBatteryOptimization = {
                    if (context.isIgnoringBatteryOptimizations()) {
                        onboardingViewModel.moveToNextStep()
                    } else {
                        runCatching {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }
                },
                onFinish = onboardingViewModel::moveToNextStep
            )
        } else if (crashLog != null) {
            CrashReportScreen(
                crashLog = crashLog!!,
                onRestart = { viewModel.clearCrashLog() },
                onShowToast = { viewModel.showToast(it) }
            )
        } else if (updateInfo != null) {
            UpdateScreen(
                info = updateInfo!!,
                onDismiss = { viewModel.dismissUpdate() },
                scaleFactor = scaleFactor
            )
        } else {
            DashboardContent(viewModel)
        }

        IosToast(
            message = toastState?.message,
            isError = toastState?.isError ?: false,
            scaleFactor = scaleFactor
        )
    }
}

@SuppressLint("BatteryLife")
@Composable
private fun DashboardContent(viewModel: AetherViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSplitTunneling by remember { mutableStateOf(false) }
    
    val config by viewModel.config.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val sessionTraffic by viewModel.sessionTraffic.collectAsStateWithLifecycle()
    val ipInfo by viewModel.ipInfo.collectAsStateWithLifecycle()
    val pingState by viewModel.pingState.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsStateWithLifecycle()
    val importConflictRules by viewModel.importConflictRules.collectAsStateWithLifecycle()
    val importErrorMessage by viewModel.importErrorMessage.collectAsStateWithLifecycle()
    val isOptimizingMtu by viewModel.isOptimizingMtu.collectAsStateWithLifecycle()
    val isWaitingForLoginCode by viewModel.isWaitingForLoginCode.collectAsStateWithLifecycle()
    val scrollToZeroTrust by viewModel.scrollToZeroTrust.collectAsStateWithLifecycle()
    var showRoutingRules by remember { mutableStateOf(false) }

    LaunchedEffect(scrollToZeroTrust) {
        if (scrollToZeroTrust) {
            selectedTab = 1
            showSplitTunneling = false
            showRoutingRules = false
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleVpn(context) {}
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            viewModel.showToast("Notification permission required", true)
        }
    }

    fun handleVpnToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.toggleVpn(context) {
            val intent = VpnService.prepare(context)
            if (intent != null) vpnPermissionLauncher.launch(intent)
        }
    }

    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val saveableStateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)

        Box(modifier = Modifier.fillMaxSize()) {
            val targetScreen = if (showRoutingRules) 100 else if (showSplitTunneling) 99 else selectedTab
            AnimatedContent(
                targetState = targetScreen,
                transitionSpec = {
                    val duration = 350
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { it } + fadeIn(animationSpec = tween(duration)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { -it / 2 } + fadeOut(animationSpec = tween(duration)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { -it } + fadeIn(animationSpec = tween(duration)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(duration, easing = FastOutSlowInEasing)) { it / 2 } + fadeOut(animationSpec = tween(duration)))
                    }
                },
                label = "screen_transition"
            ) { tab ->
                saveableStateHolder.SaveableStateProvider(tab) {
                    when (tab) {
                        0 -> DashboardScreen(
                            config = config,
                            connectionStatus = connectionStatus,
                            elapsedSeconds = elapsedSeconds,
                            sessionTraffic = sessionTraffic,
                            ipInfo = ipInfo,
                            pingState = pingState,
                            onToggleVpn = { handleVpnToggle() },
                            onUpdateProtocol = { proto -> viewModel.updateConfig(config.copy(protocol = proto)) },
                            onRefreshIpInfo = { viewModel.refreshIpInfo() },
                            onRefreshPing = { viewModel.refreshPing() },
                            onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                            bottomContentPadding = BarContentHeight + navBarHeight
                        )
                        1 -> SettingsScreen(
                            config = config,
                            isBatteryOptimized = isBatteryOptimized,
                            scrollToSection = scrollToZeroTrust,
                            onSectionScrolled = { viewModel.onZeroTrustScrolled() },
                            onUpdateConfig = { viewModel.updateConfig(it) },
                            onUpdateTunnelEngine = { viewModel.updateTunnelEngine(it) },
                            onApplyPreset = { preset ->
                                viewModel.applyPreset(preset)
                            },
                            onOpenSplitTunneling = { showSplitTunneling = true },
                            onOpenRoutingRules = { showRoutingRules = true },
                            onResetAll = { viewModel.resetAllSettings() },
                            onExportBackup = { viewModel.exportFullBackup(context) },
                            onImportBackup = { viewModel.importFullBackup(it, context) },
                            onOptimizeMtu = { viewModel.optimizeMtu() },
                            isOptimizingMtu = isOptimizingMtu,
                            onRequestBatteryOptimization = {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            },
                            onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                            bottomContentPadding = BarContentHeight + navBarHeight
                        )
                        2 -> LogsScreen(viewModel = viewModel, onShowToast = { msg, err -> viewModel.showToast(msg, err) }, bottomContentPadding = BarContentHeight + navBarHeight)
                        3 -> AboutUsScreen(bottomContentPadding = BarContentHeight + navBarHeight)
                        99 -> SplitTunnelingScreen(
                            apps = installedApps,
                            excludedPackages = config.excludedPackages,
                            blockedPackages = config.blockedPackages,
                            onUpdateMode = { pkg, mode -> viewModel.updateAppSplitTunnelingMode(pkg, mode) },
                            onBack = { showSplitTunneling = false },
                            scaleFactor = scaleFactor
                        )
                        100 -> RoutingRulesScreen(
                            rules = config.routingRules,
                            importConflictRules = importConflictRules,
                            importErrorMessage = importErrorMessage,
                            onAddRule = { pattern, mode -> viewModel.addRoutingRule(pattern, mode) },
                            onRemoveRule = { pattern -> viewModel.removeRoutingRule(pattern) },
                            onUpdateMode = { pattern, mode -> viewModel.updateRoutingRuleMode(pattern, mode) },
                            onClearAllRules = { viewModel.clearAllRoutingRules() },
                            onCleanPattern = { viewModel.cleanRoutingPattern(it) },
                            onValidatePattern = { viewModel.isValidRoutingPattern(it) },
                            onExportRules = { viewModel.exportRoutingRules(context) },
                            onImportRules = { viewModel.importRoutingRules(it, context) },
                            onResolveConflict = { rules, replace -> viewModel.resolveConflict(rules, replace) },
                            onCancelImport = { viewModel.cancelImport() },
                            onClearImportError = { viewModel.clearImportError() },
                            onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                            onBack = { showRoutingRules = false },
                            scaleFactor = scaleFactor
                        )
                    }
                }
            }
        }
        if (!showSplitTunneling && !showRoutingRules) {
            CurvedNavBar(selectedTab = selectedTab, navBarHeight = navBarHeight, onTabSelected = { selectedTab = it }, modifier = Modifier.align(Alignment.BottomCenter))
        }

        if (isWaitingForLoginCode) {
            ZeroTrustLoginDialog(
                onSubmit = { viewModel.submitLoginCode(it) },
                onDismiss = { viewModel.submitLoginCode("") },
                scaleFactor = scaleFactor
            )
        }
    }
}

@Composable
fun ZeroTrustLoginDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float
) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { focusManager.clearFocus() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width((320 * scaleFactor).dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(IosNavActiveBlue.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = IosNavActiveBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = "Zero Trust Login",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = (20 * scaleFactor).sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "A one-time code was sent to your email. Please enter it below to authorize this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosNavInactiveGrey,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = (13 * scaleFactor).sp,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                androidx.compose.foundation.text.BasicTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = IosNavActiveBlue,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { 
                        if (code.length == 6) onSubmit(code)
                    }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(IosNavActiveBlue),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (code.isEmpty()) {
                                Text("000000", color = Color.White.copy(alpha = 0.05f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                            }
                            innerTextField()
                        }
                    }
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = IosNavInactiveGrey, fontWeight = FontWeight.Medium)
                    }
                    androidx.compose.material3.Button(
                        onClick = { if (code.length == 6) onSubmit(code) },
                        enabled = code.length == 6,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = IosNavActiveBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Verify", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurvedNavBar(
    selectedTab: Int,
    navBarHeight: androidx.compose.ui.unit.Dp,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)
        
        val scaledBarHeight = (BarContentHeight.value * scaleFactor).dp
        val scaledButtonSize = (ButtonSize.value * scaleFactor).dp
        val scaledButtonCenterY = (ButtonCenterY.value * scaleFactor).dp
        val scaledCircleGap = (CircleGap.value * scaleFactor).dp
        val scaledBarTopY = (BarTopY.value * scaleFactor).dp
        val scaledItemBottomPadding = (ItemBottomPadding.value * scaleFactor).dp

        val tabs = listOf(
            "Dashboard" to Icons.Default.Dashboard,
            "Settings" to Icons.Default.Settings,
            "Logs" to Icons.Default.Code,
            "About" to Icons.Default.Info
        )
        val tabCount = tabs.size
        var barWidthPx by remember { mutableIntStateOf(0) }
        
        val indicatorOffset by animateFloatAsState(
            targetValue = selectedTab.toFloat(),
            animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
            label = "indicatorOffset"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaledBarHeight + navBarHeight)
                .onSizeChanged { barWidthPx = it.width }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(elevation = (15 * scaleFactor).dp, spotColor = Color.Black.copy(alpha = 0.5f))
            ) {
                val tabWidth = size.width / tabCount
                val centerX = (indicatorOffset * tabWidth) + (tabWidth / 2)
                val barTop = scaledBarTopY.toPx()
                val notchBottom = scaledButtonCenterY.toPx() + (scaledButtonSize.toPx() / 2f) + scaledCircleGap.toPx()
                val shoulderWidth = (45.dp.toPx() * scaleFactor)
                
                val barShape = Path().apply {
                    moveTo(0f, barTop)
                    lineTo(centerX - shoulderWidth, barTop)
                    
                    cubicTo(
                        centerX - (40.dp.toPx() * scaleFactor), barTop,
                        centerX - (38.dp.toPx() * scaleFactor), barTop + (2.dp.toPx() * scaleFactor),
                        centerX - (35.dp.toPx() * scaleFactor), barTop + (10.dp.toPx() * scaleFactor)
                    )
                    cubicTo(
                        centerX - (28.dp.toPx() * scaleFactor), barTop + (26.dp.toPx() * scaleFactor),
                        centerX - (20.dp.toPx() * scaleFactor), notchBottom,
                        centerX, notchBottom
                    )
                    cubicTo(
                        centerX + (20.dp.toPx() * scaleFactor), notchBottom,
                        centerX + (28.dp.toPx() * scaleFactor), barTop + (26.dp.toPx() * scaleFactor),
                        centerX + (35.dp.toPx() * scaleFactor), barTop + (10.dp.toPx() * scaleFactor)
                    )
                    cubicTo(
                        centerX + (38.dp.toPx() * scaleFactor), barTop + (2.dp.toPx() * scaleFactor),
                        centerX + (40.dp.toPx() * scaleFactor), barTop,
                        centerX + shoulderWidth, barTop
                    )
                    
                    lineTo(size.width, barTop)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = barShape,
                    color = IosNavBackground.copy(alpha = 0.94f),
                    style = Fill
                )
            }

            Box(
                modifier = Modifier
                    .size(scaledButtonSize + (scaledCircleGap * 2))
                    .offset {
                        val tabWidth = barWidthPx.toFloat() / tabCount
                        val outerSize = scaledButtonSize.toPx() + scaledCircleGap.toPx() * 2f
                        IntOffset(
                            (indicatorOffset * tabWidth + (tabWidth / 2) - (outerSize / 2f)).roundToInt(),
                            (scaledButtonCenterY.toPx() - outerSize / 2f).roundToInt()
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconScale by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
                    label = "iconScale"
                )
                
                Box(
                    modifier = Modifier
                        .size(scaledButtonSize)
                        .shadow(
                            elevation = (16 * scaleFactor).dp,
                            shape = CircleShape,
                            spotColor = IosNavActiveBlue.copy(alpha = 0.8f)
                        )
                        .background(IosNavActiveBlue, CircleShape)
                        .border(
                            width = (1.5 * scaleFactor).dp,
                            color = Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tabs[selectedTab].second,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size((28 * scaleFactor).dp)
                            .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledBarHeight)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.Bottom
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    val isSelected = selectedTab == index
                    
                    val contentAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.6f,
                        label = "contentAlpha"
                    )
                    
                    val textOffset by animateFloatAsState(
                        targetValue = if (isSelected) 0f else 10f,
                        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
                        label = "textOffset"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(index) }
                            .padding(bottom = scaledItemBottomPadding),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.graphicsLayer(alpha = contentAlpha)
                        ) {
                            if (!isSelected) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = IosNavInactiveGrey,
                                    modifier = Modifier.size((24 * scaleFactor).dp)
                                )
                                Spacer(modifier = Modifier.height((6 * scaleFactor).dp))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = (10 * scaleFactor).sp,
                                color = if (isSelected) IosNavActiveBlue else IosNavInactiveGrey,
                                modifier = Modifier.graphicsLayer(translationY = textOffset)
                            )
                        }
                    }
                }
            }
        }
    }
}
