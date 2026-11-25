package com.guturner.longbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.guturner.longbox.presentation.PullListViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.HomeIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullListScreen(viewModel: PullListViewModel, onGoHome: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val knownPublishers by viewModel.knownPublishers.collectAsStateWithLifecycle()
    var showAddPublisher by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pull List") },
                navigationIcon = { HomeIconButton(onGoHome) },
                actions = {
                    TextButton(onClick = { showAddPublisher = true }) { Text("+ Publisher") }
                },
            )
        },
    ) { padding ->
        if (state.entries.isEmpty()) {
            CenteredMessage(
                "Your pull list is empty.\nAdd publishers or series you plan to buy each week.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                // Group by type for a cleaner layout
                val publishers = state.entries.filterIsInstance<PullListEntry.ByPublisher>()
                val series = state.entries.filterIsInstance<PullListEntry.BySeries>()

                if (publishers.isNotEmpty()) {
                    item {
                        Text(
                            "Publishers",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(publishers, key = { it.publisherName }) { entry ->
                        PullListRow(entry.displayName, onRemove = { viewModel.remove(entry) })
                        HorizontalDivider()
                    }
                }

                if (series.isNotEmpty()) {
                    item {
                        Text(
                            "Series",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(series, key = { it.seriesId.value }) { entry ->
                        PullListRow(entry.displayName, onRemove = { viewModel.remove(entry) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAddPublisher) {
        AddPublisherDialog(
            knownPublishers = knownPublishers,
            existingPublishers = state.entries.filterIsInstance<PullListEntry.ByPublisher>()
                .map { it.publisherName }.toSet(),
            onAdd = { viewModel.addPublisher(it); showAddPublisher = false },
            onDismiss = { showAddPublisher = false },
        )
    }
}

@Composable
private fun PullListRow(label: String, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun AddPublisherDialog(
    knownPublishers: List<String>,
    existingPublishers: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val available = knownPublishers.filter { it !in existingPublishers }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add publisher") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        text = {
            Column {
                // Quick-pick chips for known publishers
                if (available.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
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
                // Escape hatch for publishers not in the chip list
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    placeholder = { Text("Add a different publisher") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.isNotBlank() && search !in knownPublishers && search !in existingPublishers) {
                    TextButton(onClick = { onAdd(search.trim()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Add \"${search.trim()}\"")
                    }
                }
            }
        },
    )
}
