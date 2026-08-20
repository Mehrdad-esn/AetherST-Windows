package io.github.immaghzbad.aetherst.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.immaghzbad.aetherst.model.AetherConfig
import io.github.immaghzbad.aetherst.model.AetherIpMode
import io.github.immaghzbad.aetherst.model.AetherLogLevel
import io.github.immaghzbad.aetherst.model.AetherNoise
import io.github.immaghzbad.aetherst.model.AetherProtocol
import io.github.immaghzbad.aetherst.model.AetherScanMode
import io.github.immaghzbad.aetherst.model.ConnectionMode
import io.github.immaghzbad.aetherst.model.RoutingRule
import io.github.immaghzbad.aetherst.model.TunnelEngine
import io.github.immaghzbad.aetherst.model.OnboardingStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AetherConfigRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val routingRulesAdapter = moshi.adapter<List<RoutingRule>>(
        Types.newParameterizedType(List::class.java, RoutingRule::class.java),
    )
    private val fullConfigAdapter = moshi.adapter(AetherConfig::class.java)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AetherConfig> = _config.asStateFlow()

    private val _isOnboardingComplete = MutableStateFlow(prefs.getBoolean("onboarding_complete", false))
    val isOnboardingComplete: StateFlow<Boolean> = _isOnboardingComplete.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: AetherConfigRepository? = null

        fun getInstance(context: Context): AetherConfigRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AetherConfigRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        LogRepository.currentAppLogLevel = _config.value.appLogLevel
        LogRepository.currentCoreLogLevel = _config.value.coreLogLevel
    }

    private fun loadConfig(): AetherConfig {
        migrateCoreLoggingDefault()
        return readFromPrefs("")
    }

    private fun migrateCoreLoggingDefault() {
        if (prefs.getBoolean("core_logging_default_v2", false)) return
        val current = prefs.getString("core_log_level", null)
        val manual = prefs.getString("manual_core_log_level", null)
        prefs.edit {
            if (current == null || current == AetherLogLevel.OFF.name) {
                putString("core_log_level", AetherLogLevel.INFO.name)
            }
            if (manual == null || manual == AetherLogLevel.OFF.name) {
                putString("manual_core_log_level", AetherLogLevel.INFO.name)
            }
            putBoolean("core_logging_default_v2", true)
        }
    }

    private fun loadManualConfig(): AetherConfig {
        return readFromPrefs("manual_")
    }

    private fun readFromPrefs(prefix: String): AetherConfig {
        val protocolStr = prefs.getString("${prefix}protocol", AetherProtocol.MASQUE.name) ?: AetherProtocol.MASQUE.name
        val noiseStr = prefs.getString("${prefix}noise", AetherNoise.FIREWALL.name) ?: AetherNoise.FIREWALL.name
        val scanModeStr = prefs.getString("${prefix}scan_mode", AetherScanMode.BALANCED.name) ?: AetherScanMode.BALANCED.name
        val ipModeStr = prefs.getString("${prefix}ip_mode", AetherIpMode.IPV4.name) ?: AetherIpMode.IPV4.name
        val appLogLevelStr = prefs.getString("${prefix}app_log_level", AetherLogLevel.INFO.name) ?: AetherLogLevel.INFO.name
        val coreLogLevelStr = prefs.getString("${prefix}core_log_level", AetherLogLevel.INFO.name) ?: AetherLogLevel.INFO.name
        val tunnelEngineStr = prefs.getString("${prefix}tunnel_engine", TunnelEngine.HEV_TUN2SOCKS.name) ?: TunnelEngine.HEV_TUN2SOCKS.name
        val connectionModeStr = prefs.getString("${prefix}connection_mode", null)
        val legacyProxyOnly = prefs.getBoolean("${prefix}proxy_only", false)
        
        val connectionMode = if (connectionModeStr != null) {
            runCatching { ConnectionMode.valueOf(connectionModeStr) }.getOrDefault(ConnectionMode.TUNNEL)
        } else {
            if (legacyProxyOnly) ConnectionMode.PROXY_ONLY else ConnectionMode.TUNNEL
        }

        val presetId = prefs.getString("${prefix}preset_id", "custom") ?: "custom"

        val socksHost = prefs.getString("${prefix}socks_host", "127.0.0.1") ?: "127.0.0.1"
        val cleanHost = if (socksHost == "198.18.0.1") "127.0.0.1" else socksHost
        
        return AetherConfig(
            presetId = presetId,
            protocol = runCatching { AetherProtocol.valueOf(protocolStr) }.getOrDefault(AetherProtocol.MASQUE),
            noise = runCatching { AetherNoise.valueOf(noiseStr) }.getOrDefault(AetherNoise.FIREWALL),
            scanMode = runCatching { AetherScanMode.valueOf(scanModeStr) }.getOrDefault(AetherScanMode.BALANCED),
            ipMode = runCatching { AetherIpMode.valueOf(ipModeStr) }.getOrDefault(AetherIpMode.IPV4),
            h2Mode = prefs.getBoolean("${prefix}h2_mode", true),
            h2Fragment = prefs.getBoolean("${prefix}h2_fragment", false),
            fragmentSize = prefs.getString("${prefix}fragment_size", "16-32") ?: "16-32",
            fragmentDelay = prefs.getString("${prefix}fragment_delay", "2-10") ?: "2-10",
            noDataCheck = prefs.getBoolean("${prefix}no_data_check", false),
            quickReconnect = prefs.getBoolean("${prefix}quick_reconnect", true),
            socksHost = cleanHost,
            socksPort = prefs.getString("${prefix}socks_port", "1819") ?: "1819",
            httpPort = prefs.getString("${prefix}http_port", "1820") ?: "1820",
            appLogLevel = runCatching { AetherLogLevel.valueOf(appLogLevelStr) }.getOrDefault(AetherLogLevel.INFO),
            coreLogLevel = runCatching { AetherLogLevel.valueOf(coreLogLevelStr) }.getOrDefault(AetherLogLevel.INFO),
            peer = prefs.getString("${prefix}peer", "") ?: "",
            keepalive = prefs.getInt("${prefix}keepalive", 5),
            validateSecs = prefs.getInt("${prefix}validate_secs", 10),
            reconnectSecs = prefs.getInt("${prefix}reconnect_secs", 2),
            noProfileRetry = prefs.getBoolean("${prefix}no_profile_retry", false),
            tlsGroups = prefs.getString("${prefix}tls_groups", "") ?: "",
            mtu = prefs.getInt("${prefix}mtu", 1100),
            connectionMode = connectionMode,
            tunnelEngine = runCatching { TunnelEngine.valueOf(tunnelEngineStr) }.getOrDefault(TunnelEngine.HEV_TUN2SOCKS),
            excludedPackages = prefs.getStringSet("${prefix}excluded_packages", emptySet()) ?: emptySet(),
            blockedPackages = prefs.getStringSet("${prefix}blocked_packages", emptySet()) ?: emptySet(),
            routingRules = prefs.getString("${prefix}routing_rules", null)?.let {
                runCatching { routingRulesAdapter.fromJson(it) }.getOrNull()
            } ?: emptyList(),
            teamName = prefs.getString("${prefix}team_name", "") ?: "",
            accessEmail = prefs.getString("${prefix}access_email", "") ?: "",
            accessId = prefs.getString("${prefix}access_id", "") ?: "",
            accessSecret = prefs.getString("${prefix}access_secret", "") ?: "",
            accessToken = prefs.getString("${prefix}access_token", "") ?: "",
            useGateway = prefs.getBoolean("${prefix}use_gateway", false),
            killSwitch = prefs.getBoolean("${prefix}kill_switch", false),
            ipv6Leak = prefs.getBoolean("${prefix}ipv6_leak", true),
            smartReconnect = prefs.getBoolean("${prefix}smart_reconnect", true),
            reconnectRetryLimit = prefs.getInt("${prefix}reconnect_retry_limit", 10),
            strictKillSwitch = prefs.getBoolean("${prefix}strict_kill_switch", false),
            dnsList = prefs.getString("${prefix}dns_list", "1.1.1.1,1.0.0.1") ?: "1.1.1.1,1.0.0.1",
            shareHotspot = prefs.getBoolean("${prefix}share_hotspot", false),
            tunnelAllApps = prefs.getBoolean("${prefix}tunnel_all_apps", true)
        )
    }

    fun updateConfig(newConfig: AetherConfig) {
        val oldConfig = _config.value
        val manualConfig = newConfig.copy(presetId = "custom")
        
        val finalConfig = if (oldConfig.protocol != manualConfig.protocol) {
            saveProtocolSettings(oldConfig)
            loadProtocolSettings(manualConfig.protocol, manualConfig)
        } else {
            saveProtocolSettings(manualConfig)
            manualConfig
        }

        saveToPrefs("", finalConfig)
        saveToPrefs("manual_", finalConfig)
        LogRepository.currentAppLogLevel = finalConfig.appLogLevel
        LogRepository.currentCoreLogLevel = finalConfig.coreLogLevel
        _config.value = finalConfig
    }

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit { putBoolean("onboarding_complete", complete) }
        _isOnboardingComplete.value = complete
    }

    fun getOnboardingStep(): OnboardingStep {
        val name = prefs.getString("onboarding_step_name", OnboardingStep.WELCOME.name) ?: OnboardingStep.WELCOME.name
        return try { OnboardingStep.valueOf(name) } catch (_: Exception) { OnboardingStep.WELCOME }
    }

    fun setOnboardingStep(step: OnboardingStep) {
        prefs.edit { putString("onboarding_step_name", step.name) }
    }

    private fun saveToPrefs(prefix: String, cfg: AetherConfig) {
        prefs.edit {
            putString("${prefix}preset_id", cfg.presetId)
            putString("${prefix}protocol", cfg.protocol.name)
            putString("${prefix}noise", cfg.noise.name)
            putString("${prefix}scan_mode", cfg.scanMode.name)
            putString("${prefix}ip_mode", cfg.ipMode.name)
            putBoolean("${prefix}h2_mode", cfg.h2Mode)
            putBoolean("${prefix}h2_fragment", cfg.h2Fragment)
            putString("${prefix}fragment_size", cfg.fragmentSize)
            putString("${prefix}fragment_delay", cfg.fragmentDelay)
            putBoolean("${prefix}no_data_check", cfg.noDataCheck)
            putBoolean("${prefix}quick_reconnect", cfg.quickReconnect)
            putString("${prefix}socks_host", cfg.socksHost)
            putString("${prefix}socks_port", cfg.socksPort)
            putString("${prefix}http_port", cfg.httpPort)
            putString("${prefix}app_log_level", cfg.appLogLevel.name)
            putString("${prefix}core_log_level", cfg.coreLogLevel.name)
            putString("${prefix}peer", cfg.peer)
            putInt("${prefix}keepalive", cfg.keepalive)
            putInt("${prefix}validate_secs", cfg.validateSecs)
            putInt("${prefix}reconnect_secs", cfg.reconnectSecs)
            putBoolean("${prefix}no_profile_retry", cfg.noProfileRetry)
            putString("${prefix}tls_groups", cfg.tlsGroups)
            putInt("${prefix}mtu", cfg.mtu)
            putString("${prefix}connection_mode", cfg.connectionMode.name)
            putString("${prefix}tunnel_engine", cfg.tunnelEngine.name)
            putStringSet("${prefix}excluded_packages", cfg.excludedPackages)
            putStringSet("${prefix}blocked_packages", cfg.blockedPackages)
            putString("${prefix}routing_rules", routingRulesAdapter.toJson(cfg.routingRules))
            putString("${prefix}team_name", cfg.teamName)
            putString("${prefix}access_email", cfg.accessEmail)
            putString("${prefix}access_id", cfg.accessId)
            putString("${prefix}access_secret", cfg.accessSecret)
            putString("${prefix}access_token", cfg.accessToken)
            putBoolean("${prefix}use_gateway", cfg.useGateway)
            putBoolean("${prefix}kill_switch", cfg.killSwitch)
            putBoolean("${prefix}ipv6_leak", cfg.ipv6Leak)
            putBoolean("${prefix}smart_reconnect", cfg.smartReconnect)
            putInt("${prefix}reconnect_retry_limit", cfg.reconnectRetryLimit)
            putBoolean("${prefix}strict_kill_switch", cfg.strictKillSwitch)
            putString("${prefix}dns_list", cfg.dnsList)
            putBoolean("${prefix}share_hotspot", cfg.shareHotspot)
            putBoolean("${prefix}tunnel_all_apps", cfg.tunnelAllApps)
        }
    }

    fun resetToDefaults() {
        val defaultConfig = AetherConfig()
        updateConfig(defaultConfig)
        LogRepository.i("System reset: All settings restored to factory defaults")
    }

    fun getFullConfigJson(): String {
        return fullConfigAdapter.toJson(_config.value)
    }

    fun restoreFullConfig(json: String): Boolean {
        return try {
            val restored = fullConfigAdapter.fromJson(json) ?: return false
            updateConfig(restored)
            LogRepository.i("Full configuration restored from backup")
            true
        } catch (_: Exception) {
            false
        }
    }

    fun applyPreset(presetId: String) {
        val current = _config.value
        val updated = when (presetId) {
            "custom" -> loadManualConfig()
            "bypass_udp" -> current.copy(
                presetId = "bypass_udp",
                protocol = AetherProtocol.MASQUE,
                noise = AetherNoise.FIREWALL,
                scanMode = AetherScanMode.BALANCED,
                h2Mode = true,
                h2Fragment = true,
                fragmentSize = "16-32",
                fragmentDelay = "2-10",
                noDataCheck = false,
                tlsGroups = "",
                mtu = 1100,
                connectionMode = ConnectionMode.TUNNEL
            )
            "ironclad_stealth" -> current.copy(
                presetId = "ironclad_stealth",
                protocol = AetherProtocol.MASQUE,
                noise = AetherNoise.GFW,
                scanMode = AetherScanMode.IRONCLAD,
                h2Mode = false,
                h2Fragment = false,
                noDataCheck = false,
                tlsGroups = "",
                mtu = 1100,
                connectionMode = ConnectionMode.TUNNEL
            )
            "turbo_wg" -> current.copy(
                presetId = "turbo_wg",
                protocol = AetherProtocol.WG,
                noise = AetherNoise.BALANCED,
                scanMode = AetherScanMode.TURBO,
                noDataCheck = true,
                h2Mode = false,
                h2Fragment = false,
                mtu = 1100,
                connectionMode = ConnectionMode.TUNNEL
            )
            else -> current
        }
        LogRepository.i("Configuration profile applied: $presetId")
        saveToPrefs("", updated)
        saveProtocolSettings(updated)
        LogRepository.currentAppLogLevel = updated.appLogLevel
        LogRepository.currentCoreLogLevel = updated.coreLogLevel
        _config.value = updated
    }

    private fun saveProtocolSettings(cfg: AetherConfig) {
        val p = "protocol_${cfg.protocol.name}_"
        prefs.edit {
            putString("${p}noise", cfg.noise.name)
            putString("${p}scan_mode", cfg.scanMode.name)
            putString("${p}ip_mode", cfg.ipMode.name)
            putBoolean("${p}h2_mode", cfg.h2Mode)
            putBoolean("${p}h2_fragment", cfg.h2Fragment)
            putString("${p}fragment_size", cfg.fragmentSize)
            putString("${p}fragment_delay", cfg.fragmentDelay)
            putBoolean("${p}no_data_check", cfg.noDataCheck)
            putBoolean("${p}quick_reconnect", cfg.quickReconnect)
            putString("${p}peer", cfg.peer)
            putInt("${p}keepalive", cfg.keepalive)
            putInt("${p}validate_secs", cfg.validateSecs)
            putInt("${p}reconnect_secs", cfg.reconnectSecs)
            putBoolean("${p}no_profile_retry", cfg.noProfileRetry)
            putString("${p}tls_groups", cfg.tlsGroups)
            putInt("${p}mtu", cfg.mtu)
            putString("${p}team_name", cfg.teamName)
            putString("${p}access_email", cfg.accessEmail)
            putString("${p}access_id", cfg.accessId)
            putString("${p}access_secret", cfg.accessSecret)
            putString("${p}access_token", cfg.accessToken)
            putBoolean("${p}use_gateway", cfg.useGateway)
            putBoolean("${p}initialized", true)
        }
    }

    private fun loadProtocolSettings(protocol: AetherProtocol, base: AetherConfig): AetherConfig {
        val p = "protocol_${protocol.name}_"
        if (!prefs.contains("${p}initialized")) {
            return when (protocol) {
                AetherProtocol.MASQUE -> base.copy(protocol = protocol, noise = AetherNoise.FIREWALL, scanMode = AetherScanMode.BALANCED)
                AetherProtocol.WG -> base.copy(protocol = protocol, noise = AetherNoise.BALANCED, scanMode = AetherScanMode.TURBO, noDataCheck = true)
                AetherProtocol.GOOL -> base.copy(protocol = protocol, noise = AetherNoise.BALANCED, scanMode = AetherScanMode.BALANCED)
                AetherProtocol.ZERO_TRUST -> base.copy(protocol = protocol, noise = AetherNoise.OFF, scanMode = AetherScanMode.BALANCED)
            }
        }
        return base.copy(
            protocol = protocol,
            noise = runCatching { AetherNoise.valueOf(prefs.getString("${p}noise", "")!!) }.getOrDefault(base.noise),
            scanMode = runCatching { AetherScanMode.valueOf(prefs.getString("${p}scan_mode", "")!!) }.getOrDefault(base.scanMode),
            ipMode = runCatching { AetherIpMode.valueOf(prefs.getString("${p}ip_mode", "")!!) }.getOrDefault(base.ipMode),
            h2Mode = prefs.getBoolean("${p}h2_mode", true),
            h2Fragment = prefs.getBoolean("${p}h2_fragment", false),
            fragmentSize = prefs.getString("${p}fragment_size", "16-32") ?: "16-32",
            fragmentDelay = prefs.getString("${p}fragment_delay", "2-10") ?: "2-10",
            noDataCheck = prefs.getBoolean("${p}no_data_check", false),
            quickReconnect = prefs.getBoolean("${p}quick_reconnect", true),
            peer = prefs.getString("${p}peer", "") ?: "",
            keepalive = prefs.getInt("${p}keepalive", 5),
            validateSecs = prefs.getInt("${p}validate_secs", 10),
            reconnectSecs = prefs.getInt("${p}reconnect_secs", 2),
            noProfileRetry = prefs.getBoolean("${p}no_profile_retry", false),
            tlsGroups = prefs.getString("${p}tls_groups", "") ?: "",
            mtu = prefs.getInt("${p}mtu", 1100),
            teamName = prefs.getString("${p}team_name", "") ?: "",
            accessEmail = prefs.getString("${p}access_email", "") ?: "",
            accessId = prefs.getString("${p}access_id", "") ?: "",
            accessSecret = prefs.getString("${p}access_secret", "") ?: "",
            accessToken = prefs.getString("${p}access_token", "") ?: "",
            useGateway = prefs.getBoolean("${p}use_gateway", false)
        )
    }
}
