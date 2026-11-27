package com.guturner.longbox.ui.containers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.ContainerKind
import com.guturner.longbox.domain.model.EntryId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToContainerSheet(
    entryId: EntryId,
    stacks: List<Container>,
    boxes: List<Container>,
    onMove: (EntryId, ContainerId) -> Unit,
    onRemoveFromContainer: (EntryId) -> Unit,
    onCreateContainer: (String, ContainerKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Text(
                "Move to container",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            if (stacks.isNotEmpty()) {
                Text(
                    "STACKS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(stacks, key = { it.id.value }) { container ->
                        ContainerPickerRow(container, onClick = { onMove(entryId, container.id); onDismiss() })
                    }
                }
            }

            if (boxes.isNotEmpty()) {
                Text(
                    "BOXES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(boxes, key = { it.id.value }) { container ->
                        ContainerPickerRow(container, onClick = { onMove(entryId, container.id); onDismiss() })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            TextButton(
                onClick = { onRemoveFromContainer(entryId); onDismiss() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("Remove from container") }
            TextButton(
                onClick = { showCreate = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("+ Create new container") }
        }
    }

    if (showCreate) {
        CreateContainerDialog(
            onCreate = { name, kind ->
                onCreateContainer(name, kind)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun ContainerPickerRow(container: Container, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(container.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${container.entryCount}", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}
