package io.github.immaghzbad.aetherst.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class CrashReportScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val sampleCrashLog = """
        java.lang.NullPointerException: ConnectionState is null
            at io.github.immaghzbad.aetherst.core.ConnectionController.connect(ConnectionController.kt:120)
            at io.github.immaghzbad.aetherst.ui.AetherViewModel.toggleConnection(AetherViewModel.kt:340)
    """.trimIndent()

    @Test
    fun `crash screen shows title, description and diagnostics`() {
        rule.setContent {
            CrashReportScreen(crashLog = sampleCrashLog, onRestart = {}, onShowToast = {})
        }
        rule.onNodeWithText("System Interruption").assertIsDisplayed()
        rule.onNodeWithText("AetherST recovered from a critical exception").assertIsDisplayed()
        rule.onNodeWithText("DIAGNOSTICS").assertIsDisplayed()
        rule.onNodeWithText("STACK TRACE").assertIsDisplayed()
    }

    @Test
    fun `crash log content is displayed`() {
        rule.setContent {
            CrashReportScreen(crashLog = sampleCrashLog, onRestart = {}, onShowToast = {})
        }
        rule.onNodeWithText("java.lang.NullPointerException", substring = true).assertIsDisplayed()
        rule.onNodeWithText("ConnectionController.kt:120", substring = true).assertIsDisplayed()
    }

    @Test
    fun `clear and restart button invokes onRestart`() {
        val restarted = AtomicBoolean(false)
        rule.setContent {
            CrashReportScreen(crashLog = sampleCrashLog, onRestart = { restarted.set(true) }, onShowToast = {})
        }
        rule.onNodeWithText("Clear & Restart App").performClick()
        org.junit.Assert.assertTrue("onRestart must be invoked", restarted.get())
    }

    @Test
    fun `action buttons visible`() {
        rule.setContent {
            CrashReportScreen(crashLog = sampleCrashLog, onRestart = {}, onShowToast = {})
        }
        rule.onNodeWithText("Save Log").assertIsDisplayed()
        rule.onNodeWithText("Copy Full").assertIsDisplayed()
    }
}