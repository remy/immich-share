package app.immichshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.immichshare.ui.ImmichShareTheme

/**
 * Launcher, settings and first-run onboarding.
 *
 * Scaffolding: the settings form, connection test and permission cards from
 * SPEC §7 land here next.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImmichShareTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    MainScreen(Modifier.padding(insets))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp)) {
        Text(
            text = stringResourceAppName(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Share photos to Immich from any app's share sheet.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun stringResourceAppName(): String =
    androidx.compose.ui.res.stringResource(R.string.app_name)

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    ImmichShareTheme { MainScreen() }
}
