package com.guturner.longbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** What each app shell provides that the shared UI can't do itself. */
interface PlatformServices {
    /** Runs the platform's Google sign-in and returns an ID token, or null if cancelled. */
    suspend fun requestGoogleIdToken(): String?

    /** Camera scanner, if this platform has one (Android). Calls [onDetected] for each barcode read. */
    val scanner: ScannerContent?

    /** Rewrites catalog image URLs; the web client routes them through the server's image proxy. */
    fun imageUrl(original: String): String = original

    /**
     * Renders [content] as a QR code and returns it as an [ImageBitmap] of [sizePx]×[sizePx].
     * Returns null on platforms without a QR generator (e.g. web).
     */
    fun generateQrCode(content: String, sizePx: Int): ImageBitmap? = null
}

interface ScannerContent {
    @Composable
    fun Content(onDetected: (mainCode: String, addOn: String?) -> Boolean, onClose: () -> Unit)
}

val LocalPlatform = staticCompositionLocalOf<PlatformServices> { error("PlatformServices not provided") }
