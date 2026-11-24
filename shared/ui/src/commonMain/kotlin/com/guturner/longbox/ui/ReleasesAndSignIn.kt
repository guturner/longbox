package com.guturner.longbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.presentation.Loadable
import com.guturner.longbox.presentation.ReleasesViewModel
import com.guturner.longbox.presentation.SessionViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.MetronAttribution
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(viewModel: ReleasesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owned by viewModel.ownedIds.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New comics · week of ${state.week.wednesday}") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    TextButton(onClick = viewModel::previousWeek) { Text("‹ Prev") }
                    TextButton(onClick = viewModel::nextWeek) { Text("Next ›") }
                },
            )
        },
    ) { padding ->
        when (val comics = state.comics) {
            Loadable.Loading -> CenteredMessage("Loading…", Modifier.padding(padding))
            is Loadable.Failed -> CenteredMessage(
                comics.message.text + "\nNew releases need the home server.", Modifier.padding(padding),
            )
            is Loadable.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(comics.value, key = { it.id.value }) { comic ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(comic.coverA.imageUrl, comic.displayTitle, Modifier.width(64.dp))
                        Column(Modifier.weight(1f)) {
                            Text(comic.displayTitle, style = MaterialTheme.typography.titleSmall)
                            Text(comic.publisher.name, style = MaterialTheme.typography.labelMedium)
                        }
                        if (comic.id in owned) Text("✓ Owned", color = MaterialTheme.colorScheme.primary)
                    }
                }
                item { MetronAttribution() }
            }
        }
    }
}

@Composable
fun SignInScreen(viewModel: SessionViewModel) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Longbox", style = MaterialTheme.typography.displaySmall)
        Text("Your comic collection, on every screen.")
        Button(onClick = { scope.launch { viewModel.onGoogleIdToken(platform.requestGoogleIdToken()) } }) {
            Text("Sign in with Google")
        }
        message?.let { Text(it.text, color = MaterialTheme.colorScheme.error) }
        Text(
            "First sign-in must happen on your home network (or VPN), where the Longbox server lives.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
