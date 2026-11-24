// Innermost ring: entities, use cases and repository ports.
// No framework dependencies: only the Kotlin stdlib and coroutines (for suspend/Flow).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.guturner.longbox.domain"
        compileSdk = 37
        minSdk = 28
    }

    jvm()          // consumed by :server and :desktopApp
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core) // Flow is part of the public API
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
