package com.guturner.longbox.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.presentation.ComicDetailViewModel
import com.guturner.longbox.presentation.ContainersState
import com.guturner.longbox.presentation.ContainersViewModel
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
import com.guturner.longbox.ui.components.CoverSize
import com.guturner.longbox.ui.containers.ContainersScreen
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
@Serializable internal data class ScanRoute(val check: Boolean, val containerId: String? = null)
@Serializable internal data object ContainersRoute

/** Root of every client. Platform shells only supply [graph] and [platform].
 * [pendingContainerId] is the UUID string from a deep link (longbox://containers/{uuid}), if any.
 * [onContainerNavConsumed] is called once navigation has been triggered, so the host can clear it.
 */
@Composable
fun LongboxApp(
    graph: AppGraph,
    platform: PlatformServices,
    pendingContainerId: String? = null,
    onContainerNavConsumed: () -> Unit = {},
) {
    val themeMode by graph.themeMode.collectAsStateWithLifecycle()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalPlatform provides platform) {
        LongboxTheme(isDark = isDark) {
            val session = viewModel { SessionViewModel(graph.sessionRepository) }
            when (session.phase.collectAsStateWithLifecycle().value) {
                SessionPhase.RESTORING -> CenteredMessage("Opening your longbox…")
                SessionPhase.SIGNED_OUT -> SignInScreen(session)
                SessionPhase.SIGNED_IN -> SignedInApp(graph, platform, themeMode, onSignOut = session::signOut, onThemeModeChange = graph::setThemeMode, pendingContainerId = pendingContainerId, onContainerNavConsumed = onContainerNavConsumed)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(
    graph: AppGraph,
    platform: PlatformServices,
    themeMode: ThemeMode,
    onSignOut: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    pendingContainerId: String? = null,
    onContainerNavConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()
    // "Go home" means back to the library list (used by deep screens like Creator/StoryArc).
    val goHome: () -> Unit = { nav.popBackStack(LibraryRoute, inclusive = false) }
    // Activity/window-scoped so filters survive navigating into details and back.
    val library: LibraryViewModel = viewModel {
        LibraryViewModel(graph.observeLibrary, graph.refreshCollection, graph.removeFromCollection, graph.sync, graph.containerRepository)
    }
    val containersVm: ContainersViewModel = viewModel { ContainersViewModel(graph.containerRepository) }
    val containersState: ContainersState by containersVm.state.collectAsStateWithLifecycle()
    val pullListVm: PullListViewModel = viewModel { PullListViewModel(graph.pullList, graph.collectionRepository, graph.getWeeklyReleases, graph.clock) }
    val manualScan: ScanViewModel = viewModel(key = "manual-scan") { ScanViewModel(graph.scanComic, graph.checkOwnership, graph.nowMillis, containerRepository = graph.containerRepository) }
    var showManualEntry by remember { mutableStateOf(false) }
    var activeContainer by remember { mutableStateOf<Container?>(null) }
    val coverSize by graph.coverSize.collectAsStateWithLifecycle()

    NavHost(nav, startDestination = ContainersRoute) {
        composable<ContainersRoute> {
            ContainersScreen(
                viewModel = containersVm,
                onFilterByContainer = { container ->
                    activeContainer = container
                    library.showOnly(LibraryFilter(containerIds = setOf(container.id)))
                    nav.navigate(LibraryRoute)
                },
                onBrowseAll = {
                    activeContainer = null
                    library.clearFilters()
                    nav.navigate(LibraryRoute)
                },
                onOpenPullList = { nav.navigate(PullListRoute) },
            )
        }
        composable<LibraryRoute> {
            LibraryScreen(
                viewModel = library,
                activeContainer = activeContainer,
                onBack = { nav.popBackStack() },
                onOpenComic = { nav.navigate(ComicRoute(it.value)) },
                onScan = platform.scanner?.let { { nav.navigate(ScanRoute(check = false, containerId = activeContainer?.id?.value)) } },
                onCheckInShop = platform.scanner?.let { { nav.navigate(ScanRoute(check = true)) } },
                onAddManually = { showManualEntry = true },
                onOpenReleases = { nav.navigate(ReleasesRoute) },
                onOpenPullList = { nav.navigate(PullListRoute) },
                onSignOut = onSignOut,
                coverSize = coverSize,
                onCoverSizeChange = graph::setCoverSize,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                containers = containersState.containers,
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
                containers = containersState.containers,
                onMoveToContainer = library::moveToContainer,
                onRemoveFromContainer = library::removeFromContainer,
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
            ReleasesScreen(vm, onGoHome = goHome, coverSize = coverSize, onCoverSizeChange = graph::setCoverSize)
        }
        composable<PullListRoute> {
            PullListScreen(pullListVm, onGoHome = goHome, coverSize = coverSize, onCoverSizeChange = graph::setCoverSize)
        }
        composable<ScanRoute> { entry ->
            val scanner = platform.scanner ?: return@composable
            val route = entry.toRoute<ScanRoute>()
            val initialContainerId = route.containerId?.let { com.guturner.longbox.domain.model.ContainerId(it) }
            val vm = viewModel(key = "scan-${route.check}-${route.containerId}") {
                ScanViewModel(graph.scanComic, graph.checkOwnership, graph.nowMillis, if (route.check) ScanMode.CHECK else ScanMode.ADD, graph.containerRepository, initialContainerId)
            }
            ScanScreen(vm, scanner, onClose = { nav.popBackStack() })
        }
    }

    // Deep-link navigation: longbox://containers/{uuid} → open that container's library view.
    LaunchedEffect(pendingContainerId, containersState.containers) {
        val id = pendingContainerId ?: return@LaunchedEffect
        val container = containersState.containers.firstOrNull { it.id.value.toString() == id }
        if (container != null) {
            onContainerNavConsumed()
            activeContainer = container
            library.showOnly(LibraryFilter(containerIds = setOf(container.id)))
            nav.navigate(LibraryRoute) {
                popUpTo(ContainersRoute) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    if (showManualEntry) ManualEntryDialog(manualScan, onDismiss = { showManualEntry = false })
}
