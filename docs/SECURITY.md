# Security decisions and their sources

You asked that no best practice be assumed. Each control below names the standard it implements; .org, .gov and .edu sources are preferred. Where only a vendor document exists (Android, Google Identity), that's marked **[vendor]**.

## Authentication

| Decision | Where | Source |
|---|---|---|
| Sign in with Google (OIDC) and never store passwords. The server verifies the ID token's **signature, `aud`, `iss`, `exp`** and also requires `email_verified`. | `GoogleIdentity.kt` | Google, *Authenticate with a backend server* **[vendor]**: verify the token's integrity on the server rather than trusting user ids sent by the client. |
| Allow-list of Google accounts; the server refuses to start with an empty list. | `AppConfig.kt` | OWASP API Security Top 10 2023, API2 *Broken Authentication* / API5 *Broken Function Level Authorization* ([owasp.org](https://owasp.org/projects/api-security-project)) |
| Desktop sign-in uses the **system browser**, a **loopback IP redirect** (`127.0.0.1`, ephemeral port) and **PKCE S256**. No embedded web view. | `GoogleLoopbackSignIn.kt` | RFC 8252 §8.12 (*native apps MUST NOT use embedded user-agents*), §7.3 (loopback, IP literal preferred), §6 (public clients MUST use PKCE) ([rfc-editor.org/rfc/rfc8252](https://www.rfc-editor.org/rfc/rfc8252)); RFC 9700 §2.1.1 ([rfc-editor.org/rfc/rfc9700](https://www.rfc-editor.org/rfc/rfc9700)) |
| The Google "desktop client secret" ships in the app and is **not treated as confidential**. | `DesktopConfig` | RFC 8252 §8.5 |
| Android uses Credential Manager / Sign in with Google. | `MainActivity.kt` | Android Credential Manager docs **[vendor]** |

## Sessions and tokens

| Decision | Where | Source |
|---|---|---|
| Server-issued **opaque** tokens of 256 random bits from `SecureRandom`. | `TokenService.kt` | OWASP Session Management Cheat Sheet: at least 64 bits of entropy from a CSPRNG ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)) |
| Only **SHA-256 hashes** of tokens are stored, so a DB leak ≠ session leak. | `V1__init.sql` | same cheat sheet (protect session IDs at rest) |
| 15-minute access tokens plus **rotating, single-use refresh tokens with replay detection**. Reusing a refresh token revokes the whole token family. | `TokenService.refresh` | RFC 9700 §2.2.2 / §4.14: *refresh tokens for public clients MUST be sender-constrained or use refresh token rotation* |
| New token family on every sign-in (no session fixation). | `signInWithGoogle` | OWASP Session Mgmt: renew the session ID after a privilege level change (authentication) |
| Web: tokens live only in **`Secure; HttpOnly; SameSite=Strict`** cookies with narrow `Path`s; JS never sees them. | `AuthRoutes.kt` | OWASP Session Mgmt (Secure and HttpOnly *mandatory*; SameSite=Strict preferred) |
| CSRF: cookie-authenticated unsafe requests must carry `X-Longbox-Client`; CORS is closed by default. | `Security.kt` | OWASP CSRF Prevention Cheat Sheet, *custom request headers* defence ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)) |
| Android: tokens encrypted with **AES-256-GCM using a non-exportable Android Keystore key**; stored in `noBackupFilesDir`; `allowBackup=false`. | `Storage.kt` | OWASP MASVS-STORAGE / MASVS-CRYPTO ([mas.owasp.org](https://mas.owasp.org/)); Android *Hardcoded cryptographic secrets* **[vendor]** |
| Windows: tokens in **Windows Credential Manager** (per-user, DPAPI) via java-keyring. | `desktopApp/Main.kt` | MASVS-STORAGE principle (use platform secure storage) |

## API hardening (OWASP API Security Top 10, 2023)

| Risk | Control |
|---|---|
| API1 Broken Object Level Authorization | Every collection query filters on the **authenticated** `user_id`; deleting another user's entry returns 404 (`CollectionStore`). |
| API4 Unrestricted Resource Consumption | Ktor rate limits (10/min on auth endpoints per IP, 600/min API per user), 2,000 scans/day cap, request size caps, and the release range limited to 14 days. |
| API7 SSRF | The image proxy fetches only `https` on port 443 from an allow-listed host, **does not follow redirects**, accepts only `image/*` and caps size at 8 MB. |
| API8 Security Misconfiguration | HSTS, `nosniff`, `X-Frame-Options: DENY`, restrictive CSP (`'wasm-unsafe-eval'` only because WebAssembly needs it), and generic error bodies (no stack traces). |
| API10 Unsafe Consumption of APIs | Metron pagination links are followed only if they stay on `https://metron.cloud`; responses are parsed into typed DTOs with unknown fields ignored. |

## Data, secrets and logging

| Decision | Source |
|---|---|
| Parameterised JDBC statements only; no string-built SQL. Cache table names come from a fixed allow-list. | OWASP SQL Injection Prevention Cheat Sheet |
| Secrets (DB password, Metron password) come from **Docker secrets files** (`*_FILE`) or env vars, never from source control or client apps. Metron credentials exist only on the server. | OWASP Secrets Management Cheat Sheet; OWASP Docker Security Cheat Sheet RULE #12 (*Utilize Docker Secrets*) |
| `toString()` on config masks secrets. Access logs record method, path and status only (never headers, cookies or bodies). | OWASP Logging Cheat Sheet (don't log session tokens or credentials) |
| Android cleartext traffic disabled (`network_security_config.xml`). | MASVS-NETWORK |

## Containers on the NAS

These follow the OWASP Docker Security Cheat Sheet ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Docker_Security_Cheat_Sheet.html)):
- RULE #2: run as an unprivileged user (`USER longbox`).
- RULE #3: drop capabilities (`cap_drop: [ALL]`).
- RULE #4: `no-new-privileges`.
- RULE #5: Postgres sits on an `internal: true` network with no published port.
- RULE #8: read-only root filesystem with a `tmpfs` for `/tmp`.
- RULE #12: Docker secrets.
- RULE #0: keep DSM and Container Manager updated.

## Known gaps (deliberate, for a single-user app)

- **ProGuard/R8 rules.** `androidApp/build.gradle.kts` sets `isMinifyEnabled = true` but includes no custom rules file. `kotlinx.serialization`'s serializer lookup relies on class names that R8 may rename or remove; without keep rules, a minified release APK will crash on launch with a `SerializationException`. Add a `proguard-rules.pro` with at minimum:
  ```
  -keepattributes *Annotation*, InnerClasses
  -dontnote kotlinx.serialization.**
  -keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
  -keepclasseswithmembers @kotlinx.serialization.Serializable class * { *; }
  ```
  This is the suspected cause of the 0.2.1 launch crash.

- **No certificate pinning.** Pinning is optional in MASVS, and a home NAS cert rotates through Let's Encrypt.

- **The offline library cache isn't encrypted at rest.** It holds comic metadata, not credentials, and sits inside the app sandbox or user profile. Encrypt it with the same Keystore key if you disagree.

- **No dependency-vulnerability scanning.** Add the OWASP Dependency-Check Gradle plugin or enable GitHub Dependabot (OWASP Docker RULE #9) when the repo is public.
