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
