package io.github.immaghzbad.aetherst.model

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

sealed class RegistrationResult {
    data object Success : RegistrationResult()
    data object TimedOut : RegistrationResult()
    data class Failed(val reason: String) : RegistrationResult()
    data object Cancelled : RegistrationResult()
}