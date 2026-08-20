import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.compose.multiplatform)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(compose.desktop.currentOs)
  implementation(compose.material3)
  implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
  implementation(compose.foundation)
  implementation(compose.animation)
  implementation(compose.ui)
  implementation(libs.okhttp)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.swing)
  implementation("org.json:json:20240303")
  implementation("net.java.dev.jna:jna:5.14.0")

  testImplementation(kotlin("test"))
  testImplementation(compose.desktop.uiTestJUnit4)
  testImplementation(compose.desktop.currentOs)
}

compose.desktop {
  application {
    mainClass = "io.github.immaghzbad.aetherst.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Msi, TargetFormat.Exe)
      packageName = "AetherST"
      packageVersion = "1.4.2"
      description = "AetherST Tunnel - Windows"
      vendor = "PowerSigma Team"
      windows {
        upgradeUuid = "8f5e6a3c-9b42-4d1e-8c7a-2f0b6e9a1c4d"
        iconFile.set(project.file("src/main/resources/icon.ico"))
      }
    }
  }
}