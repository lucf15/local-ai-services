package io.github.lucf15.localai.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.lucf15.localai.design.*
import io.github.lucf15.localai.sdk.*

@Composable
internal fun ExampleScreen(exampleId: String, vm: ExampleViewModel, onBack: () -> Unit) {
    val example = examples.first { it.id == exampleId }
    val state = vm.state
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::accessResult)
    AppScreen(
        onAccess = { context.startActivity(vm.client.accessSettingsIntent()) },
        onBack = { focus.clearFocus(); onBack() }, backLabel = "Examples",
        bottomActions = {
            ProcessingDock(state.phase, state.busy,
                state.source.isNotBlank() && state.source.toByteArray(Charsets.UTF_8).size <= 1024,
                when (state.capability) { Capability.SUMMARIZE -> "Summarize article"; Capability.REWRITE -> "Improve email"; Capability.PROOFREAD -> "Check writing" },
                onRun = {
                    focus.clearFocus()
                    if (vm.client.hasAccess()) vm.run()
                    else { vm.preparePermissionRun(); permission.launch(LocalAiClient.PERMISSION) }
                }, onCancel = vm::cancel)
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LabTitle(when (state.capability) {
                Capability.SUMMARIZE -> "Article summary"; Capability.REWRITE -> "Email assistant"; Capability.PROOFREAD -> "Writing check"
            }, hasBack = true)
            Text(example.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextArea(state.source, vm::source, !state.busy)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppSection("Result language")
                AppSegments(listOf("Italian", "English"), Language.entries.indexOf(state.language), !state.busy) {
                    vm.language(Language.entries[it])
                }
            }
            if (state.output.isNotEmpty()) AppCard {
                    Text("Result", style = MaterialTheme.typography.titleMedium)
                    SelectionContainer { Text(state.output, style = MaterialTheme.typography.bodyLarge) }
            }
            AppCard {
                Text("WHAT HAPPENED", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!state.connected) Text(if (state.busy) "Connecting to Local AI…" else "Run the example to send a text request.", style = MaterialTheme.typography.bodyMedium)
                if (state.connected) StepRow("01", "Connected to Local AI", "Capability: ${state.capability.id}")
                if (state.output.isNotEmpty()) StepRow("02", "Received generated text", "The result came from the on-device model.")
                if (state.completed) StepRow("03", "Request completed", "Review the result before using it.")
            }
            Text("Experimental · review the result before use.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}
