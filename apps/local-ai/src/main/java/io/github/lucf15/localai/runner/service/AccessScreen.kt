package io.github.lucf15.localai.runner.service

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lucf15.localai.design.*
import io.github.lucf15.localai.design.R
import kotlinx.coroutines.*

@Immutable internal data class AllowedApp(val packageName: String, val label: String)
@Stable internal interface AccessState { val apps: List<AllowedApp>; val loaded: Boolean }
@Stable private class MutableAccessState : AccessState {
    override var apps by mutableStateOf<List<AllowedApp>>(emptyList())
    override var loaded by mutableStateOf(false)
}
internal class AccessViewModel(app: Application) : AndroidViewModel(app) {
    private val mutable = MutableAccessState()
    val state: AccessState = mutable
    private var refreshJob: Job? = null
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                pm.getPackagesHoldingPermissions(arrayOf(ClientGrants.PERMISSION), 0)
                    .filter { pm.checkPermission(ClientGrants.PERMISSION, it.packageName) == PackageManager.PERMISSION_GRANTED }
                    .map { AllowedApp(it.packageName, it.applicationInfo?.let(pm::getApplicationLabel)?.toString() ?: it.packageName) }
                    .sortedBy { it.label }
            }
            Snapshot.withMutableSnapshot { mutable.apps = apps; mutable.loaded = true }
        }
    }
}

/** Android owns grants; this destination only reports them and opens system settings. */
@Composable
internal fun AccessScreen(onBack: () -> Unit, vm: AccessViewModel = viewModel()) {
    val context = LocalContext.current
    val state = vm.state
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    AppScreen(onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LabTitle("App permissions", hasBack = true)
            Text("Choose which apps can use local AI in Android settings.", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppCard {
                AppSection("Allowed apps")
                if (!state.loaded) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else if (state.apps.isEmpty()) Text("No apps have access yet. A client asks for permission the first time it runs a text tool.")
                state.apps.forEach { app ->
                    ListItem(headlineContent = { Text(app.label) }, supportingContent = { Text("Manage access in Android settings") },
                        trailingContent = { Icon(painterResource(R.drawable.ic_arrow_forward), null) },
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                        })
                }
            }
            Text("Revoking access does not remove downloaded models.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
