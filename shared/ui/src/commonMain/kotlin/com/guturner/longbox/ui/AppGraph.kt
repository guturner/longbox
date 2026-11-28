package com.guturner.longbox.ui

import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.local.StoreKeys
import com.guturner.longbox.data.remote.LongboxApi
import com.guturner.longbox.data.repository.ApiComicCatalog
import com.guturner.longbox.data.repository.ApiSessionRepository
import com.guturner.longbox.data.repository.KeyValueStorePullListRepository
import com.guturner.longbox.data.repository.OfflineFirstCollectionRepository
import com.guturner.longbox.data.repository.OfflineFirstContainerRepository
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.domain.repository.Clock
import com.guturner.longbox.domain.repository.ContainerRepository
import com.guturner.longbox.domain.repository.PullListRepository
import com.guturner.longbox.domain.repository.SyncController
import com.guturner.longbox.domain.usecase.CheckOwnershipUseCase
import com.guturner.longbox.domain.usecase.GetComicDetailUseCase
import com.guturner.longbox.domain.usecase.GetCreatorUseCase
import com.guturner.longbox.domain.usecase.GetStoryArcUseCase
import com.guturner.longbox.domain.usecase.GetWeeklyReleasesUseCase
import com.guturner.longbox.domain.usecase.ObserveLibraryUseCase
import com.guturner.longbox.domain.usecase.RefreshCollectionUseCase
import com.guturner.longbox.domain.usecase.RemoveFromCollectionUseCase
import com.guturner.longbox.domain.usecase.ScanComicUseCase
import com.guturner.longbox.ui.components.CoverSize
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Client composition root (manual constructor injection). Each app module builds one of these
 * with its own HTTP engine, token storage and base URL; nothing else references :shared:data.
 */
class AppGraph(
    engine: HttpClientEngine,
    baseUrl: String,
    transport: SessionTransport,
    private val localStore: KeyValueStore,
    private val appScope: CoroutineScope,
    val clock: Clock,
    val nowMillis: () -> Long,
) {
    private val api = LongboxApi(engine, baseUrl, transport)

    private val _coverSize = MutableStateFlow(CoverSize.MEDIUM)
    internal val coverSize: StateFlow<CoverSize> = _coverSize.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    internal val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        appScope.launch {
            localStore.read(StoreKeys.COVER_SIZE)
                ?.let { name -> CoverSize.entries.find { it.name == name } }
                ?.let { _coverSize.value = it }
            localStore.read(StoreKeys.THEME_MODE)
                ?.let { name -> ThemeMode.entries.find { it.name == name } }
                ?.let { _themeMode.value = it }
        }
    }

    internal fun setCoverSize(size: CoverSize) {
        _coverSize.value = size
        appScope.launch { localStore.write(StoreKeys.COVER_SIZE, size.name) }
    }

    internal fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        appScope.launch { localStore.write(StoreKeys.THEME_MODE, mode.name) }
    }

    private val offlineFirst = OfflineFirstCollectionRepository(api, localStore, appScope, nowMillis)
    val collectionRepository = offlineFirst
    val sync: SyncController = offlineFirst
    val containerRepository: ContainerRepository = OfflineFirstContainerRepository(api, localStore, appScope, nowMillis)
    val catalog = ApiComicCatalog(api, offlineFirst)
    val sessionRepository = ApiSessionRepository(api, transport)

    val observeLibrary = ObserveLibraryUseCase(collectionRepository)
    val refreshCollection = RefreshCollectionUseCase(collectionRepository)
    val removeFromCollection = RemoveFromCollectionUseCase(collectionRepository)
    val scanComic = ScanComicUseCase(collectionRepository)
    val checkOwnership = CheckOwnershipUseCase(collectionRepository)
    val getComic = GetComicDetailUseCase(catalog)
    val getCreator = GetCreatorUseCase(catalog)
    val getStoryArc = GetStoryArcUseCase(catalog)
    val getWeeklyReleases = GetWeeklyReleasesUseCase(catalog, clock)
    val pullList: PullListRepository = KeyValueStorePullListRepository(localStore, appScope, nowMillis)
}
