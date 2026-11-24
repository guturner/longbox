package com.guturner.longbox.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.ui.AppGraph
import com.guturner.longbox.ui.LongboxApp
import com.guturner.longbox.ui.PlatformServices
import com.guturner.longbox.ui.ScannerContent
import io.ktor.client.engine.js.Js
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlin.js.Promise

/** Defined in gis-bridge.js. */
private external fun longboxGoogleSignIn(): Promise<JsString?>

@JsFun("(s) => encodeURIComponent(s)")
private external fun encodeUriComponent(s: String): String

@JsFun("() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); }")
private external fun todayYmd(): Int

@JsFun("() => Date.now()")
private external fun nowMillis(): Double

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val graph = AppGraph(
        engine = Js.create(),
        // Served by the Longbox server itself: same origin, so HttpOnly SameSite=Strict cookies just work.
        baseUrl = window.location.origin,
        transport = SessionTransport.CookieSession,
        localStore = LocalStorageKeyValueStore,
        appScope = CoroutineScope(SupervisorJob()),
        clock = Clock { todayYmd().let { CalendarDate(it / 10000, it / 100 % 100, it % 100) } },
        nowMillis = { nowMillis().toLong() },
    )
    val platform = object : PlatformServices {
        override suspend fun requestGoogleIdToken(): String? = longboxGoogleSignIn().await<JsString?>()?.toString()
        override val scanner: ScannerContent? = null
        // The catalog's image CDN may not allow cross-origin fetches from Wasm; go via the server.
        override fun imageUrl(original: String) = "/api/v1/image?src=${encodeUriComponent(original)}"
    }
    ComposeViewport(document.body!!) { LongboxApp(graph, platform) }
}

/** No tokens are stored here (cookies are HttpOnly); only the library cache and outbox. */
private object LocalStorageKeyValueStore : KeyValueStore {
    override suspend fun read(key: String): String? = localStorage.getItem("longbox.$key")
    override suspend fun write(key: String, value: String) = localStorage.setItem("longbox.$key", value)
    override suspend fun delete(key: String) = localStorage.removeItem("longbox.$key")
}
