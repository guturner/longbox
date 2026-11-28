package com.guturner.longbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.guturner.longbox.ui.LocalPlatform
import com.guturner.longbox.ui.LongboxRainbow
import com.guturner.longbox.ui.rainbowFor
import longbox.shared.ui.generated.resources.Res
import longbox.shared.ui.generated.resources.longbox_logo
import org.jetbrains.compose.resources.painterResource

/** Comic covers are close to 2:3 (6.625" x 10.25" trim). */
const val COVER_ASPECT = 0.66f

/**
 * Cover size preference used by all three main screens.
 * [gridMinDp] is the adaptive-grid minimum column width; [listWidthDp] is the fixed
 * image width in list layouts (New Releases, Pull List).
 */
internal enum class CoverSize(val gridMinDp: Int, val listWidthDp: Int, val label: String) {
    SMALL(132, 48, "Small"),
    MEDIUM(160, 72, "Medium"),
    LARGE(300, 128, "Large"),
}

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
fun PendingCover(label: String, colorKey: Int = 0, modifier: Modifier = Modifier) {
    val accent = rainbowFor(colorKey)
    Box(
        modifier
            .aspectRatio(COVER_ASPECT)
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.18f))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center)
    }
}

/** App logo as a tappable home button — clicking navigates back to the library root. */
@Composable
fun HomeIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Image(
            painter = painterResource(Res.drawable.longbox_logo),
            contentDescription = "Home",
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * Horizontal 7-stripe rainbow bar using the Longbox palette.
 * Place below any TopAppBar for the consistent 80s Digital Collector header accent.
 */
@Composable
fun RainbowStripe(height: Dp = 6.dp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(height)) {
        LongboxRainbow.forEach { color ->
            Box(Modifier.weight(1f).fillMaxSize().background(color))
        }
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
