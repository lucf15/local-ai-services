package io.github.lucf15.localai.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** DEX Lab's scrolling page and pinned circular actions, built with Material3 surfaces. */
@Composable
fun AppScreen(
    onAccess: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    bottomActions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Scaffold(containerColor = background, bottomBar = bottomActions) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                content(PaddingValues(top = padding.calculateTopPadding() + 14.dp, bottom = padding.calculateBottomPadding()))
                Box(Modifier.fillMaxWidth().height(padding.calculateTopPadding() + 12.dp)
                    .background(Brush.verticalGradient(listOf(background, background, Color.Transparent))))
                Row(Modifier.fillMaxWidth().padding(top = padding.calculateTopPadding() + 14.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    if (onBack != null) LabIconButton(R.drawable.ic_arrow_back, backLabel, onBack) else Spacer(Modifier.size(48.dp))
                    if (onAccess != null) LabIconButton(R.drawable.ic_permissions, "App permissions", onAccess)
                }
            }
        }
    }
}

@Composable
fun LabIconButton(icon: Int, label: String, onClick: () -> Unit) {
    OutlinedIconButton(onClick, modifier = Modifier.size(48.dp).shadow(3.dp, CircleShape), shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(painterResource(icon), label, Modifier.size(22.dp))
    }
}

@Composable
fun LabTitle(title: String, hasBack: Boolean = false) {
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = if (hasBack) 64.dp else 0.dp, end = 64.dp),
        contentAlignment = Alignment.CenterStart) {
        Text(title, style = if (hasBack) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge)
    }
}

@Composable
fun LabIntro(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AppSection(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 3.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun ScenarioCard(number: String, title: String, description: String, preview: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ic_arrow_forward), null, Modifier.size(20.dp))
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun InfoTag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .08f),
        contentColor = MaterialTheme.colorScheme.primary, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .16f))) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StepRow(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val buttonModifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
    if (primary) Button(onClick, buttonModifier, enabled = enabled) { Text(text) }
    else OutlinedButton(onClick, buttonModifier, enabled = enabled,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Text(text) }
}

@Composable
fun AppSegments(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(selected = selected == index, onClick = { onSelect(index) }, enabled = enabled,
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary, activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant),
                shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label) }
        }
    }
}

@Composable
fun AppTextArea(value: String, onValueChange: (String) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val tooLong = value.toByteArray(Charsets.UTF_8).size > 1024
    OutlinedTextField(value = value, onValueChange = onValueChange,
        readOnly = !enabled, label = { Text("Source text") }, minLines = 4, maxLines = 8,
        textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
        isError = tooLong, supportingText = {
            Text(if (tooLong) "Text is too long. Shorten the passage to continue." else "Edit the example or paste your own text.")
        }, modifier = modifier.fillMaxWidth())
}

/** Fixed action: keyboard covers it, while the editor viewport applies IME padding. */
@Composable
fun ProcessingDock(phase: String, busy: Boolean, enabled: Boolean, label: String, onRun: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val actionModifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                if (busy) OutlinedButton(onCancel, modifier = actionModifier, enabled = !phase.startsWith("Stopping")) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(10.dp))
                    Text(if (phase.startsWith("Stopping")) "Stopping…" else "Cancel processing")
                } else Button(onRun, actionModifier, enabled = enabled) { Text(label) }
            }
        }
    }
}
