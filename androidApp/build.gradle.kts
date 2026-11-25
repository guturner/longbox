import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.guturner.longbox.android"
    compileSdk = 37

    // CI sets ANDROID_KEYSTORE_PATH; local debug builds use the default debug signing.
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.guturner.longbox"
        minSdk = 28
        targetSdk = 37
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "0.1.0"

        // Not secrets: OAuth client ids and the server URL are public identifiers (RFC 8252 §8.5).
        buildConfigField("String", "LONGBOX_SERVER_URL", "\"${providers.gradleProperty("longbox.serverUrl").getOrElse("https://longbox.local")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("longbox.googleWebClientId").getOrElse("")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (System.getenv("ANDROID_KEYSTORE_PATH") != null && System.getenv("ANDROID_KEYSTORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // shared:ui's commonMain Compose resources assembled with the namespace prefix the CMP runtime
    // expects. Required because com.android.kotlin.multiplatform.library doesn't wire them into
    // the asset pipeline (see shared/ui/build.gradle.kts: assembleAndroidMainComposeResources).
    sourceSets["main"].assets.srcDirs(
        "../shared/ui/build/generated/compose/resourceGenerator/assembledResources/androidMain"
    )
}

// Ensure the resource assembly runs before assets are merged.
tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }.configureEach {
    dependsOn(":shared:ui:assembleAndroidMainComposeResources")
}

// Verify that the debug APK contains the CMP resources assembled above.
// Guards against regressions where the packaging pipeline stops including them.
// Run explicitly: ./gradlew :androidApp:verifyApkComposeResources
tasks.register("verifyApkComposeResources") {
    dependsOn("assembleDebug")
    val apk = layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
    inputs.file(apk)
    doLast {
        val expectedEntries = listOf(
            "assets/composeResources/longbox.shared.ui.generated.resources/drawable/longbox_logo.png"
        )
        ZipFile(apk.get().asFile).use { zip ->
            for (entry in expectedEntries) {
                requireNotNull(zip.getEntry(entry)) {
                    "Compose resource missing from APK: $entry\n" +
                    "Check assembleAndroidMainComposeResources in shared/ui/build.gradle.kts " +
                    "and the sourceSets[\"main\"].assets.srcDirs wiring in androidApp/build.gradle.kts."
                }
            }
        }
        logger.lifecycle("APK resource check passed: ${expectedEntries.size} entries verified.")
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared:ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.runtime.compose) // LocalLifecycleOwner for CameraX binding
    implementation(libs.ktor.client.okhttp)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    // ZXing reads the EAN-5 add-on; ML Kit's barcode API does not list add-ons among its formats.
    implementation(libs.zxing.core)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
}
