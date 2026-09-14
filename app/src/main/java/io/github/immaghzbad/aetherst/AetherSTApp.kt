package io.github.immaghzbad.aetherst

import android.app.Application
import android.content.Intent
import io.github.immaghzbad.aetherst.core.ConnectionController
import io.github.immaghzbad.aetherst.service.AetherWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class AetherSTApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        observeStatusForWidgets()
    }

    private fun observeStatusForWidgets() {
        applicationScope.launch {
            ConnectionController.status.collect {
                AetherWidgetProvider.updateAllWidgets(this@AetherSTApp)
            }
        }
    }

    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()
                
                val crashLog = "Thread: ${thread.name}\n\nException: ${throwable.localizedMessage}\n\nStack Trace:\n$stackTrace"
                
                val file = File(cacheDir, "last_crash.log")
                file.writeText(crashLog)
                
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                exitProcess(1)
            }
        }
    }
}
