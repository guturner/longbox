package com.guturner.longbox.desktop

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Properties

private const val APP_VERSION = "0.1.0"
private const val RELEASES_API = "https://api.github.com/repos/guturner/longbox/releases/latest"

fun main() {
    val config = DesktopConfig.load()
    val graph = AppGraph(
        engine = OkHttp.create(),
        baseUrl = config.serverUrl,
        transport = SessionTransport.Bearer(WindowsCredentialTokenStore()),
        localStore = FileKeyValueStore(appDataDir()),
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        clock = Clock { LocalDate.now().let { CalendarDate(it.year, it.monthValue, it.dayOfMonth) } },
        nowMillis = System::currentTimeMillis,
    )
    val signIn = GoogleLoopbackSignIn(config.googleClientId, config.googleClientSecret)
    val platform = object : PlatformServices {
        override suspend fun requestGoogleIdToken() = signIn.requestIdToken()
        override val scanner: ScannerContent? = null // typed UPC entry on desktop; use a USB scanner as a keyboard
    }

    application {
        Window(onCloseRequest = ::exitApplication, title = "Longbox", state = rememberWindowState(width = 1280.dp, height = 860.dp)) {
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
                    dismissButton = {
                        TextButton(onClick = { updateRelease = null }) { Text("Later") }
                    }
                )
            }
        }
    }
}

/** Public identifiers only (server URL, OAuth client id); read from %APPDATA%\Longbox\longbox.properties. */
private data class DesktopConfig(val serverUrl: String, val googleClientId: String, val googleClientSecret: String) {
    companion object {
        fun load(): DesktopConfig {
            val props = Properties()
            File(appDataDir(), "longbox.properties").takeIf { it.exists() }?.inputStream()?.use(props::load)
            return DesktopConfig(
                serverUrl = props.getProperty("serverUrl", "https://longbox.local"),
                googleClientId = props.getProperty("googleDesktopClientId", ""),
                googleClientSecret = props.getProperty("googleDesktopClientSecret", ""),
            )
        }
    }
}

private fun appDataDir(): File {
    val base = System.getenv("LOCALAPPDATA") ?: (System.getProperty("user.home") + "/.local/share")
    return File(base, "Longbox").apply { mkdirs() }
}

/** Per-user app data directory; atomic replace so a crash can't corrupt the outbox. */
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

    private companion object {
        const val SERVICE = "Longbox"
    }
}

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
    val l = parse(latest)
    val c = parse(current)
    for (i in 0..2) {
        val lv = l.getOrElse(i) { 0 }
        val cv = c.getOrElse(i) { 0 }
        if (lv != cv) return lv > cv
    }
    return false
}
