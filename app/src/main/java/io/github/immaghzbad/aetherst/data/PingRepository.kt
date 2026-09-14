package io.github.immaghzbad.aetherst.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

data class PingState(
    val ms: Long = -1,
    val isPinging: Boolean = false,
    val error: String? = null
)

object PingRepository {
    private val _pingState = MutableStateFlow(PingState())
    val pingState: StateFlow<PingState> = _pingState.asStateFlow()

    suspend fun runPing(socksHost: String = "127.0.0.1", socksPort: Int = 1819, useProxy: Boolean = true) {
        _pingState.value = _pingState.value.copy(isPinging = true, error = null)

        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val socket = if (useProxy) {
                    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
                    Socket(proxy)
                } else {
                    Socket()
                }

                socket.use { s ->

                    s.connect(InetSocketAddress("1.1.1.1", 53), 5000)
                }
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                _pingState.value = PingState(ms = duration, isPinging = false)
                LogRepository.i("Ping success: ${duration}ms (Proxy: $useProxy)", "Ping")
            } catch (e: Exception) {
                LogRepository.w("Ping failed (Proxy: $useProxy): ${e.localizedMessage}", "Ping")
                _pingState.value = PingState(ms = -1, isPinging = false, error = "Timeout")
            }
        }
    }
}
