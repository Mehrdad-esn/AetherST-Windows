package io.github.immaghzbad.aetherst.model

enum class AetherProtocol(val rawValue: String, val displayName: String, val description: String) {
    MASQUE("masque", "MASQUE", "HTTP/2/3 Tunneling (MASQUE)"),
    WG("wg", "WireGuard", "Lean speed WireGuard tunnel"),
    GOOL("gool", "Gool (WG-in-WG)", "Double encryption WireGuard-in-WireGuard"),
    ZERO_TRUST("zt", "Zero Trust", "Cloudflare for Organizations")
}

enum class AetherNoise(val rawValue: String, val displayName: String) {
    FIREWALL("firewall", "Firewall (Strict Censorship)"),
    GFW("gfw", "GFW (Heavy DPI Obfuscation)"),
    OFF("off", "Off (No Noise)"),
    BALANCED("balanced", "Balanced Stealth & Speed"),
    AGGRESSIVE("aggressive", "Aggressive Decoy Packets"),
    LIGHT("light", "Light Overhead")
}

enum class AetherScanMode(val rawValue: String, val description: String) {
    TURBO("turbo", "Fastest endpoint match"),
    BALANCED("balanced", "Optimal speed & reliability"),
    THOROUGH("thorough", "Deep ping & latency optimization"),
    STEALTH("stealth", "Quiet slow scanning"),
    IRONCLAD("ironclad", "Full end-to-end HTTP data probe verification")
}

enum class AetherIpMode(val rawValue: String, val displayName: String) {
    IPV4("IPv4", "IPv4 Only"),
    IPV6("IPv6", "IPv6 Only"),
    DUAL("Dual", "Dual Stack (4+6)")
}

enum class AetherLogLevel(val displayName: String) {
    OFF("Off (Disabled - Default, Zero RAM Overhead)"),
    ERROR("Error Only"),
    WARN("Warning & Error"),
    INFO("Info, Warn & Error"),
    DEBUG("Debug (All Verbose Output)")
}

enum class TunnelEngine(val displayName: String) {
    HEV_TUN2SOCKS("HEV Tun2Socks"),
    SOCKS_TUN_BRIDGE("SocksTunBridge")
}

enum class ConnectionMode {
    TUNNEL,
    PROXY_ONLY
}

enum class ConnectionStatus {
    STOPPED,
    STARTING,
    VALIDATING,
    RUNNING,
    RECONNECTING,
    STOPPING,
    ERROR
}

enum class RoutingMode {
    TUNNEL,
    DIRECT,
    BLOCK
}

data class RoutingRule(
    val pattern: String,
    val mode: RoutingMode
)

data class AetherConfig(
    val presetId: String = "custom",
    val protocol: AetherProtocol = AetherProtocol.MASQUE,
    val noise: AetherNoise = AetherNoise.FIREWALL,
    val scanMode: AetherScanMode = AetherScanMode.BALANCED,
    val ipMode: AetherIpMode = AetherIpMode.IPV4,
    val h2Mode: Boolean = true,
    val h2Fragment: Boolean = false,
    val fragmentSize: String = "16-32",
    val fragmentDelay: String = "2-10",
    val noDataCheck: Boolean = false,
    val quickReconnect: Boolean = true,
    val socksHost: String = "127.0.0.1",
    val socksPort: String = "1819",
    val httpPort: String = "1820",
    val appLogLevel: AetherLogLevel = AetherLogLevel.INFO,
    val coreLogLevel: AetherLogLevel = AetherLogLevel.INFO,
    val peer: String = "",
    val keepalive: Int = 5,
    val validateSecs: Int = 10,
    val reconnectSecs: Int = 2,
    val noProfileRetry: Boolean = false,
    val tlsGroups: String = "",
    val mtu: Int = 1100,
    val connectionMode: ConnectionMode = ConnectionMode.TUNNEL,
    val tunnelEngine: TunnelEngine = TunnelEngine.HEV_TUN2SOCKS,
    val excludedPackages: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet(),
    val routingRules: List<RoutingRule> = emptyList(),
    val teamName: String = "",
    val accessEmail: String = "",
    val accessId: String = "",
    val accessSecret: String = "",
    val accessToken: String = "",
    val useGateway: Boolean = false,
    val killSwitch: Boolean = false,
    val ipv6Leak: Boolean = true,
    val smartReconnect: Boolean = true,
    val reconnectRetryLimit: Int = 10,
    val strictKillSwitch: Boolean = false,
    val dnsList: String = "1.1.1.1,1.0.0.1",
    val shareHotspot: Boolean = false,
    val tunnelAllApps: Boolean = true
)
