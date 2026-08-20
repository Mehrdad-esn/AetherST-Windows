package io.github.immaghzbad.aetherst.ui

import io.github.immaghzbad.aetherst.core.AetherRegistrationRunner
import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.model.AetherIpMode
import io.github.immaghzbad.aetherst.model.AetherProtocol
import io.github.immaghzbad.aetherst.model.AetherScanMode
import io.github.immaghzbad.aetherst.model.OnboardingState
import io.github.immaghzbad.aetherst.model.OnboardingStep
import io.github.immaghzbad.aetherst.model.ProtocolAttemptResult
import io.github.immaghzbad.aetherst.model.ProtocolTestStatus
import io.github.immaghzbad.aetherst.model.RegistrationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class OnboardingViewModel {
    private val repository = AetherConfigRepository.getInstance()
    private val registrationRunner = AetherRegistrationRunner()
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val currentSessionId = AtomicLong(0)
    private var testJob: Job? = null

    private val _state = MutableStateFlow(
        OnboardingState(
            currentStep = repository.getOnboardingStep().let { step ->
                if (step == OnboardingStep.COMPLETED) OnboardingStep.WELCOME else step
            },
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
        val next = when (_state.value.currentStep) {
            OnboardingStep.WELCOME -> OnboardingStep.PROTOCOL_TEST
            OnboardingStep.PROTOCOL_TEST -> OnboardingStep.SUCCESS
            else -> OnboardingStep.COMPLETED
        }
        updateStep(next)
        if (next == OnboardingStep.COMPLETED) {
            repository.setOnboardingComplete(true)
        }
    }

    private fun updateStep(step: OnboardingStep) {
        repository.setOnboardingStep(step)
        _state.value = _state.value.copy(currentStep = step)
    }

    fun startProtocolTests() {
        if (_state.value.isProcessing) return
        val sessionId = currentSessionId.incrementAndGet()

        _state.value = _state.value.copy(
            isProcessing = true,
            error = null,
            activeProtocol = null,
            protocolResults = _state.value.protocolResults.map { it.copy(status = ProtocolTestStatus.WAITING, error = null) }
        )

        val protocols = listOf(AetherProtocol.MASQUE, AetherProtocol.WG, AetherProtocol.GOOL)
        testJob = viewModelScope.launch {
            var bestProtocol: AetherProtocol? = null

            for (protocol in protocols) {
                if (currentSessionId.get() != sessionId) break
                val result = runSingleProtocolTest(protocol, sessionId)
                if (currentSessionId.get() != sessionId) break

                val status = when (result) {
                    is RegistrationResult.Success -> ProtocolTestStatus.CONNECTED
                    is RegistrationResult.TimedOut -> ProtocolTestStatus.TIMED_OUT
                    is RegistrationResult.Failed -> ProtocolTestStatus.FAILED
                    is RegistrationResult.Cancelled -> ProtocolTestStatus.CANCELLED
                }
                val error = (result as? RegistrationResult.Failed)?.reason
                updateProtocolStatus(protocol, status, error)

                if (result is RegistrationResult.Success) {
                    bestProtocol = bestProtocol ?: protocol
                }
            }

            if (currentSessionId.get() != sessionId) return@launch

            val selected = _state.value.selectedScanMode
            if (bestProtocol != null) {
                repository.updateConfig(
                    repository.config.value.copy(
                        protocol = bestProtocol!!,
                        scanMode = selected,
                        ipMode = AetherIpMode.IPV4
                    )
                )
            } else {
                _state.value = _state.value.copy(error = "No working protocol found for your network.")
            }

            _state.value = _state.value.copy(isProcessing = false)
        }
    }

    fun cancelTests() {
        currentSessionId.incrementAndGet()
        testJob?.cancel()
        testJob = null
        registrationRunner.stop()

        val current = _state.value
        val updated = current.protocolResults.map { result ->
            if (result.status == ProtocolTestStatus.WAITING || result.status == ProtocolTestStatus.PREPARING ||
                result.status == ProtocolTestStatus.REGISTERING || result.status == ProtocolTestStatus.IDENTITY_READY
            ) {
                result.copy(status = ProtocolTestStatus.CANCELLED)
            } else {
                result
            }
        }
        _state.value = current.copy(isProcessing = false, protocolResults = updated)
    }

    private suspend fun runSingleProtocolTest(protocol: AetherProtocol, sessionId: Long): RegistrationResult {
        val timeoutMs = getTimeoutForProtocol(protocol, _state.value.selectedScanMode)
        val config = repository.config.value.copy(
            protocol = protocol,
            scanMode = _state.value.selectedScanMode,
            ipMode = AetherIpMode.IPV4
        )

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<RegistrationResult> { continuation ->
                registrationRunner.runTest(
                    protocol = protocol,
                    config = config,
                    onStatusUpdate = { status ->
                        if (currentSessionId.get() == sessionId) {
                            updateProtocolStatus(protocol, status)
                        }
                    }
                ) { testResult ->
                    if (continuation.isActive) continuation.resume(testResult)
                }
            }
        }

        return result ?: RegistrationResult.TimedOut
    }

    private fun updateProtocolStatus(protocol: AetherProtocol, status: ProtocolTestStatus, error: String? = null) {
        val current = _state.value
        _state.value = current.copy(
            activeProtocol = if (status == ProtocolTestStatus.PREPARING || status == ProtocolTestStatus.REGISTERING ||
                status == ProtocolTestStatus.IDENTITY_READY
            ) {
                protocol
            } else {
                current.activeProtocol
            },
            protocolResults = current.protocolResults.map {
                if (it.protocol == protocol) it.copy(status = status, error = error ?: it.error) else it
            }
        )
    }

    private fun getTimeoutForProtocol(protocol: AetherProtocol, scanMode: AetherScanMode): Long {
        val base = when (protocol) {
            AetherProtocol.MASQUE -> 15000L
            AetherProtocol.WG -> 10000L
            AetherProtocol.GOOL -> 20000L
            AetherProtocol.ZERO_TRUST -> 15000L
            AetherProtocol.OPENVPN -> 15000L
        }
        return base + (if (scanMode != AetherScanMode.TURBO) 10000L else 0L)
    }

    fun release() {
        cancelTests()
        registrationRunner.release()
        viewModelScope.cancel()
    }
}