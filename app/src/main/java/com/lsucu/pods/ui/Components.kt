package com.lsucu.pods.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CardShape = RoundedCornerShape(22.dp)

@Composable
fun PodsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPods.current
    Column(
        modifier
            .clip(CardShape)
            .background(c.card)
            .border(1.dp, c.border, CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

fun batteryColor(c: PodsColors, value: Int?, charging: Boolean): Color = when {
    value == null -> c.track
    charging || value > 20 -> c.green
    else -> c.red
}

/** Кольцо заряда; в центре — что угодно (иконка наушника, кейса, молния). */
@Composable
fun BatteryRing(
    value: Int?,
    charging: Boolean,
    ringSize: Dp = 76.dp,
    stroke: Dp = 6.dp,
    center: @Composable () -> Unit,
) {
    val c = LocalPods.current
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val w = stroke.toPx()
            val tl = Offset(w / 2, w / 2)
            val s = Size(size.width - w, size.height - w)
            drawArc(c.track, 0f, 360f, false, tl, s, style = Stroke(w))
            if (value != null) {
                drawArc(
                    batteryColor(c, value, charging), -90f, 360f * value.coerceIn(0, 100) / 100f, false, tl, s,
                    style = Stroke(w, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

@Composable
fun StatusDot(on: Boolean, size: Dp = 8.dp) {
    val c = LocalPods.current
    Box(Modifier.size(size).clip(CircleShape).background(if (on) c.green else c.text2))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
        color = LocalPods.current.text,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = LocalPods.current.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
}

/** Заголовок вложенного экрана со стрелкой назад. */
@Composable
fun SubTopBar(title: String, onBack: () -> Unit) {
    val c = LocalPods.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = c.text)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Карточка-группа настроек: строки с тонкими разделителями. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    PodsCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp), content = content)
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = if (onClick != null) ({ Chevron() }) else null,
) {
    val c = LocalPods.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = c.text2, fontSize = 13.sp)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (divider) {
            HorizontalDivider(
                Modifier.padding(start = if (icon != null) 56.dp else 16.dp),
                thickness = 1.dp, color = c.border,
            )
        }
    }
}

@Composable
fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = LocalPods.current.text2)
}

// ---------------- линейные иконки (в core-наборе Material их нет) ----------------

enum class Glyph { GLOBE, MOON, HEADPHONES, TOUCH, WINDOW, POWER, SHIELD, BELL, INFO, BOLT, WAVES, CHAT }

@Composable
fun LineIcon(glyph: Glyph, tint: Color = LocalPods.current.text, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) { drawGlyph(glyph, tint) }
}

