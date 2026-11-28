package com.guturner.longbox.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.guturner.longbox.ui.LongboxApp
import com.guturner.longbox.ui.PlatformServices
import com.guturner.longbox.ui.ScannerContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val app get() = application as LongboxApplication
    private var updateCheckDone = false
    // Holds a pending deep-link container UUID (longbox://containers/{uuid}) so it survives recomposition.
    private val pendingContainerId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingContainerId.value = extractContainerId(intent)
        val platform = object : PlatformServices {
            override suspend fun requestGoogleIdToken(): String? = signInWithGoogle()
            override val scanner: ScannerContent = CameraBarcodeScanner
            override fun generateQrCode(content: String, sizePx: Int): ImageBitmap? = runCatching {
                val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                for (x in 0 until sizePx) for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
                bmp.asImageBitmap()
            }.getOrNull()
        }
        setContent {
            LongboxApp(
                app.graph,
                platform,
                pendingContainerId = pendingContainerId.value,
                onContainerNavConsumed = { pendingContainerId.value = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingContainerId.value = extractContainerId(intent)
    }

    private fun extractContainerId(intent: Intent?): String? =
        intent?.data?.takeIf { it.scheme == "longbox" && it.host == "containers" }?.lastPathSegment

    override fun onResume() {
        super.onResume()
        // Walking in the front door is the usual "back online" moment: try to drain the outbox.
        lifecycleScope.launch { app.graph.sync.syncNow() }
        if (!updateCheckDone) {
            updateCheckDone = true
            lifecycleScope.launch { checkForUpdate() }
        }
    }

    private suspend fun checkForUpdate() {
        val release = fetchLatestRelease() ?: return
        if (!isNewer(release.version, BuildConfig.VERSION_NAME)) return
        android.app.AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage("Longbox ${release.version} is available.")
            .setPositiveButton("Download") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
            }
            .setNegativeButton("Later", null)
            .show()
    }

    /**
     * Sign in with Google through Credential Manager. The ID token's audience is the *web* client id,
     * which the server lists in GOOGLE_CLIENT_IDS.
     */
    private suspend fun signInWithGoogle(): String? {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(this).getCredential(this, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            null
        }
    }
}

private data class ReleaseInfo(val version: String, val htmlUrl: String)

private suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val json = java.net.URL("https://api.github.com/repos/guturner/longbox/releases/latest").readText()
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
