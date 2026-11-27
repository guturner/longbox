package com.guturner.longbox.ui.containers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.presentation.ContainersViewModel
import com.guturner.longbox.ui.components.HomeIconButton
import com.guturner.longbox.ui.rainbowFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainersScreen(
    viewModel: ContainersViewModel,
    onGoHome: () -> Unit,
    onFilterByContainer: (ContainerId) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.text); viewModel.dismissMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stacks & Boxes") },
                navigationIcon = { HomeIconButton(onClick = onGoHome) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreate = true }) { Text("New container") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (state.stacks.isNotEmpty()) {
                item { SectionHeader("Stacks") }
                items(state.stacks, key = { it.id.value }) { container ->
                    ContainerRow(
                        container = container,
                        onClick = { onFilterByContainer(container.id) },
                        onDelete = { viewModel.deleteContainer(container.id) },
                    )
                }
            }

            if (state.boxes.isNotEmpty()) {
                item { SectionHeader("Boxes") }
                items(state.boxes, key = { it.id.value }) { container ->
                    ContainerRow(
                        container = container,
                        onClick = { onFilterByContainer(container.id) },
                        onDelete = { viewModel.deleteContainer(container.id) },
                    )
                }
            }

            if (state.stacks.isEmpty() && state.boxes.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        "No stacks or boxes yet.\nTap + to create one.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateContainerDialog(
            onCreate = { name, kind -> viewModel.createContainer(name, kind); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun ContainerRow(container: Container, onClick: () -> Unit, onDelete: () -> Unit) {
    val accent = rainbowFor(container.id.value.hashCode())
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Text(container.name, style = MaterialTheme.typography.bodyLarge)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge { Text("${container.entryCount}") }
            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }
    HorizontalDivider()
}

@Composable
internal fun CreateContainerDialog(onCreate: (String, ContainerKind) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ContainerKind.STACK) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New container") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                ContainerKind.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { kind = option },
                    ) {
                        RadioButton(selected = kind == option, onClick = { kind = option })
                        Text(
                            if (option == ContainerKind.STACK) "Stack (shelf grouping)" else "Box (physical storage box)",
                            Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, kind) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
