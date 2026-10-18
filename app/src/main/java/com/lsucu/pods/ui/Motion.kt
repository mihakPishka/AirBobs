package com.lsucu.pods.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Плавное появление: снизу вверх + из прозрачности, с мягкой пружиной.
 * [delayMs] — для «лесенки», когда блоки появляются по очереди.
 */
@Composable
fun Modifier.appear(delayMs: Int = 0, fromY: Dp = 18.dp): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    val dy = with(LocalDensity.current) { fromY.toPx() }
    return graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = (1f - p.value) * dy
    }
}

/** Значение, которое при первом показе «доезжает» от 0, а дальше плавно следует за целью. */
@Composable
fun animatedFromZero(target: Float, durationMs: Int = 900): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return a.value
}

/** Проценты, которые набегают числом: 0 → 87%. null — прочерк. */
@Composable
fun PercentText(value: Int?, color: Color, size: TextUnit, weight: FontWeight = FontWeight.Bold) {
    if (value == null) {
        Text("—", color = color, fontSize = size, fontWeight = weight)
        return
    }
    val shown = animatedFromZero(value.toFloat())
    Text("${shown.roundToInt()}%", color = color, fontSize = size, fontWeight = weight)
}

/** Молния, которая мягко «дышит», пока идёт зарядка. */
@Composable
fun ChargingBolt(size: Dp, tint: Color = LocalPods.current.green) {
    val t = rememberInfiniteTransition(label = "bolt")
    val k by t.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "boltPulse",
    )
    Box(Modifier.graphicsLayer { alpha = k; scaleX = 0.9f + 0.1f * k; scaleY = 0.9f + 0.1f * k }) {
        LineIcon(Glyph.BOLT, tint, size)
    }
}

/** Медленное «парение» вверх-вниз, как у продуктовых картинок Apple. */
@Composable
fun Modifier.floating(amplitude: Dp = 5.dp, periodMs: Int = 3200): Modifier {
    val t = rememberInfiniteTransition(label = "float")
    val k by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY",
    )
    val px = with(LocalDensity.current) { amplitude.toPx() }
    return graphicsLayer { translationY = k * px }
}
