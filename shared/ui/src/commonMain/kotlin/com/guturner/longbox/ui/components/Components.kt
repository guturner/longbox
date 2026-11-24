package com.guturner.longbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guturner.longbox.ui.LocalPlatform

/** Comic covers are close to 2:3 (6.625" x 10.25" trim). */
const val COVER_ASPECT = 0.66f

@Composable
fun CoverImage(url: String?, contentDescription: String, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    Box(
        modifier
            .aspectRatio(COVER_ASPECT)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = platform.imageUrl(url),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("No cover", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun PendingCover(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(COVER_ASPECT)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

/** Metron's data is CC BY-SA 4.0, which requires attribution wherever it is shown. */
@Composable
fun MetronAttribution(modifier: Modifier = Modifier) {
    Text(
        "Comic data from Metron (metron.cloud), CC BY-SA 4.0",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = modifier,
    )
}