private fun DrawScope.drawGlyph(g: Glyph, tint: Color) {
    val w = size.width
    val h = size.height
    val st = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
    val cx = w / 2
    val cy = h / 2
    when (g) {
        Glyph.GLOBE -> {
            drawCircle(tint, w * 0.42f, style = st)
            drawOval(tint, Offset(cx - w * 0.18f, h * 0.08f), Size(w * 0.36f, h * 0.84f), style = st)
            drawLine(tint, Offset(w * 0.08f, cy), Offset(w * 0.92f, cy), st.width)
        }
        Glyph.MOON -> {
            val p = Path().apply {
                moveTo(w * 0.62f, h * 0.10f)
                cubicTo(w * 0.20f, h * 0.12f, w * 0.06f, h * 0.62f, w * 0.34f, h * 0.84f)
                cubicTo(w * 0.56f, h * 1.00f, w * 0.86f, h * 0.88f, w * 0.92f, h * 0.62f)
                cubicTo(w * 0.62f, h * 0.74f, w * 0.38f, h * 0.44f, w * 0.62f, h * 0.10f)
                close()
            }
            drawPath(p, tint, style = st)
        }
        Glyph.HEADPHONES -> {
            drawArc(tint, 180f, 180f, false, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), style = st)
            drawRoundRect(tint, Offset(w * 0.08f, h * 0.52f), Size(w * 0.22f, h * 0.36f), CornerRadius(w * 0.08f), style = st)
            drawRoundRect(tint, Offset(w * 0.70f, h * 0.52f), Size(w * 0.22f, h * 0.36f), CornerRadius(w * 0.08f), style = st)
        }
        Glyph.TOUCH -> {
            drawRoundRect(tint, Offset(w * 0.36f, h * 0.30f), Size(w * 0.28f, h * 0.62f), CornerRadius(w * 0.14f), style = st)
            drawArc(tint, 200f, 140f, false, Offset(w * 0.16f, h * 0.04f), Size(w * 0.68f, h * 0.50f), style = st)
        }
        Glyph.WINDOW -> {
            drawRoundRect(tint, Offset(w * 0.12f, h * 0.18f), Size(w * 0.76f, h * 0.64f), CornerRadius(w * 0.12f), style = st)
            drawLine(tint, Offset(w * 0.30f, h * 0.62f), Offset(w * 0.70f, h * 0.62f), st.width, StrokeCap.Round)
        }
        Glyph.POWER -> {
            drawArc(tint, -60f, 300f, false, Offset(w * 0.14f, h * 0.16f), Size(w * 0.72f, h * 0.72f), style = st)
            drawLine(tint, Offset(cx, h * 0.08f), Offset(cx, h * 0.48f), st.width, StrokeCap.Round)
        }
        Glyph.SHIELD -> {
            val p = Path().apply {
                moveTo(cx, h * 0.08f)
                lineTo(w * 0.86f, h * 0.24f)
                cubicTo(w * 0.86f, h * 0.60f, w * 0.70f, h * 0.82f, cx, h * 0.94f)
                cubicTo(w * 0.30f, h * 0.82f, w * 0.14f, h * 0.60f, w * 0.14f, h * 0.24f)
                close()
            }
            drawPath(p, tint, style = st)
            val ck = Path().apply {
                moveTo(w * 0.34f, h * 0.50f); lineTo(w * 0.46f, h * 0.62f); lineTo(w * 0.68f, h * 0.38f)
            }
            drawPath(ck, tint, style = st)
        }
        Glyph.BELL -> {
            val p = Path().apply {
                moveTo(w * 0.20f, h * 0.74f)
                lineTo(w * 0.80f, h * 0.74f)
                lineTo(w * 0.72f, h * 0.62f)
                lineTo(w * 0.72f, h * 0.42f)
                cubicTo(w * 0.72f, h * 0.10f, w * 0.28f, h * 0.10f, w * 0.28f, h * 0.42f)
                lineTo(w * 0.28f, h * 0.62f)
                close()
            }
            drawPath(p, tint, style = st)
            drawLine(tint, Offset(w * 0.42f, h * 0.88f), Offset(w * 0.58f, h * 0.88f), st.width, StrokeCap.Round)
        }
        Glyph.INFO -> {
            drawCircle(tint, w * 0.42f, style = st)
            drawLine(tint, Offset(cx, h * 0.44f), Offset(cx, h * 0.72f), st.width, StrokeCap.Round)
            drawCircle(tint, w * 0.055f, Offset(cx, h * 0.30f))
        }
        Glyph.BOLT -> {
            val p = Path().apply {
                moveTo(w * 0.58f, h * 0.06f)
                lineTo(w * 0.22f, h * 0.56f)
                lineTo(w * 0.48f, h * 0.56f)
                lineTo(w * 0.40f, h * 0.94f)
                lineTo(w * 0.78f, h * 0.42f)
                lineTo(w * 0.52f, h * 0.42f)
                close()
            }
            drawPath(p, tint)
        }
        Glyph.WAVES -> {
            for (i in 0..2) {
                val y = h * (0.28f + i * 0.22f)
                val p = Path().apply {
                    moveTo(w * 0.10f, y)
                    cubicTo(w * 0.30f, y - h * 0.12f, w * 0.40f, y + h * 0.12f, w * 0.50f, y)
                    cubicTo(w * 0.60f, y - h * 0.12f, w * 0.70f, y + h * 0.12f, w * 0.90f, y)
                }
                drawPath(p, tint, style = st)
            }
        }
        Glyph.CHAT -> {
            drawRoundRect(tint, Offset(w * 0.10f, h * 0.16f), Size(w * 0.80f, h * 0.56f), CornerRadius(w * 0.18f), style = st)
            val p = Path().apply { moveTo(w * 0.32f, h * 0.70f); lineTo(w * 0.26f, h * 0.90f); lineTo(w * 0.50f, h * 0.72f) }
            drawPath(p, tint, style = st)
        }
    }
}
