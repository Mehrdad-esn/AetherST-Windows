package io.github.immaghzbad.aetherst.data

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrefsTest {
    private val tempFile: File = File.createTempFile("aether-prefs-test", ".properties")
    private val prefs = Prefs(tempFile)

    @AfterTest
    fun cleanup() {
        tempFile.delete()
    }

    @Test
    fun `string roundtrip`() {
        prefs.putString("protocol", "WG")
        val reloaded = Prefs(tempFile)
        assertEquals("WG", reloaded.getString("protocol", null))
    }

    @Test
    fun `string default when missing`() {
        assertNull(prefs.getString("missing_key", null))
        assertEquals("fallback", prefs.getString("missing_key", "fallback"))
    }

    @Test
    fun `boolean roundtrip and fallback`() {
        prefs.putBoolean("h2_mode", false)
        val reloaded = Prefs(tempFile)
        assertFalse(reloaded.getBoolean("h2_mode", true))
        assertTrue(reloaded.getBoolean("missing_key", true))
        assertFalse(reloaded.getBoolean("missing_key", false))
    }

    @Test
    fun `boolean invalid value falls back`() {
        prefs.putString("weird_bool", "not-a-bool")
        assertTrue(prefs.getBoolean("weird_bool", true))
    }

    @Test
    fun `int roundtrip and fallback`() {
        prefs.putInt("keepalive", 42)
        val reloaded = Prefs(tempFile)
        assertEquals(42, reloaded.getInt("keepalive", 0))
        assertEquals(7, reloaded.getInt("missing_key", 7))
    }

    @Test
    fun `int invalid value falls back`() {
        prefs.putString("bad_int", "abc")
        assertEquals(5, prefs.getInt("bad_int", 5))
    }

    @Test
    fun `string set roundtrip`() {
        prefs.putStringSet("blocked_packages", setOf("Discord", "Telegram"))
        val reloaded = Prefs(tempFile)
        assertEquals(setOf("Discord", "Telegram"), reloaded.getStringSet("blocked_packages", emptySet()))
    }

    @Test
    fun `string set empty entries filtered`() {
        prefs.putString("weird_set", "a,,b,")
        assertEquals(setOf("a", "b"), prefs.getStringSet("weird_set", emptySet()))
    }

    @Test
    fun `contains key`() {
        prefs.putString("preset_id", "custom")
        assertTrue(prefs.contains("preset_id"))
        assertFalse(prefs.contains("never_written"))
    }

    @Test
    fun `missing file yields empty prefs`() {
        val fresh = Prefs(File.createTempFile("aether-prefs-empty", ".properties").also { it.delete() })
        assertEquals(null, fresh.getString("anything", null))
        assertEquals(setOf("x"), fresh.getStringSet("anything", setOf("x")))
    }
}