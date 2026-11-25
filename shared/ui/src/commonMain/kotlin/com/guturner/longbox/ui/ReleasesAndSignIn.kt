package com.guturner.longbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.presentation.Loadable
import com.guturner.longbox.presentation.ReleasesViewModel
import com.guturner.longbox.presentation.SessionViewModel
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.CoverSize
import com.guturner.longbox.ui.components.HomeIconButton
import com.guturner.longbox.ui.components.MetronAttribution
import kotlinx.coroutines.launch
import longbox.shared.ui.generated.resources.Res
import longbox.shared.ui.generated.resources.longbox_logo
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReleasesScreen(viewModel: ReleasesViewModel, onGoHome: () -> Unit, coverSize: CoverSize, onCoverSizeChange: (CoverSize) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owned by viewModel.ownedIds.collectAsStateWithLifecycle()
    var sizeMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Releases · ${state.week.wednesday}") },
                navigationIcon = { HomeIconButton(onGoHome) },
                actions = {
                    TextButton(onClick = viewModel::previousWeek) { Text("‹ Prev") }
                    TextButton(onClick = viewModel::nextWeek) { Text("Next ›") }
                    TextButton(onClick = { sizeMenuOpen = true }) { Text("Size") }
                    DropdownMenu(expanded = sizeMenuOpen, onDismissRequest = { sizeMenuOpen = false }) {
                        CoverSize.entries.forEach { size ->
                            DropdownMenuItem(
                                text = { Text("Covers: ${size.label}") },
                                trailingIcon = if (coverSize == size) { { Text("✓") } } else null,
                                onClick = { sizeMenuOpen = false; onCoverSizeChange(size) },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.availablePublishers.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.availablePublishers.forEach { publisher ->
                        FilterChip(
                            selected = publisher in state.enabledPublishers,
                            onClick = { viewModel.togglePublisher(publisher) },
                            label = { Text(publisher) },
                        )
                    }
                }
            }

            when (val comics = state.comics) {
                Loadable.Loading -> CenteredMessage("Loading…", Modifier.weight(1f))
                is Loadable.Failed -> CenteredMessage(
                    comics.message.text + "\nNew releases need the home server.", Modifier.weight(1f),
                )
                is Loadable.Ready -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(comics.value, key = { it.id.value }) { comic ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoverImage(comic.coverA.imageUrl, comic.displayTitle, Modifier.width(coverSize.listWidthDp.dp))
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
        Image(
            painter = painterResource(Res.drawable.longbox_logo),
            contentDescription = "Longbox",
            modifier = Modifier.size(160.dp),
        )
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
