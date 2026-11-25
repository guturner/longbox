package com.guturner.longbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.presentation.Loadable
import com.guturner.longbox.presentation.PullListViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.CoverSize
import com.guturner.longbox.ui.components.HomeIconButton
import com.guturner.longbox.ui.components.MetronAttribution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullListScreen(viewModel: PullListViewModel, onGoHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddPublisher by remember { mutableStateOf(false) }
    var coverSize by remember { mutableStateOf(CoverSize.MEDIUM) }
    var sizeMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pull List · ${state.week.wednesday}") },
                navigationIcon = { HomeIconButton(onGoHome) },
                actions = {
                    TextButton(onClick = viewModel::previousWeek) { Text("‹ Prev") }
                    TextButton(onClick = viewModel::nextWeek) { Text("Next ›") }
                    TextButton(onClick = { showAddPublisher = true }) { Text("+ Publisher") }
                    TextButton(onClick = { sizeMenuOpen = true }) { Text("Size") }
                    DropdownMenu(expanded = sizeMenuOpen, onDismissRequest = { sizeMenuOpen = false }) {
                        CoverSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = { Text("Covers: ${size.label}") },
                                trailingIcon = if (coverSize == size) { { Text("✓") } } else null,
                                onClick = { sizeMenuOpen = false; coverSize = size },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (val comics = state.comics) {
                Loadable.Loading -> CenteredMessage("Loading…", Modifier.weight(1f))
                is Loadable.Failed -> CenteredMessage(
                    "${comics.message.text}\nNew releases need the home server.",
                    Modifier.weight(1f),
                )
                is Loadable.Ready -> {
                    if (comics.value.isEmpty() && state.entries.isEmpty()) {
                        CenteredMessage(
                            "Your pull list is empty.\nAdd publishers or series you plan to buy each week.",
                            Modifier.weight(1f),
                        )
                    } else if (comics.value.isEmpty()) {
                        CenteredMessage(
                            "No releases this week for your pull list publishers.",
                            Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize().weight(1f)) {
                            items(comics.value, key = { it.id.value }) { comic ->
                                val checked = comic.id in state.checkedComicIds
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CoverImage(
                                        comic.coverA.imageUrl,
                                        comic.displayTitle,
                                        Modifier.width(coverSize.listWidthDp.dp),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(comic.displayTitle, style = MaterialTheme.typography.titleSmall)
                                        Text(comic.publisher.name, style = MaterialTheme.typography.labelMedium)
                                    }
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { viewModel.toggleCheck(comic.id) },
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // Pull list entries summary at the bottom
            if (state.entries.isNotEmpty()) {
                val publishers = state.entries.filterIsInstance<PullListEntry.ByPublisher>()
                val series = state.entries.filterIsInstance<PullListEntry.BySeries>()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        buildString {
                            if (publishers.isNotEmpty()) append("${publishers.size} publisher(s)")
                            if (publishers.isNotEmpty() && series.isNotEmpty()) append(" · ")
                            if (series.isNotEmpty()) append("${series.size} series")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    TextButton(onClick = { showAddPublisher = true }) { Text("Edit") }
                }
            }

            MetronAttribution(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }

    if (showAddPublisher) {
        ManagePullListDialog(
            entries = state.entries,
            knownPublishers = state.knownPublishers,
            onAdd = { viewModel.addPublisher(it) },
            onRemove = { viewModel.remove(it) },
            onDismiss = { showAddPublisher = false },
        )
    }
}

@Composable
private fun ManagePullListDialog(
    entries: List<PullListEntry>,
    knownPublishers: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (PullListEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val existingPublishers = entries.filterIsInstance<PullListEntry.ByPublisher>().map { it.publisherName }.toSet()
    val available = knownPublishers.filter { it !in existingPublishers }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pull list publishers") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column {
                // Current entries with remove buttons
                val publishers = entries.filterIsInstance<PullListEntry.ByPublisher>()
                if (publishers.isNotEmpty()) {
                    publishers.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.publisherName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemove(entry) }) { Text("Remove") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // Quick-pick chips for publishers not yet on the list
                if (available.isNotEmpty()) {
                    Text("Add publisher", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        available.forEach { name ->
                            SuggestionChip(
                                onClick = { onAdd(name) },
                                label = { Text(name) },
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }

                // Escape hatch for unlisted publishers
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    placeholder = { Text("Add a different publisher") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.isNotBlank() && search !in knownPublishers && search !in existingPublishers) {
                    TextButton(onClick = { onAdd(search.trim()); search = "" }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add \"${search.trim()}\"")
                    }
                }
            }
        },
    )
}
