// Shared Compose Multiplatform UI for Android, Windows (JVM desktop) and Web (Wasm).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization) // type-safe navigation routes
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.guturner.longbox.ui"
        compileSdk = 37
        minSdk = 28
    }
    jvm("desktop")
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:presentation"))
            api(project(":shared:data")) // only AppGraph (the client composition root) touches it

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)

            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.components.resources)
        }
    }
}

// com.android.kotlin.multiplatform.library does not wire commonMain Compose resources into the
// Android AAR asset pipeline — CopyResourcesToAndroidAssetsTask has an unconfigured outputDirectory
// for this plugin combination. This task bridges the gap: it assembles commonMain resources into
// the namespace-qualified path the CMP runtime expects. androidApp picks up the output as assets.
tasks.register<Sync>("assembleAndroidMainComposeResources") {
    from(layout.buildDirectory.dir("generated/compose/resourceGenerator/preparedResources/commonMain/composeResources"))
    into(layout.buildDirectory.dir("generated/compose/resourceGenerator/assembledResources/androidMain/composeResources/longbox.shared.ui.generated.resources"))
    dependsOn("prepareComposeResourcesTaskForCommonMain")
}
