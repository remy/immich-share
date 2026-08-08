package app.immichshare.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import app.immichshare.BuildConfig
import app.immichshare.MainUiState
import app.immichshare.R
import app.immichshare.data.ConnectionResult

@Composable
fun MainScreen(
    state: MainUiState,
    onHostChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveAndTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringRes(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringRes(R.string.settings_intro),
            style = MaterialTheme.typography.bodyMedium,
        )

        ServerCard(state, onHostChange, onApiKeyChange, onSaveAndTest)
        PermissionCard()
        HelpCard()

        // Sideloaded builds are otherwise indistinguishable on the phone, which
        // makes "did the update actually install?" impossible to answer.
        Text(
            text = stringRes(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServerCard(
    state: MainUiState,
    onHostChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSaveAndTest: () -> Unit,
) {
    var revealKey by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(R.string.settings_server),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.host,
                onValueChange = onHostChange,
                label = { Text(stringRes(R.string.settings_host_label)) },
                placeholder = { Text(stringRes(R.string.settings_host_hint)) },
                supportingText = { Text(stringRes(R.string.settings_host_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringRes(R.string.settings_key_label)) },
                singleLine = true,
                visualTransformation = if (revealKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringRes(
                                if (revealKey) R.string.settings_key_hide else R.string.settings_key_show
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSaveAndTest,
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringRes(R.string.settings_test))
            }

            state.result?.let { ConnectionResultRow(it) }
        }
    }
}

@Composable
private fun ConnectionResultRow(result: ConnectionResult) {
    val (icon, text, good) = when (result) {
        is ConnectionResult.Success ->
            Triple(Icons.Filled.CheckCircle, stringRes(R.string.result_connected, result.email), true)

        ConnectionResult.BadKey ->
            Triple(Icons.Filled.Error, stringRes(R.string.result_bad_key), false)

        is ConnectionResult.ServerError ->
            Triple(Icons.Filled.Error, stringRes(R.string.result_server_error, result.code), false)

        is ConnectionResult.Unreachable ->
            Triple(Icons.Filled.Error, stringRes(R.string.result_unreachable, result.detail), false)
    }

    StatusRow(icon = icon, text = text, good = good)
}

@Composable
private fun StatusRow(icon: ImageVector, text: String, good: Boolean) {
    val colour = if (good) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colour)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Without `ACCESS_MEDIA_LOCATION` every uploaded photo silently loses its GPS,
 * so this card explains *why* rather than just asking.
 */
@Composable
private fun PermissionCard() {
    val context = LocalContext.current
    var mediaGranted by remember { mutableStateOf(context.hasMediaLocationAccess()) }
    var notifyGranted by remember { mutableStateOf(context.hasNotificationAccess()) }

    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        mediaGranted = context.hasMediaLocationAccess()
    }
    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        notifyGranted = context.hasNotificationAccess()
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (mediaGranted) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(R.string.permissions_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringRes(R.string.permissions_media_why),
                style = MaterialTheme.typography.bodyMedium,
            )

            StatusRow(
                icon = if (mediaGranted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                text = stringRes(
                    if (mediaGranted) R.string.permissions_media_ok else R.string.permissions_media_missing
                ),
                good = mediaGranted,
            )
            if (!mediaGranted) {
                Button(
                    onClick = { mediaLauncher.launch(requiredMediaPermissions().toTypedArray()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringRes(R.string.permissions_grant_media))
                }
            }

            if (notificationPermission().isNotEmpty()) {
                StatusRow(
                    icon = if (notifyGranted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    text = stringRes(
                        if (notifyGranted) R.string.permissions_notify_ok else R.string.permissions_notify_missing
                    ),
                    good = notifyGranted,
                )
                if (!notifyGranted) {
                    OutlinedButton(
                        onClick = { notifyLauncher.launch(notificationPermission().toTypedArray()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringRes(R.string.permissions_grant_notify))
                    }
                }
            }

            OutlinedButton(
                onClick = { context.openAppSettings() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.permissions_open_settings))
            }
        }
    }
}

@Composable
private fun HelpCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(R.string.help_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringRes(R.string.help_api_key),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringRes(R.string.help_sharing),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

@Composable
private fun stringRes(id: Int, vararg args: Any): String =
    androidx.compose.ui.res.stringResource(id, *args)
