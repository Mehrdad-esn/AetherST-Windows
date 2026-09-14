package io.github.immaghzbad.aetherst.data

import io.github.immaghzbad.aetherst.desktop.AppPaths
import java.io.File
import java.util.Properties

class Prefs(private val file: File) {
    private val props = Properties()

    init {
        if (file.exists()) {
            runCatching { file.inputStream().use { props.load(it) } }
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "AetherST config") }
        }
    }

    fun getString(key: String, def: String?): String? = props.getProperty(key) ?: def

    fun getBoolean(key: String, def: Boolean): Boolean {
        val value = props.getProperty(key) ?: return def
        return value.toBooleanStrictOrNull() ?: def
    }

    fun getInt(key: String, def: Int): Int {
        val value = props.getProperty(key) ?: return def
        return value.toIntOrNull() ?: def
    }

    fun getStringSet(key: String, def: Set<String>): Set<String> {
        val value = props.getProperty(key) ?: return def
        return value.split(',').filter { it.isNotEmpty() }.toSet()
    }

    fun putString(key: String, value: String) {
        props.setProperty(key, value)
        save()
    }

    fun putBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString())
        save()
    }

    fun putInt(key: String, value: Int) {
        props.setProperty(key, value.toString())
        save()
    }

    fun putStringSet(key: String, value: Set<String>) {
        props.setProperty(key, value.joinToString(","))
        save()
    }

    fun contains(key: String): Boolean = props.containsKey(key)
}

object DesktopPrefs {
    val instance: Prefs by lazy { Prefs(AppPaths.configFile()) }
}