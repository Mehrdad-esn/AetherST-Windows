package io.github.immaghzbad.aetherst.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.immaghzbad.aetherst.core.AetherRegistrationRunner
import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class OnboardingViewModel(context: Context) : ViewModel() {

    private val repository = AetherConfigRepository.getInstance(context)
    private val registrationRunner = AetherRegistrationRunner(context)
    private val currentSessionId = AtomicLong(0)
    private var testJob: Job? = null

    private val _state = MutableStateFlow(
        OnboardingState(
            currentStep = repository.getOnboardingStep(),
            protocolResults = listOf(
                ProtocolAttemptResult(AetherProtocol.MASQUE),
                ProtocolAttemptResult(AetherProtocol.WG),
                ProtocolAttemptResult(AetherProtocol.GOOL)
            ),
            selectedScanMode = AetherScanMode.TURBO
        )
    )
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun updateScanMode(mode: AetherScanMode) {
        if (_state.value.isProcessing) return
        _state.value = _state.value.copy(selectedScanMode = mode)
    }

    fun moveToNextStep() {
        val nextStep = when (_state.value.currentStep) {
            OnboardingStep.WELCOME -> OnboardingStep.PROTOCOL_TEST
            OnboardingStep.PROTOCOL_TEST -> OnboardingStep.VPN_PERMISSION
            OnboardingStep.VPN_PERMISSION -> OnboardingStep.NOTIFICATION_PERMISSION
            OnboardingStep.NOTIFICATION_PERMISSION -> OnboardingStep.BATTERY_OPTIMIZATION
            OnboardingStep.BATTERY_OPTIMIZATION -> OnboardingStep.SUCCESS
            OnboardingStep.SUCCESS -> OnboardingStep.COMPLETED
            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED
        }
        updateStep(nextStep)
    }

    fun showNotificationError() {
        _state.value = _state.value.copy(error = "Notification permission is required for app quality and tunnel status updates.")
    }

    private fun updateStep(step: OnboardingStep) {
        _state.value = _state.value.copy(currentStep = step, error = null)
        repository.setOnboardingStep(step)
        if (step == OnboardingStep.COMPLETED) {
            repository.setOnboardingComplete(complete = true)
        }
    }

    fun startProtocolTests() {
        if (_state.value.isProcessing) return
        val sessionId = currentSessionId.incrementAndGet()

        _state.value = _state.value.copy(
            isProcessing = true,
            error = null,
            protocolResults = _state.value.protocolResults.map { it.copy(status = ProtocolTestStatus.WAITING, error = null) }
        )

        testJob = viewModelScope.launch {
            val protocols = listOf(AetherProtocol.MASQUE, AetherProtocol.WG, AetherProtocol.GOOL)
            var bestProtocol: AetherProtocol? = null

            for (proto in protocols) {
                if (sessionId != currentSessionId.get()) break

                _state.value = _state.value.copy(activeProtocol = proto)

                val timeoutMs = getTimeoutForProtocol(proto, _state.value.selectedScanMode)
                val result = withTimeoutOrNull(timeoutMs.milliseconds) {
                    runSingleProtocolTest(proto, sessionId)
                } ?: RegistrationResult.TimedOut

                if (sessionId == currentSessionId.get()) {
                    when (result) {
                        is RegistrationResult.Success -> {
                            if (bestProtocol == null) bestProtocol = proto
                            updateProtocolStatus(proto, ProtocolTestStatus.CONNECTED)
                        }
                        is RegistrationResult.TimedOut -> {
                            updateProtocolStatus(proto, ProtocolTestStatus.TIMED_OUT, "Timeout")
                        }
                        is RegistrationResult.Failed -> {
                            updateProtocolStatus(proto, ProtocolTestStatus.FAILED, result.reason)
                        }
                        RegistrationResult.Cancelled -> {
                            updateProtocolStatus(proto, ProtocolTestStatus.CANCELLED)
                        }
                    }
                }
            }

            if (sessionId == currentSessionId.get()) {
                _state.value = _state.value.copy(isProcessing = false, activeProtocol = null)
                if (bestProtocol != null) {
                    val finalConfig = repository.config.value.copy(
                        protocol = bestProtocol,
                        scanMode = _state.value.selectedScanMode,
                        ipMode = AetherIpMode.IPV4
                    )
                    repository.updateConfig(finalConfig)
                } else {
                    _state.value = _state.value.copy(error = "No working protocol found for your network.")
                }
            }
        }
    }

    fun cancelTests() {
        currentSessionId.incrementAndGet()
        testJob?.cancel()
        registrationRunner.stop()
        
        val currentResults = _state.value.protocolResults.map { result ->
            when (result.status) {
                ProtocolTestStatus.CONNECTED,
                ProtocolTestStatus.FAILED,
                ProtocolTestStatus.TIMED_OUT,
                ProtocolTestStatus.CANCELLED -> result
                else -> result.copy(status = ProtocolTestStatus.CANCELLED)
            }
        }
        
        _state.value = _state.value.copy(
            isProcessing = false, 
            activeProtocol = null,
            protocolResults = currentResults
        )
    }

    private suspend fun runSingleProtocolTest(protocol: AetherProtocol, sessionId: Long): RegistrationResult {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val testConfig = repository.config.value.copy(
                protocol = protocol,
                scanMode = _state.value.selectedScanMode,
                ipMode = AetherIpMode.IPV4
            )
            registrationRunner.runTest(
                protocol = protocol,
                config = testConfig,
                onStatusUpdate = { status ->
                    if (sessionId == currentSessionId.get()) {
                        updateProtocolStatus(protocol, status)
                    }
                }
            ) { result ->
                if (continuation.isActive && sessionId == currentSessionId.get()) {
                    continuation.resume(result)
                }
            }
        }
    }

    private fun updateProtocolStatus(protocol: AetherProtocol, status: ProtocolTestStatus, error: String? = null) {
        val currentResults = _state.value.protocolResults.toMutableList()
        val index = currentResults.indexOfFirst { it.protocol == protocol }
        if (index != -1) {
            currentResults[index] = currentResults[index].copy(status = status, error = error)
            _state.value = _state.value.copy(protocolResults = currentResults)
        }
    }

    private fun getTimeoutForProtocol(protocol: AetherProtocol, scanMode: AetherScanMode): Long {
        val base = when (protocol) {
            AetherProtocol.MASQUE -> 15000L
            AetherProtocol.WG -> 10000L
            AetherProtocol.GOOL -> 20000L
            AetherProtocol.ZERO_TRUST -> 15000L
        }
        return if (scanMode == AetherScanMode.TURBO) base else base + 10000L
    }

    override fun onCleared() {
        registrationRunner.release()
        super.onCleared()
    }
}
