package io.github.lucf15.localai.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Light = lightColorScheme(
    primary = Color(0xFF335CC5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBFF), onPrimaryContainer = Color(0xFF1C3265),
    secondary = Color(0xFF815129), secondaryContainer = Color(0xFFFFE4CC), onSecondaryContainer = Color(0xFF4B280A),
    tertiary = Color(0xFF006B58), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD0EBE2), onTertiaryContainer = Color(0xFF0C3C31),
    background = Color(0xFFF6F8FC), onBackground = Color(0xFF182230),
    surface = Color.White, onSurface = Color(0xFF182230), onSurfaceVariant = Color(0xFF586578),
    outline = Color(0xFF758299), outlineVariant = Color(0xFFD8DFEA),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAFD),
    surfaceContainer = Color(0xFFF0F3F9), surfaceContainerHigh = Color(0xFFEAF0F8), surfaceContainerHighest = Color(0xFFE4EAF3),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA8C8FF), onPrimary = Color(0xFF10315D),
    primaryContainer = Color(0xFF233E64), onPrimaryContainer = Color(0xFFE3EBFF),
    secondary = Color(0xFFF4BF96), secondaryContainer = Color(0xFF614124), onSecondaryContainer = Color(0xFFFFE4CC),
    tertiary = Color(0xFF85D6BB), onTertiary = Color(0xFF0C3C31),
    tertiaryContainer = Color(0xFF214F42), onTertiaryContainer = Color(0xFFD0EBE2),
    background = Color(0xFF101318), onBackground = Color(0xFFEEF1F6),
    surface = Color(0xFF1B2028), onSurface = Color(0xFFEEF1F6), onSurfaceVariant = Color(0xFFAFB7C4),
    outline = Color(0xFF808B9C), outlineVariant = Color(0xFF39414F),
    surfaceContainerLowest = Color(0xFF0B0E13), surfaceContainerLow = Color(0xFF171C24),
    surfaceContainer = Color(0xFF1C222C), surfaceContainerHigh = Color(0xFF242C38), surfaceContainerHighest = Color(0xFF2D3745),
)
@OptIn(ExperimentalTextApi::class)
private val Inter = FontFamily(
    Font(R.font.inter, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
private val Type = Typography().let { base -> base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = Inter),
    displayMedium = base.displayMedium.copy(fontFamily = Inter),
    displaySmall = base.displaySmall.copy(fontFamily = Inter),
    headlineLarge = base.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-.4).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = base.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-.4).sp),
    titleMedium = base.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = base.titleSmall.copy(fontFamily = Inter),
    bodyLarge = base.bodyLarge.copy(fontFamily = Inter, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Inter, fontSize = 13.sp, lineHeight = 17.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
) }

/** Shared Material3 color and type tokens, with matching native launch-window colors. */
@Composable
fun LocalAiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        view.context.activity()?.let { activity ->
            activity.window.decorView.setBackgroundColor(scheme.background.toArgb())
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
