import com.android.build.api.dsl.LibraryExtension
import java.io.File
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

val isAndroidDisabled = providers.gradleProperty("skipAndroid").getOrElse("false") == "true"

if (!isAndroidDisabled) {
    apply(plugin = "com.android.library")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    if (!isAndroidDisabled) {
        androidTarget {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
            }
        }
    }

    jvm("desktop")

    sourceSets {
        all {
            languageSettings.optIn("kotlin.ExperimentalMultiplatform")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okhttp)
            implementation(libs.okio)
            implementation(libs.moshi.kotlin)
            implementation(libs.kotlinx.serialization.json)
        }
        if (!isAndroidDisabled) {
            androidMain.dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidsvg)
            }
        }
        getByName("desktopMain").dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

if (!isAndroidDisabled) {
    extensions.configure<LibraryExtension>("android") {
        namespace = "io.github.immaghzbad.aetherst.shared"
        compileSdk = 36
        defaultConfig {
            minSdk = 26
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

val buildCloakWindows by tasks.registering(Exec::class) {
    group = "cloak"
    description = "Compile cloak_windows.c to cloak.exe for Windows package"
    val src = project.file("src/desktopMain/native/cloak_windows.c")
    val outDir = project.file("src/desktopMain/resources/bin")
    val outFile = File(outDir, "cloak.exe")
    val buildDir = project.file("build/cloak")
    outputs.file(outFile)
    inputs.file(src)
    isIgnoreExitValue = true
    notCompatibleWithConfigurationCache("Uses Exec with file copy at execution")
    doFirst {
        outDir.mkdirs()
        buildDir.mkdirs()
        if (!src.exists()) throw GradleException("cloak source missing: $src")
    }
    commandLine("cmd", "/c", "where gcc >nul 2>&1 && gcc -O2 -o \"${outFile.absolutePath}\" \"${src.absolutePath}\" -lws2_32 || where clang >nul 2>&1 && clang -O2 -o \"${outFile.absolutePath}\" \"${src.absolutePath}\" -lws2_32 || echo cloak compiler not found, using embedded Kotlin fallback")
    doLast {
        if (outFile.exists() && outFile.length() > 0) {
            outFile.copyTo(File(buildDir, outFile.name), overwrite = true)
            println("cloak.exe built: ${outFile.length()} bytes")
        } else {
            println("cloak.exe not built, embedded Kotlin relay will be used")
        }
    }
}
tasks.named("desktopProcessResources") { dependsOn(buildCloakWindows) }

compose.desktop {
    application {
        mainClass = "io.github.immaghzbad.aetherst.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            // MSI identity kept across the Windows port so installing 1.7.1
            // performs an in-place major upgrade (same packageName/upgradeUuid).
            // Native packages require MAJOR.MINOR.BUILD: display version stays 1.7.1.
            packageName = "AetherST"
            packageVersion = "1.7.1"
            vendor = "PowerSigma Team"
            description = "AetherST Tunnel - Windows"

            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/desktopMain/resources"))

            windows {
                dirChooser = true
                menu = true
                shortcut = true
                upgradeUuid = "8f5e6a3c-9b42-4d1e-8c7a-2f0b6e9a1c4d"
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }

            buildTypes.release.proguard {
                isEnabled.set(true)
                optimize.set(false)
                obfuscate.set(true)
                configurationFiles.from(project.file("proguard-rules.pro"))
            }
        }
    }
}

// 1.7.1: SmartScreen/user trust automation — hashes every packaged MSI so each
// release ships a verifiable `.sha256` sidecar (also uploaded to the GitHub release).
tasks.register("generateMsiChecksum") {
    group = "distribution"
    description = "Writes SHA256 sidecar files next to packaged MSIs."
    dependsOn("packageMsi")
    // Resolved here (configuration time): only plain values may be captured by doLast
    // or the configuration cache cannot serialize the task.
    val msiDir: File = layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile
    doLast {
        val files = msiDir.listFiles { f -> f.isFile && f.extension.equals("msi", ignoreCase = true) }
            ?: emptyArray()
        if (files.isEmpty()) throw GradleException("No MSI found in $msiDir")
        files.forEach { msi ->
            val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
            msi.inputStream().use { input ->
                val buf = ByteArray(1024 * 1024)
                while (true) {
                    val n: Int = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            val hex: String = digest.digest().joinToString("") { b -> "%02x".format(b) }
            val out = File(msi.parentFile, "${msi.name}.sha256")
            out.writeText("$hex  ${msi.name}\n")
            println("SHA256 written: ${out.absolutePath}")
        }
    }
}


