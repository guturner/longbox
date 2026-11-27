package com.guturner.longbox.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.domain.library.Facet
import com.guturner.longbox.domain.library.LibrarySort
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.presentation.LibraryViewModel
import com.guturner.longbox.ui.ThemeMode
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.CoverSize
import com.guturner.longbox.ui.components.HomeIconButton
import com.guturner.longbox.ui.components.MetronAttribution
import com.guturner.longbox.ui.components.PendingCover
import com.guturner.longbox.ui.containers.MoveToContainerSheet

private enum class FacetKind(val label: String) { SERIES("Title"), WRITER("Writer"), ARTIST("Artist"), EVENT("Event"), PUBLISHER("Publisher"), CONTAINER("Container") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenComic: (ComicId) -> Unit,
    onScan: (() -> Unit)?,
    onCheckInShop: (() -> Unit)?,
    onAddManually: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenPullList: () -> Unit,
    onOpenContainers: () -> Unit,
    onSignOut: () -> Unit,
    coverSize: CoverSize,
    onCoverSizeChange: (CoverSize) -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    stacks: List<Container> = emptyList(),
    boxes: List<Container> = emptyList(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var openFacet by remember { mutableStateOf<FacetKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var moveSheetEntry by remember { mutableStateOf<EntryId?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.text); viewModel.dismissMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Longbox · ${state.totalInCollection}") },
                navigationIcon = { HomeIconButton(onClick = {}) },
                actions = {
                    TextButton(onClick = onOpenReleases) { Text("New Releases") }
                    TextButton(onClick = onOpenPullList) { Text("Pull List") }
                    TextButton(onClick = { menuOpen = true }) { Text("More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Add or check by UPC") }, onClick = { menuOpen = false; onAddManually() })
                        onCheckInShop?.let { check ->
                            DropdownMenuItem(text = { Text("Do I own this? (scan)") }, onClick = { menuOpen = false; check() })
                        }
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; viewModel.refresh() })
                        if (state.entries.any { it.comic == null }) {
                            DropdownMenuItem(text = { Text("Remove all unresolved") }, onClick = { menuOpen = false; viewModel.removeAllUnresolved() })
                        }
                        DropdownMenuItem(text = { Text("Stacks & Boxes") }, onClick = { menuOpen = false; onOpenContainers() })
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
                        androidx.compose.material3.HorizontalDivider()
                        CoverSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = { Text("Covers: ${size.label}") },
                                trailingIcon = if (coverSize == size) { { Text("✓") } } else null,
                                onClick = { menuOpen = false; onCoverSizeChange(size) },
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()
                        ThemeMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text("Theme: ${mode.label}") },
                                trailingIcon = if (themeMode == mode) { { Text("✓") } } else null,
                                onClick = { menuOpen = false; onThemeModeChange(mode) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (onScan != null) ExtendedFloatingActionButton(onClick = onScan) { Text("Scan") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query.filter.text,
                onValueChange = viewModel::setText,
                placeholder = { Text("Search title, creator, event") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )

            FilterRow(
                selectedCounts = mapOf(
                    FacetKind.SERIES to state.query.filter.seriesIds.size,
                    FacetKind.WRITER to state.query.filter.writerIds.size,
                    FacetKind.ARTIST to state.query.filter.artistIds.size,
                    FacetKind.EVENT to state.query.filter.storyArcIds.size,
                    FacetKind.PUBLISHER to state.query.filter.publisherIds.size,
                    FacetKind.CONTAINER to state.query.filter.containerIds.size,
                ),
                sort = state.query.sort,
                onOpen = { openFacet = it },
                onSort = viewModel::setSort,
                onClear = viewModel::clearFilters.takeIf { !state.query.filter.isEmpty },
            )

            state.syncBanner?.let { banner ->
                Text(
                    banner,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.sync.serverReachable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            if (state.pendingCount > 0 && state.sync.serverReachable) {
                Text(
                    "Looking up ${state.pendingCount} scanned comic(s)…",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            if (state.entries.isEmpty()) {
                CenteredMessage(
                    if (state.totalInCollection == 0) "Your longbox is empty. Scan a barcode to add your first comic."
                    else "No comics match these filters.",
                    Modifier.weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = coverSize.gridMinDp.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.entries, key = { it.id.value }) { entry ->
                        LibraryTile(
                            entry,
                            onClick = { entry.comic?.let { onOpenComic(it.id) } },
                            onLongClick = { moveSheetEntry = entry.id },
                            onDismiss = if (entry.comic == null) { { viewModel.remove(entry.id) } } else null,
                        )
                    }
                }
            }
            MetronAttribution(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }

    openFacet?.let { kind ->
        val facets = state.facets
        when (kind) {
            FacetKind.SERIES -> FacetPickerDialog(kind.label, facets.series, state.query.filter.seriesIds, viewModel::toggleSeries) { openFacet = null }
            FacetKind.WRITER -> FacetPickerDialog(kind.label, facets.writers, state.query.filter.writerIds, viewModel::toggleWriter) { openFacet = null }
            FacetKind.ARTIST -> FacetPickerDialog(kind.label, facets.artists, state.query.filter.artistIds, viewModel::toggleArtist) { openFacet = null }
            FacetKind.EVENT -> FacetPickerDialog(kind.label, facets.storyArcs, state.query.filter.storyArcIds, viewModel::toggleStoryArc) { openFacet = null }
            FacetKind.PUBLISHER -> FacetPickerDialog(kind.label, facets.publishers, state.query.filter.publisherIds, viewModel::togglePublisher) { openFacet = null }
            FacetKind.CONTAINER -> ContainerPickerDialog(
                stacks = stacks,
                boxes = boxes,
                selected = state.query.filter.containerIds,
                onToggle = viewModel::toggleContainer,
                onDismiss = { openFacet = null },
            )
        }
    }

    moveSheetEntry?.let { entryId ->
        MoveToContainerSheet(
            entryId = entryId,
            stacks = stacks,
            boxes = boxes,
            onMove = { id, containerId -> viewModel.moveToContainer(id, containerId) },
            onRemoveFromContainer = { id -> viewModel.removeFromContainer(id) },
            onCreateContainer = { _, _ -> /* navigate to containers screen */ },
            onDismiss = { moveSheetEntry = null },
        )
    }
}

@Composable
private fun FilterRow(
    selectedCounts: Map<FacetKind, Int>,
    sort: LibrarySort,
    onOpen: (FacetKind) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onClear: (() -> Unit)?,
) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FacetKind.entries.forEach { kind ->
            val count = selectedCounts[kind] ?: 0
            FilterChip(
                selected = count > 0,
                onClick = { onOpen(kind) },
                label = { Text(if (count > 0) "${kind.label} ($count)" else kind.label) },
            )
        }
        onClear?.let { TextButton(onClick = it) { Text("Clear") } }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { sortOpen = true }) { Text("Sort: ${sort.label}") }
        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { sortOpen = false; onSort(option) })
            }
        }
    }
}

