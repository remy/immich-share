package app.immichshare.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

/**
 * Thumbnail for the confirm sheet.
 *
 * Points at the *staged* file, and decodes for display only — this never
 * touches the upload path, which streams the original bytes untouched.
 */
@Composable
fun Thumbnail(
    staged: File,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = staged,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}
