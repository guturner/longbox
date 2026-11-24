rootProject.name = "longbox"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Required by the Kotlin Wasm/JS Gradle plugin to download Node.js and Yarn tooling.
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy {
            name = "Yarn"
            setUrl("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy {
            name = "Binaryen"
            setUrl("https://github.com/WebAssembly/binaryen/releases/download")
            patternLayout {
                artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

// Clean Architecture, innermost first. Dependencies only point up this list.
include(":shared:domain")        // entities, use cases, repository ports (pure Kotlin)
include(":shared:contract")      // API DTOs + mappers shared by server and clients
include(":shared:data")          // adapters: Ktor client, DTOs, mappers, repository impls
include(":shared:presentation")  // ViewModels + UI state (no Compose)
include(":shared:ui")            // Compose Multiplatform screens shared by all clients

// Frameworks & drivers (outermost ring)
include(":androidApp")
include(":desktopApp")
include(":webApp")
include(":server")
