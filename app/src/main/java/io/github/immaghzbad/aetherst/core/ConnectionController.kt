package io.github.immaghzbad.aetherst.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.TrafficStats
import android.os.Process
import io.github.immaghzbad.aetherst.data.AetherConfigRepository
import io.github.immaghzbad.aetherst.data.LogRepository
import io.github.immaghzbad.aetherst.model.AetherScanMode
import io.github.immaghzbad.aetherst.model.ConnectionStatus
import io.github.immaghzbad.aetherst.model.SessionTraffic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConnectionController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runner = AetherProcessRunner(appContext)
    private val mutex = Mutex()
    private val activeAttemptId = AtomicLong(0)
    private val loginCodeChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    private val _isWaitingForCode = MutableStateFlow(false)
    val isWaitingForCode: StateFlow<Boolean> = _isWaitingForCode.asStateFlow()

    private var timerJob: Job? = null
    private var durationSeconds = 0L
    private var baseTx = 0L
    private var baseRx = 0L
    private var isManualTraffic = false
    private var lastManualTx = 0L
    private var lastManualRx = 0L
    private var httpProxy: LocalHttpProxyServer? = null

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: ConnectionController? = null

        private val _status = MutableStateFlow(ConnectionStatus.STOPPED)
        val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        private val _sessionTraffic = MutableStateFlow(SessionTraffic())
        val sessionTraffic: StateFlow<SessionTraffic> = _sessionTraffic.asStateFlow()

        fun getInstance(context: Context): ConnectionController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionController(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun updateStatus(status: ConnectionStatus) {
            _status.value = status
        }
    }

    init {
        scope.launch {
            runner.connectionStatus.collect { coreStatus ->
                handleCoreStatus(coreStatus)
            }
        }
    }

    fun submitLoginCode(code: String) {
        _isWaitingForCode.value = false
        loginCodeChannel.trySend(code)
    }

    suspend fun start() = mutex.withLock {
        if (_status.value == ConnectionStatus.RUNNING || _status.value == ConnectionStatus.VALIDATING) {
            return@withLock
        }

        val attemptId = System.currentTimeMillis()
        activeAttemptId.set(attemptId)
        _status.value = ConnectionStatus.STARTING

        try {
            val config = AetherConfigRepository.getInstance(appContext).config.value
            val bindHost = if (config.shareHotspot) "0.0.0.0" else "127.0.0.1"
            val bindAddress = "$bindHost:${config.socksPort}"

            isManualTraffic = false
            baseTx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)
            baseRx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)

            LogRepository.i("[Controller] Starting core at $bindAddress")
            runner.start(config, bindAddress, onCodeRequired = {
                _isWaitingForCode.value = true
            }, inputProvider = {
                loginCodeChannel.receive()
            })

            val proxyPort = config.socksPort.toIntOrNull() ?: 1819
            val startupTimeoutSeconds = when (config.scanMode) {
                AetherScanMode.TURBO -> 90L
                AetherScanMode.BALANCED -> 120L
                AetherScanMode.THOROUGH -> 180L
                AetherScanMode.STEALTH -> 240L
                AetherScanMode.IRONCLAD -> 240L
            } + config.validateSecs.coerceAtLeast(0)

            val ready = withTimeoutOrNull(startupTimeoutSeconds.seconds) {
                while (currentCoroutineContext().isActive) {
                    if (runner.connectionStatus.value == ConnectionStatus.RUNNING) return@withTimeoutOrNull true
                    if (isPortListening("127.0.0.1", proxyPort)) return@withTimeoutOrNull true
                    delay(250.milliseconds)
                }
                false
            } ?: false

            if (!ready) {
                throw IllegalStateException("Core startup timed out after ${startupTimeoutSeconds}s")
            }

            if (!verifyPortListening("127.0.0.1", proxyPort)) {
                throw IllegalStateException("Proxy port is not listening")
            }

            _status.value = ConnectionStatus.RUNNING
            
            val httpListenHost = if (config.shareHotspot) "0.0.0.0" else "127.0.0.1"
            httpProxy = LocalHttpProxyServer(
                vpnService = null,
                listenHost = httpListenHost,
                listenPort = config.httpPort.toIntOrNull() ?: 1820,
                socksHost = "127.0.0.1",
                socksPort = config.socksPort.toIntOrNull() ?: 1819
            ).apply { start() }
            
            startTimer()
            LogRepository.i("[Controller] Core is active and validated")
        } catch (e: Exception) {
            LogRepository.e("[Controller] Startup failed: ${e.localizedMessage}")
            cleanup(attemptId)
            _status.value = ConnectionStatus.ERROR
        }
    }

    suspend fun stop() = mutex.withLock {
        if (_status.value == ConnectionStatus.STOPPED) {
            return@withLock
        }

        val attemptId = activeAttemptId.get()
        _status.value = ConnectionStatus.STOPPING
        LogRepository.i("[Controller] Stopping core")

        stopTimer()
        cleanup(attemptId)

        _status.value = ConnectionStatus.STOPPED
        LogRepository.i("[Controller] Core stopped")
    }

    private suspend fun cleanup(attemptId: Long) {
        if (activeAttemptId.get() == attemptId) {
            activeAttemptId.set(0)
        }
        runner.stop()
        httpProxy?.stop()
        httpProxy = null
        _isWaitingForCode.value = false
        delay(500.milliseconds)
    }

    private fun handleCoreStatus(coreStatus: ConnectionStatus) {
        val current = _status.value
        if (current == ConnectionStatus.STOPPED || current == ConnectionStatus.STOPPING) return

        when (coreStatus) {
            ConnectionStatus.ERROR -> {
                LogRepository.e("[Controller] Core reported error")
                _status.value = ConnectionStatus.ERROR
                stopTimer()
            }
            ConnectionStatus.STOPPED -> {
                if (current == ConnectionStatus.RUNNING || current == ConnectionStatus.RECONNECTING) {
                    LogRepository.w("[Controller] Core stopped unexpectedly")
                    _status.value = ConnectionStatus.ERROR
                    stopTimer()
                }
            }
            ConnectionStatus.RECONNECTING -> {
                if (current == ConnectionStatus.RUNNING) {
                    _status.value = ConnectionStatus.RECONNECTING
                }
            }
            ConnectionStatus.RUNNING -> {
                if (current == ConnectionStatus.RECONNECTING || current == ConnectionStatus.STARTING || current == ConnectionStatus.VALIDATING) {
                    _status.value = ConnectionStatus.RUNNING
                    startTimer()
                }
            }
            else -> {}
        }
    }

    private suspend fun isPortListening(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 300)
                    true
                }
            }.getOrDefault(false)
        }
    }

    private suspend fun verifyPortListening(host: String, port: Int): Boolean {
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (isPortListening(host, port)) return true
            delay(200.milliseconds)
        }
        return false
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        durationSeconds = 0
        _elapsedSeconds.value = 0
        timerJob = scope.launch {
            while (isActive) {
                delay(1000.milliseconds)
                durationSeconds++
                _elapsedSeconds.value = durationSeconds
                if (!isManualTraffic) {
                    updateTrafficFromStats()
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        durationSeconds = 0
        _elapsedSeconds.value = 0
        _sessionTraffic.value = SessionTraffic()
        isManualTraffic = false
        lastManualTx = 0
        lastManualRx = 0
    }

    private fun updateTrafficFromStats() {
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)
        val currentRx = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)
        
        val diffTx = (currentTx - baseTx).coerceAtLeast(0)
        val diffRx = (currentRx - baseRx).coerceAtLeast(0)
        
        _sessionTraffic.value = SessionTraffic(diffTx, diffRx)
    }

    fun setTraffic(tx: Long, rx: Long) {
        if (tx > lastManualTx || rx > lastManualRx || (tx == 0L && rx == 0L && !isManualTraffic)) {
            isManualTraffic = true
            lastManualTx = tx.coerceAtLeast(lastManualTx)
            lastManualRx = rx.coerceAtLeast(lastManualRx)
            _sessionTraffic.value = SessionTraffic(lastManualTx, lastManualRx)
        }
    }
}
