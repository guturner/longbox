package com.guturner.longbox.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.guturner.longbox.domain.model.Cover
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.Container
import com.guturner.longbox.domain.model.ContainerId
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.CreatorRole
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.PullListEntry
import com.guturner.longbox.domain.model.StoryArcRef
import com.guturner.longbox.presentation.ComicDetailViewModel
import com.guturner.longbox.presentation.Loadable
import com.guturner.longbox.ui.components.CenteredMessage
import com.guturner.longbox.ui.components.CoverImage
import com.guturner.longbox.ui.components.HomeIconButton
import com.guturner.longbox.ui.components.MetronAttribution
import com.guturner.longbox.ui.components.RainbowStripe
import com.guturner.longbox.ui.containers.MoveToContainerSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComicDetailScreen(
    viewModel: ComicDetailViewModel,
    onGoHome: () -> Unit,
    onOpenCreator: (CreatorId) -> Unit,
    onOpenStoryArc: (StoryArcRef) -> Unit,
    onRemoveCopy: (EntryId) -> Unit,
    onAddSeriesToPullList: (PullListEntry.BySeries) -> Unit = {},
    containers: List<Container> = emptyList(),
    onMoveToContainer: (EntryId, ContainerId) -> Unit = { _, _ -> },
    onRemoveFromContainer: (EntryId) -> Unit = {},
) {
    val comic by viewModel.comic.collectAsStateWithLifecycle()
    val copies by viewModel.ownedCopies.collectAsStateWithLifecycle()
    var moveEntry by remember { mutableStateOf<EntryId?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text((comic as? Loadable.Ready)?.value?.displayTitle ?: "") },
                    navigationIcon = { HomeIconButton(onGoHome) },
                )
                RainbowStripe()
            }
        },
    ) { padding ->
        when (val c = comic) {
            Loadable.Loading -> CenteredMessage("Loading…", Modifier.padding(padding))
            is Loadable.Failed -> CenteredMessage(c.message.text, Modifier.padding(padding))
            is Loadable.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoversRow(c.value)
                Header(c.value)

                if (c.value.storyArcs.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        c.value.storyArcs.forEach { arc ->
                            AssistChip(onClick = { onOpenStoryArc(arc) }, label = { Text(arc.name) })
                        }
                    }
                }

                c.value.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

                HorizontalDivider()
                Text("Creators", style = MaterialTheme.typography.titleMedium)
                c.value.credits.sortedBy { credit -> credit.roles.minOfOrNull { it.ordinal } ?: Int.MAX_VALUE }.forEach { credit ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenCreator(credit.creator.id) }.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(credit.creator.name, fontWeight = FontWeight.Medium)
                        Text(credit.roles.joinToString { it.label }, style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (copies.isNotEmpty()) {
                    HorizontalDivider()
                    Text("In your collection", style = MaterialTheme.typography.titleMedium)
                    copies.forEach { copy ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(copy.ownedCoverName ?: "Cover A")
                                copy.containerName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row {
                                TextButton(onClick = { moveEntry = copy.id }) { Text("Move") }
                                TextButton(onClick = { onRemoveCopy(copy.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
                HorizontalDivider()
                TextButton(
                    onClick = {
                        onAddSeriesToPullList(
                            PullListEntry.BySeries(
                                seriesId = c.value.series.id,
                                seriesName = c.value.series.displayName,
                                publisherName = c.value.publisher.name,
                            )
                        )
                    },
                ) { Text("+ Track this series on pull list") }
                MetronAttribution()
            }
        }
    }

    moveEntry?.let { entryId ->
        MoveToContainerSheet(
            entryId = entryId,
            containers = containers,
            onMove = { id, containerId -> onMoveToContainer(id, containerId) },
            onRemoveFromContainer = { id -> onRemoveFromContainer(id) },
            onDismiss = { moveEntry = null },
        )
    }
}

@Composable
private fun CoversRow(comic: Comic) {
    var enlarged by remember { mutableStateOf<Cover?>(null) }

    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        comic.allCovers.forEach { cover ->
            Column(
                Modifier.width(180.dp).clickable { enlarged = cover },
            ) {
                CoverImage(cover.imageUrl, contentDescription = "${comic.displayTitle} ${cover.name}")
                Text(cover.name, style = MaterialTheme.typography.labelMedium, maxLines = 2)
            }
        }
    }

    enlarged?.let { cover ->
        Dialog(
            onDismissRequest = { enlarged = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { enlarged = null },
                contentAlignment = Alignment.Center,
            ) {
                CoverImage(
                    cover.imageUrl,
                    contentDescription = cover.name,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                Text(
                    "Tap to close",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(comic: Comic) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(comic.displayTitle, style = MaterialTheme.typography.headlineSmall)
        comic.title?.let { Text("\u201c$it\u201d", style = MaterialTheme.typography.titleMedium) }
        Text("${comic.publisher.name} · ${comic.series.displayName}", style = MaterialTheme.typography.bodyMedium)
        // Release (in-store) date is what collectors care about; cover date is often months later.
        comic.storeDate?.let { Text("Released $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
        comic.coverDate?.let { Text("Cover date $it", style = MaterialTheme.typography.labelMedium) }
    }
}

private val CreatorRole.label: String
    get() = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