private val LibrarySort.label: String
    get() = when (this) {
        LibrarySort.SERIES_THEN_ISSUE -> "Title & issue #"
        LibrarySort.RELEASE_DATE_NEWEST -> "Release date"
        LibrarySort.RECENTLY_ADDED -> "Recently added"
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTile(
    entry: CollectionEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val comic = entry.comic
    val colorKey = entry.id.value.hashCode()
    Column(Modifier.combinedClickable(enabled = true, onClick = { if (comic != null) onClick() }, onLongClick = onLongClick)) {
        when {
            comic != null -> CoverImage(comic.coverA.imageUrl, contentDescription = comic.displayTitle)
            entry.isQueuedOnDevice -> PendingCover("Saved offline\n${entry.scannedCode}", colorKey)
            entry.state == EntryState.PENDING -> PendingCover("Looking up\n${entry.scannedCode}", colorKey)
            else -> PendingCover("Not in catalog yet\n${entry.scannedCode}", colorKey)
        }
        onDismiss?.let { dismiss ->
            TextButton(onClick = dismiss, modifier = Modifier.padding(top = 2.dp)) { Text("Remove") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                comic?.let { "#${it.number.raw}" } ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            entry.ownedCoverName?.let { Badge { Text("Variant") } }
            if (entry.copyCount > 1) Badge { Text("×${entry.copyCount}") }
        }
        entry.containerName?.let { name ->
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) { Text(name) }
        }
        Text(
            comic?.series?.displayName ?: "Unknown",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContainerPickerDialog(
    stacks: List<Container>,
    boxes: List<Container>,
    selected: Set<ContainerId>,
    onToggle: (ContainerId) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = stacks + boxes
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Filter by Container") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                items(all.size) { index ->
                    val container = all[index]
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(container.id) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            (if (container.id in selected) "✓ " else "") + container.name,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${container.entryCount}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
    )
}

@Composable
private fun <K> FacetPickerDialog(
    title: String,
    facets: List<Facet<K>>,
    selected: Set<K>,
    onToggle: (K) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Filter by $title") },
        text = {
            Column {
                OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text("Search") })
                androidx.compose.foundation.lazy.LazyColumn(Modifier.padding(top = 8.dp)) {
                    val visible = facets.filter { it.label.contains(search, ignoreCase = true) }
                    items(visible.size) { index ->
                        val facet = visible[index]
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(facet.key) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text((if (facet.key in selected) "✓ " else "") + facet.label, modifier = Modifier.weight(1f))
                            Text("${facet.count}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
    )
}
