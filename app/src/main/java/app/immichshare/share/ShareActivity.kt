package app.immichshare.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.immichshare.MainActivity
import app.immichshare.R
import app.immichshare.ui.ConfirmSheet
import app.immichshare.ui.ImmichShareTheme
import app.immichshare.ui.hasMediaLocationAccess
import app.immichshare.ui.requiredMediaPermissions

/**
 * Share-sheet target for images.
 *
 * SPEC §3.1: the `content://` grant on an `ACTION_SEND` URI lives only as long
 * as this activity and cannot be made persistable, so staging starts in
 * [onCreate] — before the confirm sheet renders — and everything downstream
 * works from app-private copies.
 */
class ShareActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = incomingImageUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.error_no_images, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Staging starts here, not on first composition: the read grant lives
        // only as long as this activity, and the user may dismiss the sheet
        // immediately. Everything downstream reads app-private copies.
        viewModel.stage(uris)

        setContent {
            ImmichShareTheme {
                ShareSheetHost(viewModel = viewModel, onClose = { finish() })
            }
        }
    }

    private fun incomingImageUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra(Intent.EXTRA_STREAM)
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            getParcelableExtra(name)
        }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableArrayListExtra(name: String): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(name, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(name).orEmpty()
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheetHost(
    viewModel: ShareViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var mediaGranted by remember { mutableStateOf(context.hasMediaLocationAccess()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaGranted = context.hasMediaLocationAccess()
    }

    LaunchedEffect(state.done) {
        if (state.done) {
            Toast.makeText(context, R.string.toast_queued, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        when (state.configured) {
            false -> NotConfigured(onClose = onClose)
            else -> ConfirmSheet(
                staging = state.staging,
                assets = state.assets,
                albums = state.albums,
                tags = state.tags,
                pickersLoading = state.pickersLoading,
                albumSelection = state.albumSelection,
                selectedTags = state.selectedTags,
                mediaLocationGranted = mediaGranted,
                onAlbumSelected = viewModel::selectAlbum,
                onTagToggled = viewModel::toggleTag,
                onTagAdded = viewModel::addTag,
                onGrantMediaLocation = {
                    permissionLauncher.launch(requiredMediaPermissions().toTypedArray())
                },
                onUpload = viewModel::upload,
                onCancel = {
                    viewModel.discard()
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun NotConfigured(onClose: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.sheet_setup_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.sheet_setup_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.sheet_setup_action))
        }
    }
}
