package io.github.immaghzbad.aetherst.data

import io.github.immaghzbad.aetherst.model.AetherConfig
import io.github.immaghzbad.aetherst.model.AetherNoise
import io.github.immaghzbad.aetherst.model.AetherProtocol
import io.github.immaghzbad.aetherst.model.AetherScanMode
import io.github.immaghzbad.aetherst.model.ConnectionMode
import io.github.immaghzbad.aetherst.model.OnboardingStep
import io.github.immaghzbad.aetherst.model.RoutingMode
import io.github.immaghzbad.aetherst.model.RoutingRule
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AetherConfigRepositoryTest {
    private val tempFile: File = File.createTempFile("aether-repo-test", ".properties")
    private val prefs = Prefs(tempFile)
    private val repository = AetherConfigRepository(prefs)

    @AfterTest
    fun cleanup() {
        tempFile.delete()
    }

    private fun freshRepository(): AetherConfigRepository = AetherConfigRepository(Prefs(tempFile))

    @Test
    fun `defaults when prefs empty`() {
        val cfg = repository.config.value
        assertEquals("custom", cfg.presetId)
        assertEquals(AetherProtocol.MASQUE, cfg.protocol)
        assertEquals(AetherNoise.FIREWALL, cfg.noise)
        assertEquals(AetherScanMode.BALANCED, cfg.scanMode)
        assertEquals("127.0.0.1", cfg.socksHost)
        assertEquals("1819", cfg.socksPort)
        assertEquals("1820", cfg.httpPort)
        assertEquals(ConnectionMode.PROXY_ONLY, cfg.connectionMode)
        assertEquals(1100, cfg.mtu)
        assertEquals("1.1.1.1,1.0.0.1", cfg.dnsList)
        assertTrue(cfg.quickReconnect)
        assertTrue(cfg.smartReconnect)
        assertTrue(cfg.routingRules.isEmpty())
        assertTrue(repository.isOnboardingComplete.value)
    }

    @Test
    fun `updateConfig persists and survives reload`() {
        val updated = repository.config.value.copy(
            protocol = AetherProtocol.WG,
            noise = AetherNoise.GFW,
            scanMode = AetherScanMode.TURBO,
            connectionMode = ConnectionMode.TUNNEL,
            socksPort = "9999",
            mtu = 1280
        )
        repository.updateConfig(updated)

        val reloaded = freshRepository().config.value
        assertEquals(AetherProtocol.WG, reloaded.protocol)
        assertEquals(AetherNoise.BALANCED, reloaded.noise)
        assertEquals(AetherScanMode.TURBO, reloaded.scanMode)
        assertEquals(ConnectionMode.TUNNEL, reloaded.connectionMode)
        assertEquals("9999", reloaded.socksPort)
        assertEquals(1280, reloaded.mtu)
    }

    @Test
    fun `invalid enum value falls back to default`() {
        prefs.putString("protocol", "NOT_A_PROTOCOL")
        prefs.putString("noise", "NOPE")
        val repo = freshRepository()
        assertEquals(AetherProtocol.MASQUE, repo.config.value.protocol)
        assertEquals(AetherNoise.FIREWALL, repo.config.value.noise)
    }

    @Test
    fun `openvpn config path roundtrip with reload`() {
        val withOpenVpn = repository.config.value.copy(
            protocol = AetherProtocol.OPENVPN,
            openVpnConfigPath = "C:\\Users\\me\\vpn\\server.ovpn",
            openVpnUsername = "user123",
            openVpnPassword = "pass456"
        )
        repository.updateConfig(withOpenVpn)

        val reloaded = freshRepository().config.value
        assertEquals(AetherProtocol.OPENVPN, reloaded.protocol)
        assertEquals("C:\\Users\\me\\vpn\\server.ovpn", reloaded.openVpnConfigPath)
        assertEquals("user123", reloaded.openVpnUsername)
        assertEquals("pass456", reloaded.openVpnPassword)
    }

    @Test
    fun `openvpn defaults noise off and turbo scan`() {
        val repo = freshRepository()
        repo.updateConfig(repo.config.value.copy(protocol = AetherProtocol.OPENVPN))

        val reloaded = freshRepository().config.value
        assertEquals(AetherProtocol.OPENVPN, reloaded.protocol)
        assertEquals(AetherNoise.OFF, reloaded.noise)
        assertEquals(AetherScanMode.TURBO, reloaded.scanMode)
        assertEquals(ConnectionMode.PROXY_ONLY, reloaded.connectionMode)
    }

    @Test
    fun `openvpn forces proxy only mode even if tunnel was set`() {
        val repo = freshRepository()
        repo.updateConfig(repo.config.value.copy(connectionMode = ConnectionMode.TUNNEL))
        assertEquals(ConnectionMode.TUNNEL, repo.config.value.connectionMode)

        repo.updateConfig(repo.config.value.copy(protocol = AetherProtocol.OPENVPN))
        assertEquals(ConnectionMode.PROXY_ONLY, repo.config.value.connectionMode)

        val reloaded = freshRepository().config.value
        assertEquals(ConnectionMode.PROXY_ONLY, reloaded.connectionMode)
    }

    @Test
    fun `legacy socks host 198_18_0_1 is normalized to loopback`() {
        prefs.putString("socks_host", "198.18.0.1")
        assertEquals("127.0.0.1", freshRepository().config.value.socksHost)
    }

    @Test
    fun `legacy proxy_only flag maps to PROXY_ONLY mode`() {
        prefs.putBoolean("proxy_only", true)
        assertEquals(ConnectionMode.PROXY_ONLY, freshRepository().config.value.connectionMode)
    }

    @Test
    fun `routing rules roundtrip with reload`() {
        val withRules = repository.config.value.copy(
            routingRules = listOf(
                RoutingRule(pattern = "*.ir", mode = RoutingMode.DIRECT),
                RoutingRule(pattern = "10.0.0.0/8", mode = RoutingMode.BLOCK),
                RoutingRule(pattern = "*.example.com", mode = RoutingMode.TUNNEL)
            )
        )
        repository.updateConfig(withRules)

        val reloaded = freshRepository().config.value
        assertEquals(3, reloaded.routingRules.size)
        assertEquals(RoutingRule("*.ir", RoutingMode.DIRECT), reloaded.routingRules[0])
        assertEquals(RoutingRule("10.0.0.0/8", RoutingMode.BLOCK), reloaded.routingRules[1])
        assertEquals(RoutingRule("*.example.com", RoutingMode.TUNNEL), reloaded.routingRules[2])
    }

    @Test
    fun `corrupt routing rules json yields empty list`() {
        prefs.putString("routing_rules", "{not-valid-json")
        assertTrue(freshRepository().config.value.routingRules.isEmpty())
    }

    @Test
    fun `applyPreset turbo_wg applies WG turbo tunnel`() {
        repository.applyPreset("turbo_wg")
        val cfg = repository.config.value
        assertEquals("turbo_wg", cfg.presetId)
        assertEquals(AetherProtocol.WG, cfg.protocol)
        assertEquals(AetherScanMode.TURBO, cfg.scanMode)
        assertTrue(cfg.noDataCheck)
        assertEquals(ConnectionMode.TUNNEL, cfg.connectionMode)
    }

    @Test
    fun `applyPreset bypass_udp applies masque fragment tunnel`() {
        repository.applyPreset("bypass_udp")
        val cfg = repository.config.value
        assertEquals(AetherProtocol.MASQUE, cfg.protocol)
        assertTrue(cfg.h2Fragment)
        assertEquals(ConnectionMode.TUNNEL, cfg.connectionMode)
    }

    @Test
    fun `applyPreset ironclad_stealth applies GFW ironclad`() {
        repository.applyPreset("ironclad_stealth")
        val cfg = repository.config.value
        assertEquals(AetherNoise.GFW, cfg.noise)
        assertEquals(AetherScanMode.IRONCLAD, cfg.scanMode)
        assertFalse(cfg.h2Mode)
        assertEquals(ConnectionMode.TUNNEL, cfg.connectionMode)
    }

    @Test
    fun `applyPreset unknown keeps current`() {
        val before = repository.config.value
        repository.applyPreset("does_not_exist")
        assertEquals(before, repository.config.value)
    }

    @Test
    fun `resetToDefaults restores factory defaults`() {
        repository.updateConfig(repository.config.value.copy(protocol = AetherProtocol.GOOL, mtu = 9000))
        repository.resetToDefaults()
        assertEquals(AetherConfig(), repository.config.value)
    }

    @Test
    fun `onboarding complete flag and step roundtrip`() {
        assertTrue(repository.isOnboardingComplete.value)
        repository.setOnboardingComplete(false)
        assertFalse(repository.isOnboardingComplete.value)
        assertEquals(OnboardingStep.WELCOME, repository.getOnboardingStep())
        repository.setOnboardingStep(OnboardingStep.COMPLETED)
        assertEquals(OnboardingStep.COMPLETED, repository.getOnboardingStep())
    }

    @Test
    fun `invalid onboarding step falls back to welcome`() {
        prefs.putString("onboarding_step_name", "BOGUS_STEP")
        assertEquals(OnboardingStep.WELCOME, freshRepository().getOnboardingStep())
    }

    @Test
    fun `full config json roundtrip restore`() {
        repository.updateConfig(repository.config.value.copy(protocol = AetherProtocol.GOOL, mtu = 9000))
        val json = repository.getFullConfigJson()
        val restored = freshRepository()
        assertTrue(restored.restoreFullConfig(json))
        assertEquals(AetherProtocol.GOOL, restored.config.value.protocol)
        assertEquals(9000, restored.config.value.mtu)
    }

    @Test
    fun `restoreFullConfig rejects invalid json`() {
        assertFalse(repository.restoreFullConfig("{\"garbage"))
    }

    @Test
    fun `protocol specific settings saved on switch and reloaded`() {
        val wgConfig = repository.config.value.copy(protocol = AetherProtocol.WG, keepalive = 25, mtu = 1400)
        repository.updateConfig(wgConfig)
        val backToMasque = repository.config.value.copy(protocol = AetherProtocol.MASQUE)
        repository.updateConfig(backToMasque)

        val reloaded = freshRepository().config.value
        assertEquals(AetherProtocol.MASQUE, reloaded.protocol)

        val wgAgain = reloaded.copy(protocol = AetherProtocol.WG)
        repository.updateConfig(wgAgain)
        val wgReloaded = freshRepository().config.value
        assertNotNull(wgReloaded)
        assertEquals(25, wgReloaded.keepalive)
        assertEquals(1400, wgReloaded.mtu)
    }
}