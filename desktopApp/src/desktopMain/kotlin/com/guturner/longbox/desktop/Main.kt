package com.guturner.longbox.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.javakeyring.Keyring
import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.data.session.StoredTokens
import com.guturner.longbox.data.session.TokenStore
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.ui.AppGraph
import com.guturner.longbox.ui.LongboxApp
import com.guturner.longbox.ui.PlatformServices
import com.guturner.longbox.ui.ScannerContent
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Properties

// APP_VERSION is generated into Version.kt at build time from the DESKTOP_VERSION env var.
private const val RELEASES_API = "https://api.github.com/repos/guturner/longbox/releases/latest"
private const val DEFAULT_SERVER_URL = "https://longbox.local"

fun main() {
    // %APPDATA%\Longbox — roaming AppData, never touched by jpackage's per-user MSI uninstall.
    // (jpackage installs to %LOCALAPPDATA%\Longbox and wipes it; %APPDATA% is safe.)
    val appDir = appDataDir()
    val initialConfig = DesktopConfig.load(appDir)

    val platform = object : PlatformServices {
        override suspend fun requestGoogleIdToken(): String? {
            val cfg = DesktopConfig.load(appDir)
            return GoogleLoopbackSignIn(cfg.googleClientId, cfg.googleClientSecret).requestIdToken()
        }
        override val scanner: ScannerContent? = null // use a USB scanner as a keyboard on desktop
    }

    application {
        // serverUrl drives AppGraph creation; changing it reconnects immediately.
        var serverUrl by remember { mutableStateOf(initialConfig.serverUrl) }
        // Auto-open settings on first run (URL is still the default placeholder).
        var showSettings by remember { mutableStateOf(initialConfig.serverUrl == DEFAULT_SERVER_URL) }

        // Cancel the previous AppGraph's scope when the URL changes so its background
        // coroutines don't outlive the graph instance.
        val prevScope = remember { arrayOf<CoroutineScope?>(null) }
        val graph = remember(serverUrl) {
            prevScope[0]?.cancel()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            prevScope[0] = scope
            AppGraph(
                engine = OkHttp.create(),
                baseUrl = serverUrl,
                transport = SessionTransport.Bearer(WindowsCredentialTokenStore()),
                localStore = FileKeyValueStore(appDir),
                appScope = scope,
                clock = Clock { LocalDate.now().let { CalendarDate(it.year, it.monthValue, it.dayOfMonth) } },
                nowMillis = System::currentTimeMillis,
            )
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Longbox",
            state = rememberWindowState(width = 1280.dp, height = 860.dp),
        ) {
            MenuBar {
                Menu("Longbox") {
                    Item("Settings…") { showSettings = true }
                    Separator()
                    Item("Quit") { exitApplication() }
                }
            }

            LongboxApp(graph, platform)

            var updateRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
            LaunchedEffect(Unit) {
                val release = fetchLatestRelease() ?: return@LaunchedEffect
                if (isNewer(release.version, APP_VERSION)) updateRelease = release
            }
            updateRelease?.let { release ->
                AlertDialog(
                    onDismissRequest = { updateRelease = null },
                    title = { Text("Update available") },
                    text = { Text("Longbox ${release.version} is available. Download now?") },
                    confirmButton = {
                        TextButton(onClick = {
                            Desktop.getDesktop().browse(URI(release.htmlUrl))
                            updateRelease = null
                        }) { Text("Download") }
                    },
                    dismissButton = { TextButton(onClick = { updateRelease = null }) { Text("Later") } },
                )
            }

            if (showSettings) {
                SettingsDialog(
                    current = DesktopConfig.load(appDir),
                    onSave = { updated ->
                        updated.save(appDir)
                        serverUrl = updated.serverUrl
                        showSettings = false
                    },
                    // Don't allow dismissing on first run until a real URL is configured.
                    // The user can still Quit via Longbox > Quit.
                    onDismiss = { if (serverUrl != DEFAULT_SERVER_URL) showSettings = false },
                )
            }
        }
    }
}

// ── Settings dialog ───────────────────────────────────────────────────────────

