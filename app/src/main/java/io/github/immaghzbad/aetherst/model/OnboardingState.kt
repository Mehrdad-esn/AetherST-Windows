package io.github.immaghzbad.aetherst.model

enum class OnboardingStep {
    WELCOME,
    PROTOCOL_TEST,
    VPN_PERMISSION,
    NOTIFICATION_PERMISSION,
    BATTERY_OPTIMIZATION,
    SUCCESS,
    COMPLETED
}

enum class ProtocolTestStatus {
    WAITING,
    PREPARING,
    REGISTERING,
    IDENTITY_READY,
    CONNECTED,
    FAILED,
    TIMED_OUT,
    CANCELLED
}

sealed interface RegistrationResult {
    object Success : RegistrationResult
    object TimedOut : RegistrationResult
    data class Failed(val reason: String) : RegistrationResult
    object Cancelled : RegistrationResult
}

data class ProtocolAttemptResult(
    val protocol: AetherProtocol,
    val status: ProtocolTestStatus = ProtocolTestStatus.WAITING,
    val error: String? = null
)

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val protocolResults: List<ProtocolAttemptResult> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val selectedScanMode: AetherScanMode = AetherScanMode.TURBO,
    val activeProtocol: AetherProtocol? = null
)
