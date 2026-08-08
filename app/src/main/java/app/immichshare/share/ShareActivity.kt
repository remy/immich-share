package app.immichshare.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.immichshare.ui.ImmichShareTheme

/**
 * Share-sheet target for image MIME types.
 *
 * SPEC §3.1: the `content://` grant on an `ACTION_SEND` URI lives only as long
 * as this activity, and cannot be made persistable. Byte staging must therefore
 * start in [onCreate], before the confirm sheet renders — not in the worker.
 *
 * Scaffolding: intent parsing is wired up, staging and the confirm sheet are
 * the next piece of work.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = incomingImageUris(intent)

        setContent {
            ImmichShareTheme {
                ConfirmSheetPlaceholder(uris.size)
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

@Composable
private fun ConfirmSheetPlaceholder(imageCount: Int) {
    Text("$imageCount image(s) received")
}
