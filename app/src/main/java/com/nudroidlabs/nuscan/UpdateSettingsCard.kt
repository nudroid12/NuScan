package com.nudroidlabs.nuscan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nudroidlabs.nuscan.update.InstallPreparation
import com.nudroidlabs.nuscan.update.UpdateCheckResult
import com.nudroidlabs.nuscan.update.UpdateInfo
import com.nudroidlabs.nuscan.update.UpdateManager
import java.io.File
import kotlinx.coroutines.launch

private sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val info: UpdateInfo, val progress: Int) : UpdateUiState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState
    data class Message(val text: String) : UpdateUiState
}

@Composable
fun UpdateSettingsCard() {
    val context = LocalContext.current
    val manager = remember { UpdateManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(AppPreferences.isAutoUpdateCheckEnabled(context)) }
    var state: UpdateUiState by remember { mutableStateOf(UpdateUiState.Idle) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingFile?.let { file ->
            when (val result = manager.prepareInstall(file)) {
                InstallPreparation.Started -> state = UpdateUiState.Message("Android installer opened. Tap Update to continue.")
                is InstallPreparation.Error -> state = UpdateUiState.Message(result.message)
                is InstallPreparation.NeedsPermission -> state = UpdateUiState.Message(
                    "Install permission is still disabled. Enable it in Android settings and tap Install update again."
                )
            }
        }
    }

    fun startInstall(file: File) {
        when (val result = manager.prepareInstall(file)) {
            InstallPreparation.Started -> state = UpdateUiState.Message("Android installer opened. Tap Update to continue.")
            is InstallPreparation.Error -> state = UpdateUiState.Message(result.message)
            is InstallPreparation.NeedsPermission -> {
                pendingFile = file
                state = UpdateUiState.Message("Android needs permission for NuScan to install downloaded updates.")
                permissionLauncher.launch(result.intent)
            }
        }
    }

    fun checkNow() {
        state = UpdateUiState.Checking
        scope.launch {
            state = when (val result = manager.checkForUpdate()) {
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.info)
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Error -> UpdateUiState.Message(result.message)
            }
        }
    }

    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("App updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Current version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { checkNow() },
                    enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading
                ) {
                    Text("Check")
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Check automatically", fontWeight = FontWeight.Medium)
                    Text(
                        "Check GitHub when NuScan opens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoCheck,
                    onCheckedChange = { enabled ->
                        autoCheck = enabled
                        AppPreferences.setAutoUpdateCheckEnabled(context, enabled)
                    }
                )
            }

            when (val current = state) {
                UpdateUiState.Idle -> Text(
                    "Updates come from the official NuScan GitHub release and are verified before installation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                UpdateUiState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Checking for updates…")
                }
                UpdateUiState.UpToDate -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("NuScan is up to date.")
                }
                is UpdateUiState.Available -> {
                    Text("NuScan ${current.info.versionName} is available", fontWeight = FontWeight.SemiBold)
                    if (current.info.changelog.isNotBlank()) {
                        Text(current.info.changelog, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            state = UpdateUiState.Downloading(current.info, 0)
                            scope.launch {
                                val result = manager.download(current.info) { progress ->
                                    scope.launch { state = UpdateUiState.Downloading(current.info, progress) }
                                }
                                state = result.fold(
                                    onSuccess = { file -> UpdateUiState.Ready(current.info, file) },
                                    onFailure = { error -> UpdateUiState.Message(error.message ?: "Update download failed") }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download update")
                    }
                }
                is UpdateUiState.Downloading -> {
                    Text("Downloading NuScan ${current.info.versionName}… ${current.progress}%")
                    LinearProgressIndicator(
                        progress = { current.progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateUiState.Ready -> {
                    Text("Update verified and ready to install.", fontWeight = FontWeight.Medium)
                    Button(
                        onClick = { startInstall(current.file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install update")
                    }
                }
                is UpdateUiState.Message -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(current.text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
