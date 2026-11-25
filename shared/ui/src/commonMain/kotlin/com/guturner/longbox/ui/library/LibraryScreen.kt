package com.guturner.longbox.ui.library

import androidx.compose.foundation.clickable
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
import com.guturner.longbox.domain.model.LookupStatus
import com.guturner.longbox.presentation.LibraryViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.HomeIconButton
import com.guturner.longbox.ui.components.MetronAttribution
import com.guturner.longbox.ui.components.PendingCover

private enum class FacetKind(val label: String) { SERIES("Title"), WRITER("Writer"), ARTIST("Artist"), EVENT("Event"), PUBLISHER("Publisher") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenComic: (ComicId) -> Unit,
    onScan: (() -> Unit)?,
    onCheckInShop: (() -> Unit)?,
    onAddManually: () -> Unit,
    onOpenReleases: () -> Unit,
    onSignOut: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var openFacet by remember { mutableStateOf<FacetKind?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

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
                    TextButton(onClick = { menuOpen = true }) { Text("More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Add or check by UPC") }, onClick = { menuOpen = false; onAddManually() })
                        onCheckInShop?.let { check ->
                            DropdownMenuItem(text = { Text("Do I own this? (scan)") }, onClick = { menuOpen = false; check() })
                        }
                        DropdownMenuItem(text = { Text("Refresh") }, onClick = { menuOpen = false; viewModel.refresh() })
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { menuOpen = false; onSignOut() })
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
                    color = MaterialTheme.colorScheme.primary,
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
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(state.entries, key = { it.id.value }) { entry ->
                        LibraryTile(entry, onClick = { entry.comic?.let { onOpenComic(it.id) } })
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
        }
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

@Composable
private fun LibraryTile(entry: CollectionEntry, onClick: () -> Unit) {
    val comic = entry.comic
    Column(Modifier.clickable(enabled = comic != null, onClick = onClick)) {
        when {
            comic != null -> CoverImage(comic.coverA.imageUrl, contentDescription = comic.displayTitle)
            entry.isQueuedOnDevice -> PendingCover("Saved offline\n${entry.scannedCode}")
            entry.status == LookupStatus.PENDING -> PendingCover("Looking up\n${entry.scannedCode}")
            else -> PendingCover("Not in catalog yet\n${entry.scannedCode}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(
                comic?.let { "#${it.number.raw}" } ?: "—",
                style = MaterialTheme.typography.titleSmall,
            )
            entry.ownedCoverName?.let { Badge { Text("Variant") } }
        }
        Text(
            comic?.series?.displayName ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
