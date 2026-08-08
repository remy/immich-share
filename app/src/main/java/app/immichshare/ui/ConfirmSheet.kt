package app.immichshare.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import app.immichshare.R
import app.immichshare.data.AlbumResponse
import app.immichshare.data.TagResponse
import app.immichshare.share.StagedAsset
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun ConfirmSheet(
    staging: Boolean,
    assets: List<StagedAsset>,
    albums: List<AlbumResponse>,
    tags: List<TagResponse>,
    pickersLoading: Boolean,
    albumSelection: AlbumSelection,
    selectedTags: Set<String>,
    mediaLocationGranted: Boolean,
    onAlbumSelected: (AlbumSelection) -> Unit,
    onTagToggled: (String) -> Unit,
    onTagAdded: (String) -> Unit,
    onGrantMediaLocation: () -> Unit,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (staging) {
                stringRes(R.string.sheet_preparing)
            } else {
                pluralRes(R.plurals.sheet_title, assets.size, assets.size)
            },
            style = MaterialTheme.typography.titleLarge,
        )

        Thumbnails(assets)

        if (!staging && assets.isNotEmpty()) {
            MetadataSummary(assets, mediaLocationGranted, onGrantMediaLocation)
            AlbumPicker(albums, pickersLoading, albumSelection, onAlbumSelected)
            TagPicker(tags, selectedTags, onTagToggled, onTagAdded)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringRes(R.string.action_cancel))
            }
            Button(
                onClick = onUpload,
                enabled = !staging && assets.isNotEmpty(),
                modifier = Modifier.weight(2f),
            ) {
                Text(stringRes(R.string.action_upload))
            }
        }
    }
}

@Composable
private fun Thumbnails(assets: List<StagedAsset>) {
    if (assets.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        assets.forEach { asset ->
            // Decodes for display only — this never touches the upload path,
            // which streams the original bytes untouched.
            AsyncImage(
                model = File(asset.path),
                contentDescription = asset.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

/**
 * SPEC §3.5: makes retention visible. GPS redaction depends on the *sharing*
 * app as much as on this one, so an invisible failure becomes a visible one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataSummary(
    assets: List<StagedAsset>,
    mediaLocationGranted: Boolean,
    onGrantMediaLocation: () -> Unit,
) {
    val withDate = assets.count { it.metadata.hasDate }
    val withGps = assets.count { it.metadata.hasGps }
    val withCamera = assets.count { it.metadata.hasCamera }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(stringRes(R.string.meta_date), withDate, assets.size)
            MetaChip(stringRes(R.string.meta_gps), withGps, assets.size)
            MetaChip(stringRes(R.string.meta_camera), withCamera, assets.size)
        }

        // The actionable case: no photo has GPS and we know why.
        if (withGps == 0 && !mediaLocationGranted) {
            Text(
                text = stringRes(R.string.meta_gps_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onGrantMediaLocation) {
                Text(stringRes(R.string.permissions_grant_media))
            }
        }
    }
}

@Composable
private fun MetaChip(label: String, present: Int, total: Int) {
    val complete = present == total && total > 0
    AssistChip(
        onClick = {},
        enabled = present > 0,
        label = {
            Text(if (complete) label else "$label $present/$total")
        },
        leadingIcon = {
            Icon(
                imageVector = if (present > 0) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/** Either an existing album, a name to create, or nothing. */
sealed interface AlbumSelection {
    data object None : AlbumSelection
    data class Existing(val id: String, val name: String) : AlbumSelection
    data class New(val name: String) : AlbumSelection
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumPicker(
    albums: List<AlbumResponse>,
    loading: Boolean,
    selection: AlbumSelection,
    onSelected: (AlbumSelection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val typed = when (selection) {
        is AlbumSelection.Existing -> selection.name
        is AlbumSelection.New -> selection.name
        AlbumSelection.None -> ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { text ->
                    // Typing a name that doesn't exist means "create it".
                    val match = albums.firstOrNull { it.albumName.equals(text, ignoreCase = true) }
                    onSelected(
                        when {
                            text.isBlank() -> AlbumSelection.None
                            match != null -> AlbumSelection.Existing(match.id, match.albumName)
                            else -> AlbumSelection.New(text)
                        }
                    )
                    expanded = true
                },
                label = { Text(stringRes(R.string.sheet_album)) },
                placeholder = { Text(stringRes(R.string.sheet_album_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )

            val matches = albums.filter {
                typed.isBlank() || it.albumName.contains(typed, ignoreCase = true)
            }

            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.sheet_album_none)) },
                    onClick = {
                        onSelected(AlbumSelection.None)
                        expanded = false
                    },
                )
                matches.take(30).forEach { album ->
                    DropdownMenuItem(
                        text = { Text(album.albumName) },
                        onClick = {
                            onSelected(AlbumSelection.Existing(album.id, album.albumName))
                            expanded = false
                        },
                    )
                }
            }
        }

        if (selection is AlbumSelection.New) {
            Text(
                text = stringRes(R.string.sheet_album_create, selection.name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPicker(
    tags: List<TagResponse>,
    selected: Set<String>,
    onToggled: (String) -> Unit,
    onAdded: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(R.string.sheet_tags), style = MaterialTheme.typography.titleSmall)

        // Selected tags first, so a newly typed one is visible immediately even
        // if the server list never loaded.
        val known = tags.map { it.value }
        val options = (selected.toList() + known).distinct()

        if (options.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.take(40).forEach { value ->
                    FilterChip(
                        selected = value in selected,
                        onClick = { onToggled(value) },
                        label = { Text(value) },
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringRes(R.string.sheet_tag_new)) },
                supportingText = { Text(stringRes(R.string.sheet_tag_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAdded(draft.trim())
                        draft = ""
                    }
                },
                modifier = Modifier.width(80.dp),
            ) {
                Text(stringRes(R.string.action_add))
            }
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)

@Composable
private fun pluralRes(id: Int, count: Int, vararg args: Any): String =
    androidx.compose.ui.res.pluralStringResource(id, count, *args)
