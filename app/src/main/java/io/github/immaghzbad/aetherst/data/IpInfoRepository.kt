package io.github.immaghzbad.aetherst.data

import io.github.immaghzbad.aetherst.core.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.time.Duration.Companion.milliseconds

data class IpInfo(
    val ip: String = "",
    val country: String = "",
    val countryCode: String = "",
    val flagEmoji: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

object IpInfoRepository {
    private val _ipInfo = MutableStateFlow(IpInfo())
    val ipInfo: StateFlow<IpInfo> = _ipInfo.asStateFlow()

    suspend fun fetchIpInfo(socksHost: String = "127.0.0.1", socksPort: Int = 1819, useProxy: Boolean = true) {
        _ipInfo.value = _ipInfo.value.copy(isLoading = true)

        withContext(Dispatchers.IO) {
            if (!useProxy) {
                LogRepository.i("Querying public IP endpoint...", "IpWhois")
                if (tryFetchDirectIpWhois() || tryFetchDirectIpApi()) {
                    return@withContext
                }
            } else {
                delay(1200.milliseconds)

                for (attempt in 1..3) {
                    LogRepository.i("Querying public IP via tunnel ($socksHost:$socksPort)...", "IpWhois")

                    val success = tryFetchFromIpWhois(socksHost, socksPort) || tryFetchFromIpApi(socksHost, socksPort)
                    if (success) return@withContext

                    if (attempt < 3) {
                        delay(1000.milliseconds)
                    }
                }
            }

            LogRepository.w("${if (useProxy) "SOCKS proxy" else "Direct"} IP lookup failed.", "IpWhois")
            _ipInfo.value = _ipInfo.value.copy(
                isLoading = false,
                error = if (useProxy) "Proxy Lookup Failed" else "Direct Lookup Failed"
            )
        }
    }

    private fun tryFetchFromIpWhois(socksHost: String, socksPort: Int): Boolean {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            val client = NetworkClient.instance.newBuilder()
                .proxy(proxy)
                .connectTimeout(6000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(6000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url("https://ipwho.is/")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return false
                    val json = JSONObject(jsonStr)
                    if (json.optBoolean("success", true)) {
                        val ip = json.optString("ip", "")
                        val country = json.optString("country", "Unknown")
                        val countryCode = json.optString("country_code", "")
                        val flagObj = json.optJSONObject("flag")
                        val flagEmoji = flagObj?.optString("emoji", "")?.ifEmpty { getFlagEmoji(countryCode) }
                            ?: getFlagEmoji(countryCode)

                        if (ip.isNotEmpty()) {
                            _ipInfo.value = IpInfo(
                                ip = ip,
                                country = country,
                                countryCode = countryCode,
                                flagEmoji = flagEmoji,
                                isLoading = false
                            )
                            LogRepository.i("Geo-data synchronized: $ip ($country $flagEmoji)", "IpWhois")
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            LogRepository.w("ipwho.is via SOCKS error: ${e.localizedMessage}", "IpWhois")
            false
        }
    }

    private fun tryFetchFromIpApi(socksHost: String, socksPort: Int): Boolean {
        return try {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(socksHost, socksPort))
            val client = NetworkClient.instance.newBuilder()
                .proxy(proxy)
                .connectTimeout(6000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(6000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return false
                    val json = JSONObject(jsonStr)
                    val ip = json.optString("ip", "")
                    val country = json.optString("country_name", "Unknown")
                    val countryCode = json.optString("country_code", "")
                    val flagEmoji = getFlagEmoji(countryCode)

                    if (ip.isNotEmpty()) {
                        _ipInfo.value = IpInfo(
                            ip = ip,
                            country = country,
                            countryCode = countryCode,
                            flagEmoji = flagEmoji,
                            isLoading = false
                        )
                        LogRepository.i("Geo-data synchronized: $ip ($country $flagEmoji)", "IpWhois")
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            LogRepository.w("ipapi.co via SOCKS error: ${e.localizedMessage}", "IpWhois")
            false
        }
    }

    private fun tryFetchDirectIpWhois(): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://ipwho.is/")
                .build()

            NetworkClient.instance.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return false
                    val json = JSONObject(jsonStr)
                    if (json.optBoolean("success", true)) {
                        val ip = json.optString("ip", "")
                        val country = json.optString("country", "Unknown")
                        val countryCode = json.optString("country_code", "")
                        val flagObj = json.optJSONObject("flag")
                        val flagEmoji = flagObj?.optString("emoji", "")?.ifEmpty { getFlagEmoji(countryCode) }
                            ?: getFlagEmoji(countryCode)

                        if (ip.isNotEmpty()) {
                            _ipInfo.value = IpInfo(
                                ip = ip,
                                country = country,
                                countryCode = countryCode,
                                flagEmoji = flagEmoji,
                                isLoading = false
                            )
                            return true
                        }
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun tryFetchDirectIpApi(): Boolean {
        return try {
            val request = Request.Builder()
                .url("https://ipapi.co/json/")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            NetworkClient.instance.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return false
                    val json = JSONObject(jsonStr)
                    val ip = json.optString("ip", "")
                    val country = json.optString("country_name", "Unknown")
                    val countryCode = json.optString("country_code", "")
                    val flagEmoji = getFlagEmoji(countryCode)

                    if (ip.isNotEmpty()) {
                        _ipInfo.value = IpInfo(
                            ip = ip,
                            country = country,
                            countryCode = countryCode,
                            flagEmoji = flagEmoji,
                            isLoading = false
                        )
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun reset() {
        _ipInfo.value = IpInfo()
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return "🌐"
        val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}
