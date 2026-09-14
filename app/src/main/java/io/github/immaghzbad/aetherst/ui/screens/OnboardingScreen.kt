package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immaghzbad.aetherst.model.*
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onGetStarted: () -> Unit,
    onRetryRegistration: () -> Unit,
    onCancelRegistration: () -> Unit,
    onUpdateScanMode: (AetherScanMode) -> Unit,
    onRequestVpnPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onFinish: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            OnboardingHeader()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.currentStep,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                    },
                    label = "step_transition"
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep(onGetStarted)
                        OnboardingStep.PROTOCOL_TEST -> ProtocolTestStep(
                            state,
                            onRetryRegistration,
                            onCancelRegistration,
                            onUpdateScanMode,
                            onFinish
                        )
                        OnboardingStep.VPN_PERMISSION -> VpnPermissionStep(onRequestVpnPermission)
                        OnboardingStep.NOTIFICATION_PERMISSION -> NotificationPermissionStep(state, onRequestNotificationPermission)
                        OnboardingStep.BATTERY_OPTIMIZATION -> BatteryOptimizationStep(onRequestBatteryOptimization, onFinish)
                        OnboardingStep.SUCCESS -> SuccessStep(onFinish)
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }

            OnboardingFooter(state.currentStep)
        }
    }
}

@Composable
private fun OnboardingHeader() {
    val slogans = listOf(
        "Privacy at Warp Speed",
        "Beyond Boundaries, Beyond Limits",
        "Invisible, Untraceable, Unstoppable",
        "The Future of Secure Networking",
        "Your Digital Shield in the Shadows",
        "Encryption Without Compromise",
        "Defying Censorship, Ensuring Freedom",
        "Secure, Free, and Ad-free",
        "Secure Your Connection Instantly",
        "Total Freedom for Every User",
        "High-Performance Proxy Engine",
        "Advanced Protection Against Tracking",
        "Seamless Access to Global Content",
        "Reliable Security for Your Data",
        "Experience a Truly Open Internet",
        "Optimized for Low-Latency Browsing",
        "Your Trusted Companion for Privacy",
        "Fast, Secure, and Reliable"
    )
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000.milliseconds)
            index = (index + 1) % slogans.size
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "AetherST",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = slogans[index],
                transitionSpec = {
                    (slideInVertically { it } + fadeIn(tween(600))) togetherWith
                            (slideOutVertically { -it } + fadeOut(tween(600)))
                },
                label = "slogan_animation"
            ) { slogan ->
                Text(
                    text = slogan,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8E8E93),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to AetherST",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Let’s prepare your secure connection in a few quick steps.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF), contentColor = Color.White)
        ) {
            Text("Get Started", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun ProtocolTestStep(
    state: OnboardingState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onUpdateScanMode: (AetherScanMode) -> Unit,
    onContinue: () -> Unit
) {
    val allowedModes = listOf(AetherScanMode.TURBO, AetherScanMode.BALANCED, AetherScanMode.STEALTH, AetherScanMode.IRONCLAD)
    val allDone = !state.isProcessing && state.protocolResults.all {
        it.status == ProtocolTestStatus.CONNECTED ||
        it.status == ProtocolTestStatus.FAILED ||
        it.status == ProtocolTestStatus.TIMED_OUT ||
        it.status == ProtocolTestStatus.CANCELLED
    }
    val anySuccess = state.protocolResults.any { it.status == ProtocolTestStatus.CONNECTED }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Preparing Your Connection",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        SelectorLabel()
        AetherScanModeSelector(
            selected = state.selectedScanMode,
            allowedModes = allowedModes,
            enabled = !state.isProcessing,
            onSelect = onUpdateScanMode
        )

        Spacer(modifier = Modifier.height(32.dp))

        state.protocolResults.forEach { result ->
            ProtocolRow(result.protocol.displayName, result.status, state.activeProtocol == result.protocol)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = state.error, color = Color(0xFFFF3B30), fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (state.isProcessing) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel Test", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else if (allDone && anySuccess) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759), contentColor = Color.White)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF), contentColor = Color.White)
            ) {
                Text(
                    text = if (state.error != null) "Try Again" else "Start Connection Test",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SelectorLabel() {
    Text(
        text = "SCAN MODE",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF8E8E93),
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun AetherScanModeSelector(
    selected: AetherScanMode,
    allowedModes: List<AetherScanMode>,
    enabled: Boolean,
    onSelect: (AetherScanMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1E)).padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        allowedModes.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF007AFF) else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val label = when(mode) {
                    AetherScanMode.TURBO -> "Turbo"
                    AetherScanMode.BALANCED -> "Balanced"
                    AetherScanMode.STEALTH -> "Stealth"
                    AetherScanMode.IRONCLAD -> "Ironclad"
                    else -> mode.name
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else Color(0xFF8E8E93),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ProtocolRow(name: String, status: ProtocolTestStatus, isActive: Boolean) {
    Surface(
        color = Color(0xFF1C1C1E),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = name, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (isActive) {
                    Text(
                        text = when (status) {
                            ProtocolTestStatus.PREPARING -> "Preparing engine..."
                            ProtocolTestStatus.REGISTERING -> "Registering account..."
                            ProtocolTestStatus.IDENTITY_READY -> "Identity verified"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF007AFF)
                    )
                }
            }

            when (status) {
                ProtocolTestStatus.WAITING -> Text("Waiting", color = Color(0xFF8E8E93), style = MaterialTheme.typography.labelSmall)
                ProtocolTestStatus.CONNECTED -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759))
                ProtocolTestStatus.FAILED, ProtocolTestStatus.TIMED_OUT -> Icon(Icons.Default.Error, null, tint = Color(0xFFFF3B30))
                ProtocolTestStatus.CANCELLED -> Text("Cancelled", color = Color(0xFF8E8E93), style = MaterialTheme.typography.labelSmall)
                else -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF007AFF))
            }
        }
    }
}

@Composable
private fun VpnPermissionStep(onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Allow VPN Access", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AetherST needs VPN permission to create a secure tunnel. Your current connection remains untouched for now.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF), contentColor = Color.White)
        ) {
            Text("Allow Access", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun NotificationPermissionStep(state: OnboardingState, onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Stay Informed", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Enable notifications to see tunnel status and important updates.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF), contentColor = Color.White)
        ) {
            Text("Enable Notifications", fontWeight = FontWeight.Bold, color = Color.White)
        }
        if (state.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.error,
                color = Color(0xFFFF9500),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BatteryOptimizationStep(onRequest: () -> Unit, onSkip: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Unrestricted Background Service", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To ensure a stable and persistent tunnel connection, please disable battery optimizations for AetherST.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF), contentColor = Color.White)
        ) {
            Text("Disable Restrictions", fontWeight = FontWeight.Bold, color = Color.White)
        }
        TextButton(onClick = onSkip) {
            Text("Not Now", color = Color.White)
        }
    }
}

@Composable
private fun SuccessStep(onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Setup Complete", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "AetherST is ready to protect your connection. You can now enter the dashboard and start the tunnel.",
            color = Color(0xFF8E8E93),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759), contentColor = Color.White)
        ) {
            Text("Start Secure Journey", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun OnboardingFooter(currentStep: OnboardingStep) {
    Row(
        modifier = Modifier.padding(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OnboardingStep.entries.filter { it != OnboardingStep.COMPLETED }.forEach { step ->
            val isSelected = step == currentStep
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "indicator_width"
            )
            val color by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF007AFF) else Color(0xFF2C2C2E),
                animationSpec = tween(400),
                label = "indicator_color"
            )

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
