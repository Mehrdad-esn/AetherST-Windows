package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.immaghzbad.aetherst.model.RoutingMode
import io.github.immaghzbad.aetherst.ui.AppNavigation
import io.github.immaghzbad.aetherst.ui.AetherViewModel
import io.github.immaghzbad.aetherst.ui.OnboardingViewModel
import io.github.immaghzbad.aetherst.ui.components.IosToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SubScreen { ROUTING }

private val IosNavBackground = Color(0xFF1C1C1E)
private val IosNavActiveBlue = Color(0xFF007AFF)
private val IosNavInactiveGrey = Color(0xFF8E8E93)
private val BarContentHeight = 80.dp
private val ButtonSize = 56.dp
private val ButtonCenterY = 22.dp
private val CircleGap = 6.dp
private val BarTopY = 20.dp
private val ItemBottomPadding = 10.dp

@Composable
fun MainScreen(viewModel: AetherViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var subScreen by remember { mutableStateOf<SubScreen?>(null) }
    var pendingOpenVpnPath by remember { mutableStateOf<String?>(null) }
    var showOpenVpnCredentials by remember { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val navigationRequest by viewModel.navigationRequest.collectAsState()
    LaunchedEffect(navigationRequest) {
        navigationRequest?.let { destination ->
            subScreen = null
            when (destination) {
                AppNavigation.SETTINGS -> selectedTab = 1
                AppNavigation.ROUTING -> {
                    selectedTab = 1
                    subScreen = SubScreen.ROUTING
                }
            }
            viewModel.clearNavigation()
        }
    }

    val toastState by viewModel.toastState.collectAsState()
    val config by viewModel.config.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val sessionTraffic by viewModel.sessionTraffic.collectAsState()
    val ipInfo by viewModel.ipInfo.collectAsState()
    val pingState by viewModel.pingState.collectAsState()
    val isWaitingForLoginCode by viewModel.isWaitingForLoginCode.collectAsState()
    val isOptimizingMtu by viewModel.isOptimizingMtu.collectAsState()
    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val crashLog by viewModel.crashLog.collectAsState()
    val importConflictRules by viewModel.importConflictRules.collectAsState()
    val importErrorMessage by viewModel.importErrorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    val onboardingViewModel = remember { OnboardingViewModel() }
    val onboardingState by onboardingViewModel.state.collectAsState()

    if (!isOnboardingComplete) {
        OnboardingScreen(
            state = onboardingState,
            onGetStarted = { onboardingViewModel.moveToNextStep() },
            onRetryRegistration = { onboardingViewModel.startProtocolTests() },
            onCancelRegistration = { onboardingViewModel.cancelTests() },
            onUpdateScanMode = { onboardingViewModel.updateScanMode(it) },
            onFinish = { onboardingViewModel.moveToNextStep() }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = this.maxWidth
        val scaleFactor = (screenWidth.value / 411f).coerceIn(0.7f, 1.1f)

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = selectedTab,
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
                            onToggleVpn = { viewModel.toggleConnection() },
                            onUpdateProtocol = { proto -> viewModel.updateConfig(config.copy(protocol = proto)) },
                            onRefreshIpInfo = { viewModel.refreshIpInfo() },
                            onRefreshPing = { viewModel.refreshPing() },
                            onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                            bottomContentPadding = BarContentHeight
                        )
                        1 -> SettingsScreen(
                            config = config,
                            onUpdateConfig = { viewModel.updateConfig(it) },
                            onApplyPreset = { viewModel.applyPreset(it) },
                            onOpenRoutingRules = { subScreen = SubScreen.ROUTING },
                            onOpenVpnConfig = {
                                scope.launch(Dispatchers.Main) {
                                    val dialog = java.awt.FileDialog(
                                        null as java.awt.Frame?,
                                        "Select OpenVPN Config (.ovpn)",
                                        java.awt.FileDialog.LOAD
                                    )
                                    dialog.setFilenameFilter { _, name ->
                                        name.endsWith(".ovpn", ignoreCase = true) ||
                                            name.endsWith(".conf", ignoreCase = true)
                                    }
                                    dialog.isVisible = true
                                    val fileName = dialog.file
                                    if (fileName != null) {
                                        val path = java.io.File(dialog.directory, fileName).absolutePath
                                        val needsAuth = runCatching {
                                            java.io.File(path).readLines()
                                                .any { it.trim().equals("auth-user-pass", ignoreCase = true) }
                                        }.getOrDefault(false)
                                        if (needsAuth) {
                                            pendingOpenVpnPath = path
                                        } else {
                                            viewModel.updateConfig(config.copy(openVpnConfigPath = path))
                                            viewModel.showToast("OpenVPN config selected", false)
                                        }
                                    }
                                }
                            },
                            onOpenVpnCredentials = { showOpenVpnCredentials = true },
                            onResetAll = { viewModel.resetAllSettings() },
                            onExportBackup = { viewModel.exportFullBackup() },
                            onImportBackup = { viewModel.importFullBackup() },
                            onOptimizeMtu = { viewModel.optimizeMtu() },
                            isOptimizingMtu = isOptimizingMtu,
                            onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                            bottomContentPadding = BarContentHeight
                        )
                        2 -> LogsScreen(
                            viewModel = viewModel,
                            onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                            bottomContentPadding = BarContentHeight
                        )
                        else -> AboutUsScreen(bottomContentPadding = BarContentHeight)
                    }
                }
            }
        }

        CurvedNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        subScreen?.let { sub ->
            when (sub) {
                SubScreen.ROUTING -> RoutingRulesScreen(
                    rules = config.routingRules,
                    importConflictRules = importConflictRules,
                    importErrorMessage = importErrorMessage,
                    onAddRule = { pattern, mode -> viewModel.addRoutingRule(pattern, mode) },
                    onRemoveRule = { viewModel.removeRoutingRule(it) },
                    onUpdateMode = { pattern, mode -> viewModel.updateRoutingRuleMode(pattern, mode) },
                    onClearAllRules = { viewModel.clearAllRoutingRules() },
                    onCleanPattern = { viewModel.cleanRoutingPattern(it) },
                    onValidatePattern = { viewModel.isValidRoutingPattern(it) },
                    onExportRules = { viewModel.exportRoutingRules() },
                    onImportRules = { viewModel.importRoutingRules() },
                    onImportInternal = { resourceName -> viewModel.importInternalRules(resourceName) },
                    onResolveConflict = { rules, replace -> viewModel.resolveConflict(rules, replace) },
                    onCancelImport = { viewModel.cancelImport() },
                    onClearImportError = { viewModel.clearImportError() },
                    onShowToast = { msg, err -> viewModel.showToast(msg, err) },
                    onBack = { subScreen = null },
                    scaleFactor = scaleFactor
                )
            }
        }

        updateInfo?.let { info ->
            UpdateScreen(
                info = info,
                onDismiss = { viewModel.dismissUpdate() },
                scaleFactor = scaleFactor
            )
        }

        crashLog?.let { log ->
            CrashReportScreen(
                crashLog = log,
                onRestart = { viewModel.clearCrashLog() },
                onShowToast = { msg -> viewModel.showToast(msg, false) }
            )
        }

        if (isWaitingForLoginCode) {
            ZeroTrustLoginDialog(
                onSubmit = { viewModel.submitLoginCode(it) },
                onDismiss = { viewModel.submitLoginCode("") },
                scaleFactor = scaleFactor
            )
        }

        pendingOpenVpnPath?.let { path ->
            OpenVpnAuthDialog(
                title = "OpenVPN Config Requires Login",
                subtitle = "This .ovpn file needs a username and password. They will be saved so you are not asked again.",
                initialUsername = config.openVpnUsername,
                initialPassword = config.openVpnPassword,
                onSave = { user, pass ->
                    viewModel.updateConfig(
                        config.copy(openVpnConfigPath = path, openVpnUsername = user, openVpnPassword = pass)
                    )
                    pendingOpenVpnPath = null
                    viewModel.showToast("OpenVPN config and credentials saved", false)
                },
                onDismiss = { pendingOpenVpnPath = null },
                scaleFactor = scaleFactor
            )
        }

        if (showOpenVpnCredentials) {
            OpenVpnAuthDialog(
                title = "OpenVPN Credentials",
                subtitle = "Used when the .ovpn config requires a login. Saved for future reconnects.",
                initialUsername = config.openVpnUsername,
                initialPassword = config.openVpnPassword,
                onSave = { user, pass ->
                    viewModel.updateConfig(config.copy(openVpnUsername = user, openVpnPassword = pass))
                    showOpenVpnCredentials = false
                    viewModel.showToast("OpenVPN credentials saved", false)
                },
                onDismiss = { showOpenVpnCredentials = false },
                scaleFactor = scaleFactor
            )
        }

        IosToast(
            message = toastState?.message,
            isError = toastState?.isError ?: false,
            scaleFactor = scaleFactor
        )
    }
}

@Composable
fun ZeroTrustLoginDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float
) {
    var code by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
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
private fun OpenVpnAuthDialog(
    title: String,
    subtitle: String,
    initialUsername: String,
    initialPassword: String,
    onSave: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    scaleFactor: Float
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width((330 * scaleFactor).dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1C1C1E))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF9500).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = (18 * scaleFactor).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IosNavInactiveGrey,
                    fontSize = (13 * scaleFactor).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Username") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosNavActiveBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = IosNavInactiveGrey,
                        unfocusedLabelColor = IosNavInactiveGrey,
                        cursorColor = IosNavActiveBlue
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Password") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IosNavActiveBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = IosNavInactiveGrey,
                        unfocusedLabelColor = IosNavInactiveGrey,
                        cursorColor = IosNavActiveBlue
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel", color = IosNavInactiveGrey, fontWeight = FontWeight.Medium)
                    }
                    androidx.compose.material3.Button(
                        onClick = { onSave(username.trim(), password) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = IosNavActiveBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CurvedNavBar(
    selectedTab: Int,
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
                .height(scaledBarHeight)
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
                                modifier = Modifier.graphicsLayer(translationY = 0f)
                            )
                        }
                    }
                }
            }
        }
    }
}
