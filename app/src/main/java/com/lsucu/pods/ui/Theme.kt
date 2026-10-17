package com.lsucu.pods.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.lsucu.pods.Prefs
import java.util.Locale

/** Токены по макету: тёмно-синий фон, приподнятые карточки с тонкой рамкой, зелёный заряд, синий акцент. */
data class PodsColors(
    val bg: Color,
    val bgTop: Color,
    val card: Color,
    val border: Color,
    val text: Color,
    val text2: Color,
    val track: Color,
    val accent: Color,
    val green: Color,
    val yellow: Color,
    val red: Color,
    val nav: Color,
    val dark: Boolean,
)

val DarkPods = PodsColors(
    bg = Color(0xFF0A0D12),
    bgTop = Color(0xFF141B26),
    card = Color(0xFF151A22),
    border = Color(0xFF232A35),
    text = Color(0xFFF2F4F7),
    text2 = Color(0xFF8D97A6),
    track = Color(0xFF2A313C),
    accent = Color(0xFF4C8DFF),
    green = Color(0xFF30D158),
    yellow = Color(0xFFFFD60A),
    red = Color(0xFFFF453A),
    nav = Color(0xFF10151C),
    dark = true,
)

val LightPods = PodsColors(
    bg = Color(0xFFF2F4F7),
    bgTop = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFE2E6EC),
    text = Color(0xFF0E1116),
    text2 = Color(0xFF6B7380),
    track = Color(0xFFE6E9EE),
    accent = Color(0xFF2F6FEB),
    green = Color(0xFF24A148),
    yellow = Color(0xFFE0A800),
    red = Color(0xFFE5372C),
    nav = Color(0xFFFFFFFF),
    dark = false,
)

val LocalPods = staticCompositionLocalOf { DarkPods }

@Composable
fun PodsTheme(dark: Boolean, content: @Composable () -> Unit) {
    val c = if (dark) DarkPods else LightPods
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent,
        onPrimary = Color.White,
        background = c.bg,
        onBackground = c.text,
        surface = c.card,
        onSurface = c.text,
        onSurfaceVariant = c.text2,
        surfaceVariant = c.track,
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        surfaceContainerHighest = c.track,
        outline = c.border,
        outlineVariant = c.border,
    )
    CompositionLocalProvider(LocalPods provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Язык из настроек ("" = как в системе). Оборачиваем контекст Activity и сервиса. */
object Lang {
    val options = listOf("", "en", "ru")

    fun wrap(base: Context): Context {
        val tag = runCatching { Prefs.lang }.getOrDefault("")
        if (tag.isEmpty()) return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(cfg)
    }
}
