package com.guturner.longbox.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.presentation.CreatorViewModel
import com.guturner.longbox.presentation.Loadable
import com.guturner.longbox.presentation.StoryArcViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.MetronAttribution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorScreen(
    viewModel: CreatorViewModel,
    onBack: () -> Unit,
    onShowAsWriter: () -> Unit,
    onShowAsArtist: () -> Unit,
) {
    val creator by viewModel.creator.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((creator as? Loadable.Ready)?.value?.name ?: "Creator") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        when (val c = creator) {
            Loadable.Loading -> CenteredMessage("Loading…", Modifier.padding(padding))
            is Loadable.Failed -> CenteredMessage(c.message.text, Modifier.padding(padding))
            is Loadable.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    c.value.imageUrl?.let { CoverImage(it, c.value.name, Modifier.width(120.dp)) }
                    Column {
                        Text(c.value.name, style = MaterialTheme.typography.headlineSmall)
                        c.value.birth?.let { Text("Born $it", style = MaterialTheme.typography.labelMedium) }
                        c.value.death?.let { Text("Died $it", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Text(c.value.bio ?: "No biography yet.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShowAsWriter) { Text("My books they wrote") }
                    OutlinedButton(onClick = onShowAsArtist) { Text("…drew") }
                }
                MetronAttribution()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryArcScreen(
    viewModel: StoryArcViewModel,
    onBack: () -> Unit,
    onShowInLibrary: () -> Unit,
) {
    val arc by viewModel.arc.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((arc as? Loadable.Ready)?.value?.name ?: "Event") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        when (val a = arc) {
            Loadable.Loading -> CenteredMessage("Loading…", Modifier.padding(padding))
            is Loadable.Failed -> CenteredMessage(a.message.text, Modifier.padding(padding))
            is Loadable.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(a.value.name, style = MaterialTheme.typography.headlineSmall)
                Text(a.value.description ?: "No description yet.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onShowInLibrary) { Text("Show my issues in this event") }
                MetronAttribution()
            }
        }
    }
}
