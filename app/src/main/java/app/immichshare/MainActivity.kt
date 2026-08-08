package app.immichshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import app.immichshare.share.sweepStaleBatches
import app.immichshare.ui.ImmichShareTheme
import app.immichshare.ui.MainScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SPEC §3.1: a crash mid-share would otherwise leak staged bytes into
        // the cache forever. Skips anything WorkManager still has queued.
        lifecycleScope.launch {
            sweepStaleBatches(now = System.currentTimeMillis())
        }

        enableEdgeToEdge()
        setContent {
            ImmichShareTheme {
                val model: MainViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
                    MainScreen(
                        state = state,
                        onHostChange = model::onHostChange,
                        onApiKeyChange = model::onApiKeyChange,
                        onSaveAndTest = model::saveAndTest,
                        modifier = Modifier.padding(insets),
                    )
                }
            }
        }
    }
}
