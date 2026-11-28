package com.guturner.longbox.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.ui.LocalPlatform
import com.guturner.longbox.ui.components.PendingCover
import kotlin.math.abs

private const val PEEK_HEIGHT_DP = 56
private const val PEEK_COVER_WIDTH_DP = 44

/**
 * Immersive "flipping through a physical box" view.
 *
 * Shows a large floating cover card that the user swipes left/right to browse.
 * Adjacent pages scale down and fade to give depth. Below the pager a peek strip
 * shows the tops of all covers in order (like comics standing up in a box), with the
 * current page highlighted and the strip auto-scrolling to keep it visible.
 *
 * Long-pressing the main card triggers [onMoveEntry] (move-to-container sheet).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoxBrowseContent(
    entries: List<CollectionEntry>,
    onOpenComic: (ComicId) -> Unit,
    onMoveEntry: (EntryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { entries.size })
    val current = pagerState.currentPage
    val peekListState = rememberLazyListState()

    LaunchedEffect(current) {
        peekListState.animateScrollToItem(current.coerceAtLeast(0))
    }

    Column(modifier.fillMaxSize()) {
        // ── Main pager ─────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 2,
        ) { page ->
            val entry = entries[page]
            // Distance of this page from the current viewport center [0, 1].
            val offset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, 1f)
            val scale = 1f - 0.08f * offset
            val alpha = 1f - 0.35f * offset
            val colorKey = entry.id.value.hashCode()

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .combinedClickable(
                        onClick = { entry.comic?.let { onOpenComic(it.id) } },
                        onLongClick = { onMoveEntry(entry.id) },
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            ) {
                Box(Modifier.fillMaxSize()) {
                    val comic = entry.comic
                    when {
                        comic != null -> {
                            val platform = LocalPlatform.current
                            AsyncImage(
                                model = comic.coverA.imageUrl?.let { platform.imageUrl(it) },
                                contentDescription = comic.displayTitle,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Title overlay at bottom
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    comic.displayTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        entry.isQueuedOnDevice -> PendingCover("Saved offline\n${entry.scannedCode}", colorKey)
                        entry.state == EntryState.PENDING -> PendingCover("Looking up…", colorKey)
                        else -> PendingCover("Not in catalog", colorKey)
                    }
                }
            }
        }

        // ── Page counter ───────────────────────────────────────────────────────
        Text(
            "${current + 1} of ${entries.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 4.dp),
        )

        // ── Peek strip — tops of all covers, like comics standing in a box ─────
        if (entries.size > 1) {
            LazyRow(
                state = peekListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PEEK_HEIGHT_DP.dp)
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(entries) { index, entry ->
                    val isCurrent = index == current
                    val platform = LocalPlatform.current
                    Box(
                        Modifier
                            .width(PEEK_COVER_WIDTH_DP.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (isCurrent) Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(3.dp),
                                ) else Modifier,
                            ),
                    ) {
                        entry.comic?.coverA?.imageUrl?.let { url ->
                            AsyncImage(
                                model = platform.imageUrl(url),
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
