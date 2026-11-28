package com.guturner.longbox.ui.containers

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.presentation.ContainersViewModel
import com.guturner.longbox.ui.components.RainbowStripe
import com.guturner.longbox.ui.rainbowFor
import longbox.shared.ui.generated.resources.Res
import longbox.shared.ui.generated.resources.container_longbox
import longbox.shared.ui.generated.resources.container_shortbox
import longbox.shared.ui.generated.resources.container_stack
import longbox.shared.ui.generated.resources.longbox_logo
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainersScreen(
    viewModel: ContainersViewModel,
    onFilterByContainer: (Container) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.text); viewModel.dismissMessage() }
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                // Logo header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.longbox_logo),
                        contentDescription = "Longbox",
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Longbox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                RainbowStripe()
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showCreate = true }) { Text("Add Container") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.containers.isEmpty() && !state.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No stacks or boxes yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Tap + to create your first container.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.containers, key = { _, c -> c.id.value }) { index, container ->
                    ContainerTile(
                        container = container,
                        tileColor = rainbowFor(index),
                        onClick = { onFilterByContainer(container) },
                        onDelete = { viewModel.deleteContainer(container.id) },
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
private fun ContainerTile(
    container: Container,
    tileColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = tileColor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.height(80.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(containerImageRes(container.kind)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(80.dp).padding(8.dp),
            )
            Column(
                Modifier.weight(1f).padding(start = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    container.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    kindLabel(container.kind),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
            Badge(
                containerColor = Color.White.copy(alpha = 0.25f),
                contentColor = Color.White,
                modifier = Modifier.padding(end = 8.dp),
            ) { Text("${container.entryCount}") }
            TextButton(onClick = onDelete) {
                Text("Remove", color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun containerImageRes(kind: ContainerKind) = when (kind) {
    ContainerKind.STACK -> Res.drawable.container_stack
    ContainerKind.SHORT_BOX -> Res.drawable.container_shortbox
    ContainerKind.LONG_BOX -> Res.drawable.container_longbox
}

private fun kindLabel(kind: ContainerKind) = when (kind) {
    ContainerKind.STACK -> "Stack"
    ContainerKind.SHORT_BOX -> "Short Box"
    ContainerKind.LONG_BOX -> "Long Box"
}

@Composable
internal fun CreateContainerDialog(onCreate: (String, ContainerKind) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ContainerKind.SHORT_BOX) }

    val options = listOf(
        ContainerKind.STACK to "Stack (reading pile / shelf grouping)",
        ContainerKind.SHORT_BOX to "Short Box (~100 comics)",
        ContainerKind.LONG_BOX to "Long Box (~250 comics)",
    )

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
                options.forEach { (option, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { kind = option }.fillMaxWidth(),
                    ) {
                        RadioButton(selected = kind == option, onClick = { kind = option })
                        Text(label, Modifier.padding(start = 4.dp))
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
