package io.github.lucf15.localai.runner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lucf15.localai.design.*
import io.github.lucf15.localai.runner.service.AccessScreen
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import kotlinx.serialization.Serializable
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installLocalAiSplash()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LocalAiTheme { ManagerApp() } }
    }
}

@Serializable private data object ModelsDestination : NavKey
@Serializable private data object AccessDestination : NavKey

@Composable
private fun ManagerApp() {
    val backStack = rememberNavBackStack(ModelsDestination)
    NavDisplay(backStack = backStack, onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<ModelsDestination> { RunnerScreen(onAccess = { backStack.add(AccessDestination) }) }
            entry<AccessDestination> { AccessScreen(onBack = { backStack.removeLastOrNull() }) }
        })
}

@Composable
private fun RunnerScreen(onAccess: () -> Unit, vm: RunnerViewModel = viewModel()) {
    val state = vm.state
    val owner = LocalLifecycleOwner.current
    var installation by remember { mutableStateOf<ModelUiState?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(vm, owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.observe() } }
    AppScreen(onAccess = onAccess) { padding ->
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().consumeWindowInsets(padding)) {
            item { LabTitle("Local AI") }
            item { LabIntro("Models, shared across apps.", "Download and manage models. Qwen3 currently powers text tools in connected apps.") }
            if (state.models.any { it.installed }) {
                item { AppSection("On this device") }
                items(state.models.filter { it.installed }, key = { it.id }) { model ->
                    ModelCard(model, onDownload = { installation = model }, onPause = { vm.pause(model.id) })
                }
            }
            if (state.models.any { !it.installed }) {
                item { AppSection("Model catalog", "Download sizes shown below. Models stay in app storage.") }
                items(state.models.filter { !it.installed }, key = { it.id }) { model ->
                    ModelCard(model, onDownload = { installation = model }, onPause = { vm.pause(model.id) })
                }
            }
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Wi-Fi downloads", style = MaterialTheme.typography.titleSmall)
                            Text("Wait for an unmetered connection", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(state.wifiOnly, vm::setWifiOnly)
                    }
                }
            }
            item { Text("Text tools are experimental. Review generated text before using it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
    installation?.let { model ->
        AlertDialog(onDismissRequest = { installation = null }, title = { Text("Install ${model.title}?") },
            text = { Text("Download ${formatBytes(model.downloadBytes)} from Hugging Face. Integrity is verified before the model becomes available.") },
            confirmButton = { TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.install(model.id); installation = null
            }) { Text("Download") } }, dismissButton = { TextButton(onClick = { installation = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ModelCard(state: ModelUiState, onDownload: () -> Unit, onPause: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleLarge)
                Text(formatBytes(state.downloadBytes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.installed) InfoTag("Installed")
        }
        if (state.installed) {
            if (state.id == io.github.lucf15.localai.runner.service.LocalAiService.MODEL_ID) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoTag("Summarize"); InfoTag("Rewrite"); InfoTag("Proofread")
                }
            }
            Text(if (state.id == "qwen3-06b-int4") "Available to connected apps · experimental" else "Downloaded for testing · not used by connected apps yet",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        } else if (state.active) {
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
            Text(state.phase.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
            AppButton("Pause download", primary = false, onClick = onPause)
        } else {
            if (state.id != io.github.lucf15.localai.runner.service.LocalAiService.MODEL_ID) {
                Text("Research candidate · not used by connected apps yet", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppButton(if (state.phase == "paused" || state.phase == "failed") "Resume download" else "Download model",
                onClick = onDownload)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun formatBytes(bytes: Long): String = if (bytes >= 1_000_000_000) String.format(Locale.ROOT, "%.2f GB", bytes / 1_000_000_000.0) else String.format(Locale.ROOT, "%.0f MB", bytes / 1_000_000.0)
