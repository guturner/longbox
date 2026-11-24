# Longbox

Scan comic barcodes with your phone, keep your collection in sync on Android, Windows and the web, and check "do I already own this?" in a shop with no signal.

- **Clients:** one Kotlin Multiplatform codebase. Android (Compose + CameraX + ZXing), Windows (Compose Desktop, MSI) and Web (Compose/Wasm, served by the server).
- **Server:** Kotlin + Ktor + Postgres, self-hosted in Docker on a Synology DS923+.
- **Data:** [Metron](https://metron.cloud): free, has UPC lookup, release dates, variants, events and creator bios. CC BY-SA 4.0.
- **Offline-first:** a cached library plus a durable on-device outbox. Scans made away from home sync when you're back.

## What it does

| Flow | How |
|---|---|
| Scan boxes / weekly pull | **Scan** → *Add to library*. Each barcode is validated on the device, queued, and looked up in the background at Metron's rate limit. |
| Library | Cover A thumbnails, ordered by title then issue number (handles `0`, `½`, `1.MU`, `Annual 1`…). Search, plus filters by title, writer, artist, event (story arc) and publisher. |
| Comic detail | All main covers, release (store) date and cover date, summary, event tags, creators (tap for a bio), and your copies including variants. |
| In a shop | **Do I own this?** scan → owned / own a different cover or printing / not owned. **I bought it** queues the add until you're home. |
| New releases | "This week" view for DC, Marvel, Dynamite and Titan (edit `FOLLOWED_PUBLISHERS`). |

## Layout

```
shared/domain        entities, use cases, ports        (pure Kotlin, tested)
shared/contract      HTTP DTOs + mappers (server ⇄ clients)
shared/data          Ktor client, offline outbox + cache, token ports
shared/presentation  ViewModels
shared/ui            Compose Multiplatform screens + AppGraph
androidApp desktopApp webApp server
deploy/synology      docker-compose for Container Manager
docs/                ARCHITECTURE · SECURITY · DATA-SOURCES · SYNOLOGY
```

## Getting started

0. Generate the Gradle wrapper once: `gradle wrapper --gradle-version 9.2.1` (my sandbox couldn't download it).
1. Create a free Metron account and three Google OAuth clients (Android, Desktop, Web).
2. Local dev: `docker compose up -d` (Postgres), copy `server/.env.example` → `server/.env`, then run `./gradlew :server:run`.
3. Run the apps: Android from Android Studio, `./gradlew :desktopApp:run` for Windows, `./gradlew :webApp:wasmJsBrowserDevelopmentRun` for the web.
4. Deploy to the NAS: [docs/SYNOLOGY.md](docs/SYNOLOGY.md).

Tests: `./gradlew allTests :server:test`.

## Status

The domain, presentation and Metron-mapping code is compiled and unit-tested (36 tests). The Ktor, Compose and Android code is written against current APIs, but it hasn't been through a Gradle build yet. Expect a few compile fixes on the first sync, and bump the versions marked `check` in `gradle/libs.versions.toml`.

## Style notes

Constructor injection with explicit wiring (no DI framework), `/api/v1` versioned routes, explicit `asX/asXDto` mappers (nullable instead of `Optional`), and `// Given / When / Then` tests. These carry over the patterns from `queuehaven` and `indy-oracle2`, translated to idiomatic Kotlin.
