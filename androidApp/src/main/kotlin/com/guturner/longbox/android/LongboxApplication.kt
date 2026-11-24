package com.guturner.longbox.android

import android.app.Application
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.ui.AppGraph
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.LocalDate

class LongboxApplication : Application() {

    /** Application-scoped so the outbox keeps syncing while you move between screens. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(
            engine = OkHttp.create(),
            baseUrl = BuildConfig.LONGBOX_SERVER_URL,
            transport = SessionTransport.Bearer(KeystoreTokenStore(this)),
            localStore = FileKeyValueStore(filesDir.resolve("longbox")),
            appScope = appScope,
            clock = Clock { LocalDate.now().let { CalendarDate(it.year, it.monthValue, it.dayOfMonth) } },
            nowMillis = System::currentTimeMillis,
        )
    }
}
