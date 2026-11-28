import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val desktopVersion = System.getenv("DESKTOP_VERSION") ?: "0.1.0"

// Write Version.kt at configuration time — simpler than a task and avoids config-cache issues.
val versionKtFile = layout.buildDirectory.file("generated/kotlin/com/guturner/longbox/desktop/Version.kt").get().asFile
versionKtFile.parentFile.mkdirs()
versionKtFile.writeText("package com.guturner.longbox.desktop\n\ninternal const val APP_VERSION = \"$desktopVersion\"\n")

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/kotlin"))
            dependencies {
                implementation(project(":shared:ui"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.java.keyring) // Windows Credential Manager
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.zxing.core) // QR code generation for container labels
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.guturner.longbox.desktop.MainKt"
        nativeDistributions {
            // MSI is the standard Windows installer format; jpackage bundles a private JRE.
            targetFormats(TargetFormat.Msi)
            modules("jdk.httpserver") // loopback OAuth redirect listener
            packageName = "Longbox"
            packageVersion = desktopVersion
            windows {
                menu = true
                shortcut = true
                perUserInstall = true // no admin rights needed
                upgradeUuid = "6f1d3c4e-5c1b-4d8e-9a59-1f0b3b8f2a10"
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
            }
        }
    }
}
