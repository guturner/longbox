package com.guturner.longbox.ui

import com.guturner.longbox.data.local.KeyValueStore
import com.guturner.longbox.data.remote.LongboxApi
import com.guturner.longbox.data.repository.OfflineFirstCollectionRepository
import com.guturner.longbox.data.repository.ApiComicCatalog
import com.guturner.longbox.data.repository.ApiSessionRepository
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.domain.repository.Clock
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
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope

/**
 * Client composition root (manual constructor injection). Each app module builds one of these
 * with its own HTTP engine, token storage and base URL; nothing else references :shared:data.
 */
class AppGraph(
    engine: HttpClientEngine,
    baseUrl: String,
    transport: SessionTransport,
    localStore: KeyValueStore,
    appScope: CoroutineScope,
    val clock: Clock,
    val nowMillis: () -> Long,
) {
    private val api = LongboxApi(engine, baseUrl, transport)

    private val offlineFirst = OfflineFirstCollectionRepository(api, localStore, appScope, nowMillis)
    val collectionRepository = offlineFirst
    val sync: SyncController = offlineFirst
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
}
