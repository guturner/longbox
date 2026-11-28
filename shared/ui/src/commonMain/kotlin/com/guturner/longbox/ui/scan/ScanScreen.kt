package com.guturner.longbox.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3Api
import com.guturner.longbox.domain.model.ContainerId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.presentation.DuplicateScan
import com.guturner.longbox.presentation.OwnershipCard
import com.guturner.longbox.presentation.ScanMode
import com.guturner.longbox.presentation.ScanViewModel
import com.guturner.longbox.ui.ScannerContent

/** Camera scanning with ADD (boxes, weekly pull) and CHECK ("do I own this?" in a shop) modes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel, scanner: ScannerContent, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            scanner.Content(onDetected = viewModel::onDetected, onClose = onClose)
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeToggle(state.mode, viewModel::setMode)
            when (state.mode) {
                ScanMode.CHECK -> state.ownership?.let { OwnershipResult(it, onAdd = viewModel::addCheckedComic) }
                    ?: Text("Point at a barcode to check your library. Works offline.")
                ScanMode.ADD -> {
                    ContainerPicker(
                        containers = state.containers,
                        selectedId = state.selectedContainerId,
                        onSelect = viewModel::selectContainer,
                    )
                    Text("${state.sessionCount} added this session", style = MaterialTheme.typography.titleSmall)
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(state.recent) { feedback ->
                            Text(
                                feedback.message.text,
                                color = if (feedback.message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onClose) { Text("Done") }
        }
    }

    state.duplicatePending?.let { dup ->
        DuplicateDialog(dup, onConfirm = viewModel::confirmAddCopy, onDismiss = viewModel::dismissDuplicate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainerPicker(
    containers: List<com.guturner.longbox.domain.model.Container>,
    selectedId: ContainerId?,
    onSelect: (ContainerId?) -> Unit,
) {
    if (containers.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val selected = containers.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "No container",
            onValueChange = {},
            readOnly = true,
            label = { Text("Scan into") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            containers.sortedWith(compareBy({ it.kind.ordinal }, { it.createdAtEpochMillis }))
                .forEach { container ->
                    DropdownMenuItem(
                        text = { Text("${container.kind.name.lowercase().replaceFirstChar(Char::uppercase).replace("_", " ")} · ${container.name}") },
                        onClick = { onSelect(container.id); expanded = false },
                    )
                }
        }
    }
}

@Composable
private fun ModeToggle(mode: ScanMode, onMode: (ScanMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == ScanMode.ADD, onClick = { onMode(ScanMode.ADD) }, label = { Text("Add to library") })
        FilterChip(selected = mode == ScanMode.CHECK, onClick = { onMode(ScanMode.CHECK) }, label = { Text("Do I own this?") })
    }
}

@Composable
fun OwnershipResult(card: OwnershipCard, onAdd: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (card.owned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleLarge)
            Text(card.detail, style = MaterialTheme.typography.bodyMedium)
            if (card.barcode != null) {
                Button(onClick = onAdd) { Text("I bought it: add to library") }
            }
        }
    }
}

@Composable
private fun DuplicateDialog(dup: DuplicateScan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Already in your library") },
        text = { Text("You have ${dup.copyCount} cop${if (dup.copyCount == 1) "y" else "ies"} of \"${dup.displayTitle}\". Add another copy?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Add copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } },
    )
}

/**
 * Typed UPC entry for platforms without a camera (Windows, web) and for damaged barcodes.
 * Same two modes as the camera.
 */
@Composable
fun ManualEntryDialog(viewModel: ScanViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var upc by remember { mutableStateOf("") }
    var addOn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter barcode") },
        confirmButton = {
            Button(onClick = { viewModel.onDetected(upc.trim(), addOn.trim().ifEmpty { null }) }) {
                Text(if (state.mode == ScanMode.ADD) "Add" else "Check")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeToggle(state.mode, viewModel::setMode)
                OutlinedTextField(
                    upc, { upc = it.filter(Char::isDigit).take(17) },
                    label = { Text("UPC (12 digits)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    addOn, { addOn = it.filter(Char::isDigit).take(5) },
                    label = { Text("Small 5-digit code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (state.mode == ScanMode.CHECK) state.ownership?.let { OwnershipResult(it, viewModel::addCheckedComic) }
                else state.recent.firstOrNull()?.let { Text(it.message.text) }
            }
        },
    )

    state.duplicatePending?.let { dup ->
        DuplicateDialog(dup, onConfirm = viewModel::confirmAddCopy, onDismiss = viewModel::dismissDuplicate)
    }
}