@androidx.compose.runtime.Composable
private fun SettingsDialog(
    current: DesktopConfig,
    onSave: (DesktopConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var serverUrl by remember { mutableStateOf(current.serverUrl) }
    var clientId by remember { mutableStateOf(current.googleClientId) }
    var clientSecret by remember { mutableStateOf(current.googleClientSecret) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server settings") },
        text = {
            Column {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://100.x.x.x  or  https://host.ts.net") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Google OAuth Client ID") },
                    placeholder = { Text("…apps.googleusercontent.com") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Google OAuth Client Secret") },
                    placeholder = { Text("GOCSPX-…") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(DesktopConfig(serverUrl.trimEnd('/'), clientId.trim(), clientSecret.trim()))
                },
                enabled = serverUrl.isNotBlank(),
            ) { Text("Save & connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Config ────────────────────────────────────────────────────────────────────

/**
 * Public identifiers only (server URL, OAuth client id/secret).
 * Stored in %APPDATA%\Longbox\longbox.properties — survives MSI reinstalls.
 */
private data class DesktopConfig(
    val serverUrl: String,
    val googleClientId: String,
    val googleClientSecret: String,
) {
    companion object {
        fun load(dir: File): DesktopConfig {
            val props = Properties()
            File(dir, "longbox.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
            return DesktopConfig(
                serverUrl = props.getProperty("serverUrl", DEFAULT_SERVER_URL),
                googleClientId = props.getProperty("googleDesktopClientId", ""),
                googleClientSecret = props.getProperty("googleDesktopClientSecret", ""),
            )
        }
    }

    fun save(dir: File) {
        val file = File(dir, "longbox.properties")
        // Preserve any keys we don't know about.
        val props = Properties()
        if (file.exists()) file.inputStream().use { props.load(it) }
        props.setProperty("serverUrl", serverUrl)
        props.setProperty("googleDesktopClientId", googleClientId)
        props.setProperty("googleDesktopClientSecret", googleClientSecret)
        file.outputStream().use { props.store(it, "Longbox desktop configuration") }
    }
}

// ── Storage ───────────────────────────────────────────────────────────────────

/**
 * %APPDATA%\Longbox — roaming user data that survives jpackage per-user MSI reinstalls.
 * Falls back to ~/.config/Longbox on non-Windows (Linux desktop app).
 */
private fun appDataDir(): File {
    val base = System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/.config")
    return File(base, "Longbox").apply { mkdirs() }
}

/** Per-user app data; atomic replace so a crash can't corrupt the outbox. */
private class FileKeyValueStore(private val dir: File) : KeyValueStore {
    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        File(dir, "$key.json").takeIf { it.exists() }?.readText()
    }

    override suspend fun write(key: String, value: String) = withContext(Dispatchers.IO) {
        val target = File(dir, "$key.json").toPath()
        val temp = Files.createTempFile(dir.toPath(), key, ".tmp")
        Files.writeString(temp, value)
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    override suspend fun delete(key: String) = withContext(Dispatchers.IO) { File(dir, "$key.json").delete(); Unit }
}

/** Windows Credential Manager (DPAPI-protected, per Windows user) via java-keyring. */
private class WindowsCredentialTokenStore : TokenStore {
    private val keyring by lazy { Keyring.create() }

    override suspend fun load(): StoredTokens? = withContext(Dispatchers.IO) {
        runCatching {
            StoredTokens(keyring.getPassword(SERVICE, "access"), keyring.getPassword(SERVICE, "refresh"))
        }.getOrNull()
    }

    override suspend fun save(tokens: StoredTokens) = withContext(Dispatchers.IO) {
        keyring.setPassword(SERVICE, "access", tokens.accessToken)
        keyring.setPassword(SERVICE, "refresh", tokens.refreshToken)
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { keyring.deletePassword(SERVICE, "access") }
        runCatching { keyring.deletePassword(SERVICE, "refresh") }
        Unit
    }

    private companion object { const val SERVICE = "Longbox" }
}

// ── Auto-update ───────────────────────────────────────────────────────────────

private data class ReleaseInfo(val version: String, val htmlUrl: String)

private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val json = java.net.URL(RELEASES_API).readText()
        val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: return@runCatching null
        val url = Regex(""""html_url"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: return@runCatching null
        ReleaseInfo(tag.removePrefix("v"), url)
    }.getOrNull()
}

private fun isNewer(latest: String, current: String): Boolean {
    fun parse(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
    val l = parse(latest); val c = parse(current)
    for (i in 0..2) { val lv = l.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }; if (lv != cv) return lv > cv }
    return false
}
