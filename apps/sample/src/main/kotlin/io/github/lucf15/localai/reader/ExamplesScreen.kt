package io.github.lucf15.localai.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.lucf15.localai.design.*
import io.github.lucf15.localai.sdk.LocalAiClient

@Composable
internal fun ExamplesScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val client = remember(context) { LocalAiClient(context) }
    AppScreen(onAccess = { context.startActivity(client.accessSettingsIntent()) }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            LabTitle("Sample")
            LabIntro("Choose a text task.", "Three examples, one model in Local AI. Start with a draft, edit it and run a real request.")
            examples.forEachIndexed { index, item ->
                ScenarioCard("0${index + 1}", item.title, item.description, item.source, onClick = { onOpen(item.id) })
            }
            Text("Sample sends the text. Local AI runs the model.\nNo separate model download is needed in this app.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}
