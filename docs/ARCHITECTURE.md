# Architecture

## Rings (Clean Architecture)

Source-code dependencies point inward only: the Dependency Rule ([UWaterloo CS 346, *Clean Architecture*](https://student.cs.uwaterloo.ca/~cs346/site/slides/clean-architecture.pdf), after R. C. Martin). Inner rings declare interfaces ("ports"), and outer rings implement them (dependency inversion). Wiring happens in one composition root per process.

```
                 ┌─────────────────────────── frameworks & drivers ───────────────────────────┐
                 │ androidApp   desktopApp   webApp           server (Ktor, Postgres, Metron)   │
                 │   │ CameraX+ZXing │ loopback OAuth │ GIS     │ routes, JDBC, MetronClient   │
                 │   └──────┬────────┴──────┬─────────┘         │                               │
                 │      :shared:ui (Compose MP screens, AppGraph = client composition root)    │
                 │          │                                    │                               │
                 │  :shared:presentation (ViewModels)   :shared:data (Ktor client, outbox,     │
                 │          │                             cache, token ports, pull list)        │
                 │          │            :shared:contract (HTTP DTOs + mappers)                 │
                 │          └─────────────► :shared:domain ◄──────────────────────┘              │
                 │      entities · use cases · ports (CollectionRepository, ComicCatalog,       │
                 │      BarcodeLookup, SyncController, SessionRepository, PullListRepository,  │
                 │      Clock)                                                                  │
                 └──────────────────────────────────────────────────────────────────────────────┘
```

| Module | Ring | Depends on | Notes |
|---|---|---|---|
| `:shared:domain` | Entities + use cases | coroutines only | Barcode parsing, issue-number ordering, filters/facets, `ResolveBarcodeUseCase`, `CheckOwnershipUseCase`, `GetWeeklyReleasesUseCase`. No framework imports. |
| `:shared:contract` | Interface adapters | domain | One HTTP DTO definition shared by the server and the clients, so the wire format can't drift. |
| `:shared:data` | Interface adapters | contract | `OfflineFirstCollectionRepository` (outbox + cache), `ApiComicCatalog`, `ApiSessionRepository`, `KeyValueStorePullListRepository`, `LongboxApi`. |
| `:shared:presentation` | Interface adapters | domain | ViewModels expose `StateFlow` UI state. Never imports `:shared:data`. |
| `:shared:ui` | Frameworks | presentation, data | Compose Multiplatform screens; `AppGraph` is the only client code that touches `:shared:data`. |
| `:server` | Frameworks | contract | Ktor; `Application.module` is the server's composition root. |

**Why Kotlin Multiplatform plus Compose Multiplatform for all three clients:** Android, desktop (JVM) and server-side JVM are *Stable* targets, and Compose UI is Stable on Android and desktop. Web on Kotlin/Wasm is *Beta* ([kotlinlang.org, stability of supported platforms](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)). Beta is acceptable for a personal web client. If it bites, Kotlin/JS (Stable) is the fallback.

**Windows packaging:** Compose Desktop builds an MSI through `jpackage` (`./gradlew :desktopApp:packageMsi`), with a private JRE and a per-user install.

**DI:** explicit constructor injection, as in `codex`. `AppGraph` (clients) and `Application.module` (server) are readable, single-file object graphs, and no DI framework leaks into the inner rings.

## Data source

**Metron** (metron.cloud) is a free community API with a `upc` filter that matches the main *or variant* cover. It has store (release) dates, story arcs, credits, variants and creator bios. See [DATA-SOURCES.md](DATA-SOURCES.md).

## The scan pipeline

```
camera ─► ZXing (UPC-A + EAN-5) ─► ScanComicUseCase (validates check digit, offline)
      ─► OfflineFirstCollectionRepository.addScan
            └─ append to on-device OUTBOX (persisted first) ─► library shows "Saved offline"
      ─► syncNow(): probe /healthz (server + DB) ─► replay outbox in order, idempotently
      ─► server: insert PENDING entry ─► ScanWorker drains at Metron's pace (≤18 req/min)
            └─ ResolveBarcodeUseCase: exact UPC+supplement, else retry as Cover A
      ─► client refreshes ─► cache replaced with the server's copy
```

Metron allows 20 requests/minute (burst) and 5,000/day (sustained). These are the `DEFAULT_THROTTLE_RATES` in Metron's `settings.py`. A new comic costs about two requests (UPC search plus issue detail), so the server accepts scans instantly and resolves them in the background. Books not in Metron yet (common in release week) are rechecked daily for 14 days.

## Offline-first (roaming)

The NAS is only reachable at home (or over a VPN). Each client follows Android's **lazy writes** strategy: write to local storage first, then notify the network at the earliest opportunity. The guide calls this *the correct choice when the data is critical to the app*, and treats the local data source as the canonical source of truth for reads ([developer.android.com, offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first); a vendor source, there's no .org equivalent).

- **Read path:** `observeEntries()` = cached server library ⊕ queued local adds ⊖ queued removals.
- **Write path:** the outbox is persisted *before* the UI is updated, using atomic file replace (`AtomicFile` on Android, `ATOMIC_MOVE` on Windows), so a crash never loses a scan.
- **Reconnect:** probe every 30 s while away and on every `onResume`. When `/healthz` (which also pings Postgres) returns 200, replay in order, then fetch the library and replace the cache.
- **Idempotency:** each queued scan has a device-generated UUID (`clientMutationId`). The server has a unique index on `(user_id, client_mutation_id)`, so a replay after a dropped connection can never add a duplicate. Deletes are idempotent (404 on replay counts as done).
- **Conflicts:** none in practice. Adds are append-only and deletes target server ids, and the server stays the source of truth.
- **Why no Kafka or SQS:** the queue has to be reachable when *nothing else is*. A broker on the NAS is offline when you are, and SQS needs an internet connection the shop basement may not have. The server-side lookup queue lives in Postgres (`FOR UPDATE SKIP LOCKED`), so the stack stays two containers.

### "Do I own this?" in a shop

`CheckOwnershipUseCase` runs only against the cached library plus the outbox. Because the barcode encodes series (UPC) and issue/cover/printing (supplement), it can tell three cases apart: *you own this exact copy*, *you own this issue but a different cover/printing*, and *not owned*. Tapping **I bought it** queues an add that syncs when you get home.

## Pull list

`PullListRepository` stores a list of `PullListEntry` values — either `ByPublisher(publisherName)` or `BySeries(seriesId, seriesName, publisherName)`. `KeyValueStorePullListRepository` serialises the list to JSON via `kotlinx.serialization` and writes it atomically through the same `KeyValueStore` used by the outbox.

Entries are purely local — no server round-trips. The pull list is intended as a weekly shopping helper: compare it against the New Releases screen to see what you're missing.

## Navigation

Each secondary screen (comic detail, creator, story arc, releases, pull list) has an `onGoHome` callback wired to `nav.popBackStack(LibraryRoute, inclusive = false)`. This pops the entire back stack back to the library root without re-creating it.

**Type annotation required:** `nav.popBackStack()` returns `Boolean`. Without an explicit type annotation, Kotlin infers the lambda as `() -> Boolean`, which does not satisfy `() -> Unit` and cascades into a wall of unrelated type errors at every call site. Always declare it as:

```kotlin
val goHome: () -> Unit = { nav.popBackStack(LibraryRoute, inclusive = false) }
```

## Build notes

### Ktor 3.x breaking change

`HttpRequestBuilder.url(urlString: String)` was removed in Ktor 3.x. Inside a request builder block, replace it with `url.takeFrom(urlString)`. The extension is in `io.ktor.http` and needs an explicit import — the compiler's "unresolved reference" message will not suggest it:

```kotlin
import io.ktor.http.takeFrom

// inside get(...) { ... }
url.takeFrom("https://example.com/api/...")
```

Fixed in `ApiRepositories.kt` and `OfflineFirstCollectionRepository.kt`.

### `compileCommonMainKotlinMetadata` failure

The metadata compiler reports an unresolved reference on `androidx.lifecycle.viewmodel.compose`. This is a pre-existing KMP toolchain issue — the per-target compilations (`compileAndroidMain`, `compileKotlinDesktop`, `compileKotlinWasmJs`) all succeed. Don't route around it by moving that import; it will resolve itself as the KMP toolchain matures.

### Multiplatform tests

Use `kotlinx.coroutines.test.runTest` for all tests in `commonTest`. `runBlocking` compiles on JVM but fails to compile for `wasmJs` and native targets.

## Pending

- WorkManager job so Android drains the outbox when the app isn't in the foreground.
- ProGuard rules for `kotlinx.serialization` in the release APK — see [SECURITY.md](SECURITY.md#known-gaps).
- Optional Tailscale on the NAS for immediate sync while roaming (see [SYNOLOGY.md](SYNOLOGY.md)).
