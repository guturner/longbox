# Longbox

Scan comic barcodes with your phone, keep your collection in sync on Android, Windows and the web, and check "do I already own this?" in a shop with no signal.

- **Clients:** one Kotlin Multiplatform codebase. Android (Compose + CameraX + ZXing), Windows (Compose Desktop, MSI) and Web (Compose/Wasm, served by the server).
- **Server:** Kotlin + Ktor + Postgres, self-hosted in Docker on a Synology DS923+.
- **Data:** [Metron](https://metron.cloud) — free, has UPC lookup, release dates, variants, events and creator bios. CC BY-SA 4.0.
- **Offline-first:** a cached library plus a durable on-device outbox. Scans made away from home sync when you're back.

## What it does

| Flow | How |
|---|---|
| Scan boxes / weekly pull | **Scan** → *Add to library*. Each barcode is validated on the device, queued, and looked up in the background at Metron's rate limit. |
| Library | Cover A thumbnails, ordered by title then issue number (handles `0`, `½`, `1.MU`, `Annual 1`…). Search, plus filters by title, writer, artist, event (story arc) and publisher. |
| Comic detail | All main covers, release (store) date and cover date, summary, event tags, creators (tap for a bio), and your copies including variants. Track a series on your pull list from here. |
| In a shop | **Do I own this?** scan → owned / own a different cover or printing / not owned. **I bought it** queues the add until you're home. |
| New releases | Per-publisher filter chips; DC Comics, Marvel, Dynamite Entertainment and Titan Comics on by default. Toggle chips to narrow or expand. Edit `FOLLOWED_PUBLISHERS` in `ReleaseUseCases.kt` to change the defaults. |
| Pull list | Track publishers and series you want to buy each week. Add a publisher from the pull list screen or add a series from any comic's detail page. Stored locally on-device. |

## Layout

```
shared/domain        entities, use cases, ports              (pure Kotlin, all tests live here)
shared/contract      HTTP DTOs + mappers (server ⇄ clients)
shared/data          Ktor client, offline outbox + cache, token ports, pull list store
shared/presentation  ViewModels
shared/ui            Compose Multiplatform screens + AppGraph
androidApp desktopApp webApp server
deploy/synology      docker-compose for Synology Container Manager
deploy/local         docker-compose for local development
docs/                ARCHITECTURE · SECURITY · DATA-SOURCES · SYNOLOGY
scripts/             release · deploy-local · synology-package · adb-debug · db-console · gen-icons
```

## Getting started

1. Free accounts: [Metron](https://metron.cloud) for comic data, and three Google OAuth 2.0 clients — Android, Desktop, and Web — from [Google Cloud Console](https://console.cloud.google.com).
2. Android SDK path: add `sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk` to `local.properties` at the root (Android Studio writes this automatically; needed for command-line builds).
3. Client properties: in `~/.gradle/gradle.properties`, add `longbox.serverUrl=https://...` and `longbox.googleWebClientId=...` (the Web OAuth client id).
4. Local dev server:
   - Create `deploy/local/secrets/db_password` and `deploy/local/secrets/metron_password`, each containing one value.
   - Copy `deploy/local/.env` and fill in `GOOGLE_CLIENT_IDS` and `ALLOWED_GOOGLE_EMAILS`.
   - `./scripts/deploy-local.sh` — builds the fat jar, the web distribution, the Docker image, and starts Postgres + server.
   - Or run the server in Gradle directly: start Postgres separately, copy `server/.env.example` → `server/.env`, then `./gradlew :server:run`.
5. Run the apps: Android from Android Studio, `./gradlew :desktopApp:run` for Windows, `./gradlew :webApp:wasmJsBrowserDevelopmentRun` for the web.
6. Deploy to Synology: [docs/SYNOLOGY.md](docs/SYNOLOGY.md).

Tests: `./gradlew allTests :server:test`.

## Status

All modules compile. Domain, presentation, data and server tests pass. Android and Desktop clients build and run. The web client (Kotlin/Wasm) is Beta per the KMP stability table — it compiles and runs but `compileCommonMainKotlinMetadata` has a pre-existing failure on `androidx.lifecycle.viewmodel.compose` that does not affect any per-target build.

Release APK: `isMinifyEnabled = true` with no custom ProGuard rules. `kotlinx.serialization`'s serializer lookup relies on class names that R8 may rename, which will cause a launch crash in a minified release build. Add a `proguard-rules.pro` before shipping — see [docs/SECURITY.md](docs/SECURITY.md#known-gaps).

Pending:
- WorkManager so Android drains the outbox when the app isn't in the foreground.
- ProGuard rules for `kotlinx.serialization`.
- Optional Tailscale on the NAS for immediate sync while roaming.

## Style notes

Constructor injection with explicit wiring (no DI framework), `/api/v1` versioned routes, explicit `asX/asXDto` mappers (nullable instead of `Optional`), `// Given / When / Then` tests, and `kotlinx.coroutines.test.runTest` for all multiplatform tests (`runBlocking` compiles on JVM only). These carry over the patterns from `queuehaven` and `codex`, translated to idiomatic Kotlin.
