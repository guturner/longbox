package com.guturner.longbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.guturner.longbox.domain.library.LibraryFilter
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.presentation.ComicDetailViewModel
import com.guturner.longbox.presentation.CreatorViewModel
import com.guturner.longbox.presentation.LibraryViewModel
import com.guturner.longbox.presentation.PullListViewModel
import com.guturner.longbox.presentation.ReleasesViewModel
import com.guturner.longbox.presentation.ScanMode
import com.guturner.longbox.presentation.ScanViewModel
import com.guturner.longbox.presentation.SessionPhase
import com.guturner.longbox.presentation.SessionViewModel
import com.guturner.longbox.presentation.StoryArcViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.detail.ComicDetailScreen
import com.guturner.longbox.ui.detail.CreatorScreen
import com.guturner.longbox.ui.detail.StoryArcScreen
import com.guturner.longbox.ui.library.LibraryScreen
import com.guturner.longbox.ui.scan.ManualEntryDialog
import com.guturner.longbox.ui.scan.ScanScreen
import kotlinx.serialization.Serializable

@Serializable internal data object LibraryRoute
@Serializable internal data class ComicRoute(val id: Long)
@Serializable internal data class CreatorRoute(val id: Long)
@Serializable internal data class StoryArcRoute(val id: Long)
@Serializable internal data object ReleasesRoute
@Serializable internal data object PullListRoute
@Serializable internal data class ScanRoute(val check: Boolean)

/** Root of every client. Platform shells only supply [graph] and [platform]. */
@Composable
fun LongboxApp(graph: AppGraph, platform: PlatformServices) {
    CompositionLocalProvider(LocalPlatform provides platform) {
        LongboxTheme {
            val session = viewModel { SessionViewModel(graph.sessionRepository) }
            when (session.phase.collectAsStateWithLifecycle().value) {
                SessionPhase.RESTORING -> CenteredMessage("Opening your longbox…")
                SessionPhase.SIGNED_OUT -> SignInScreen(session)
                SessionPhase.SIGNED_IN -> SignedInApp(graph, platform, onSignOut = session::signOut)
            }
        }
    }
}

@Composable
private fun SignedInApp(graph: AppGraph, platform: PlatformServices, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val goHome: () -> Unit = { nav.popBackStack(LibraryRoute, inclusive = false) }
    // Activity/window-scoped so filters survive navigating into details and back.
    val library = viewModel {
        LibraryViewModel(graph.observeLibrary, graph.refreshCollection, graph.removeFromCollection, graph.sync)
    }
    val pullListVm = viewModel { PullListViewModel(graph.pullList, graph.collectionRepository, graph.getWeeklyReleases, graph.clock) }
    val manualScan = viewModel(key = "manual-scan") { ScanViewModel(graph.scanComic, graph.checkOwnership, graph.nowMillis) }
    var showManualEntry by remember { mutableStateOf(false) }

    NavHost(nav, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                viewModel = library,
                onOpenComic = { nav.navigate(ComicRoute(it.value)) },
                onScan = platform.scanner?.let { { nav.navigate(ScanRoute(check = false)) } },
                onCheckInShop = platform.scanner?.let { { nav.navigate(ScanRoute(check = true)) } },
                onAddManually = { showManualEntry = true },
                onOpenReleases = { nav.navigate(ReleasesRoute) },
                onOpenPullList = { nav.navigate(PullListRoute) },
                onSignOut = onSignOut,
            )
        }
        composable<ComicRoute> { entry ->
            val id = ComicId(entry.toRoute<ComicRoute>().id)
            val vm = viewModel(key = "comic-${id.value}") { ComicDetailViewModel(id, graph.getComic, graph.collectionRepository) }
            ComicDetailScreen(
                viewModel = vm,
                onGoHome = goHome,
                onOpenCreator = { nav.navigate(CreatorRoute(it.value)) },
                onOpenStoryArc = { nav.navigate(StoryArcRoute(it.id.value)) },
                onRemoveCopy = library::remove,
                onAddSeriesToPullList = pullListVm::addSeries,
            )
        }
        composable<CreatorRoute> { entry ->
            val id = CreatorId(entry.toRoute<CreatorRoute>().id)
            val vm = viewModel(key = "creator-${id.value}") { CreatorViewModel(id, graph.getCreator) }
            CreatorScreen(
                viewModel = vm,
                onGoHome = goHome,
                onShowAsWriter = { library.showOnly(LibraryFilter(writerIds = setOf(id))); goHome() },
                onShowAsArtist = { library.showOnly(LibraryFilter(artistIds = setOf(id))); goHome() },
            )
        }
        composable<StoryArcRoute> { entry ->
            val id = StoryArcId(entry.toRoute<StoryArcRoute>().id)
            val vm = viewModel(key = "arc-${id.value}") { StoryArcViewModel(id, graph.getStoryArc) }
            StoryArcScreen(
                viewModel = vm,
                onGoHome = goHome,
                onShowInLibrary = { library.showOnly(LibraryFilter(storyArcIds = setOf(id))); goHome() },
            )
        }
        composable<ReleasesRoute> {
            val vm = viewModel { ReleasesViewModel(graph.getWeeklyReleases, graph.collectionRepository, graph.clock) }
            ReleasesScreen(vm, onGoHome = goHome)
        }
        composable<PullListRoute> {
            PullListScreen(pullListVm, onGoHome = goHome)
        }
        composable<ScanRoute> { entry ->
            val scanner = platform.scanner ?: return@composable
            val check = entry.toRoute<ScanRoute>().check
            val vm = viewModel(key = "scan-$check") {
                ScanViewModel(graph.scanComic, graph.checkOwnership, graph.nowMillis, if (check) ScanMode.CHECK else ScanMode.ADD)
            }
            ScanScreen(vm, scanner, onClose = { nav.popBackStack() })
        }
    }

    if (showManualEntry) ManualEntryDialog(manualScan, onDismiss = { showManualEntry = false })
}
