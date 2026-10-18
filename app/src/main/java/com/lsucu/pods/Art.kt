package com.lsucu.pods

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lsucu.pods.ui.floating
import kotlin.math.min
import kotlin.math.sqrt

enum class Part { PODS, CASE, POD }

/**
 * Картинка модели. Если в res/drawable* лежит img_<family>_pods / img_<family>_case (PNG владельца) —
 * берём её, иначе рисуем сами.
 */
@SuppressLint("DiscouragedApi")
@Composable
fun PodsArt(family: Family, part: Part, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val res = remember(family, part) {
        val suffix = when (part) {
            Part.PODS -> "pods"
            Part.CASE -> "case"
            Part.POD -> if (family == Family.MAX) "pods" else "pod"
        }
        ctx.resources.getIdentifier("img_${family.key}_$suffix", "drawable", ctx.packageName)
    }
    if (res != 0) {
        Image(painterResource(res), null, modifier, contentScale = ContentScale.Fit)
    } else {
        Canvas(modifier) {
            when (part) {
                Part.PODS -> if (family == Family.MAX) drawMax() else drawPair(family)
                Part.CASE -> drawCase(family)
                Part.POD -> if (family == Family.MAX) drawMax() else drawPodIcon(family)
            }
        }
    }
}

/** Большая картинка как на макете: наушники сверху, кейс под ними. */
@Composable
fun PodsHero(family: Family, modifier: Modifier = Modifier, height: Dp = 250.dp) {
    if (family == Family.MAX) {
        PodsArt(Family.MAX, Part.PODS, modifier.fillMaxWidth(0.6f).height(height * 0.85f).floating())
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        PodsArt(family, Part.PODS, Modifier.fillMaxWidth(0.5f).height(height * 0.46f).floating())
        PodsArt(family, Part.CASE, Modifier.fillMaxWidth(0.56f).height(height * 0.54f).offset(y = (-6).dp))
    }
}

// ---------------- рисование ----------------

private val White = Color(0xFFFFFFFF)
private val Gloss = Color(0xFFEFF0F3)
private val Shade = Color(0xFFC6C9CF)
private val Outline = Color(0x1F000000)
private val Led = Color(0xFF34C759)

private fun bodyV(top: Float, bottom: Float) = Brush.verticalGradient(
    0f to White, 0.55f to Gloss, 1f to Shade, startY = top, endY = bottom,
)

private fun DrawScope.shadow(center: Offset, w: Float, h: Float, alpha: Float = 0.28f) {
    drawOval(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = alpha), Color.Transparent),
            center = center, radius = w / 2,
        ),
        topLeft = Offset(center.x - w / 2, center.y - h / 2),
        size = Size(w, h),
    )
}

/** Пропорции наушника (доли от рамки w×h): голова, ножка, амбушюра. */
private data class PodSpec(
    val headW: Float, val headH: Float,
    val stemW: Float, val stemTop: Float, val stemBottom: Float,
    val tip: Boolean,
)

private fun spec(f: Family) = when (f) {
    Family.AIRPODS_1 -> PodSpec(0.74f, 0.29f, 0.22f, 0.16f, 1.00f, tip = false)
    Family.AIRPODS_3, Family.AIRPODS_4 -> PodSpec(0.80f, 0.34f, 0.24f, 0.22f, 0.90f, tip = false)
    Family.PRO -> PodSpec(0.80f, 0.36f, 0.26f, 0.24f, 0.80f, tip = true)
    else -> PodSpec(0.82f, 0.38f, 0.27f, 0.25f, 0.78f, tip = true)
}

/** Правый наушник в рамке w×h: амбушюра слева, ножка справа-снизу. */
private fun DrawScope.drawPod(w: Float, h: Float, f: Family) {
    val s = spec(f)
    val stemX = w * 0.58f
    val stemW = w * s.stemW
    val stemTop = h * s.stemTop
    val stemH = h * (s.stemBottom - s.stemTop)

    // амбушюра (за головой)
    if (s.tip) {
        drawOval(
            Brush.verticalGradient(listOf(Color(0xFFD3D5DA), Color(0xFF9FA3AA)), startY = h * 0.07f, endY = h * 0.31f),
            topLeft = Offset(-w * 0.04f, h * 0.07f), size = Size(w * 0.42f, h * 0.24f),
        )
        drawOval(Color(0x55000000), topLeft = Offset(0f, h * 0.14f), size = Size(w * 0.12f, h * 0.10f))
    }

    // ножка
    drawRoundRect(bodyV(stemTop, stemTop + stemH), Offset(stemX, stemTop), Size(stemW, stemH), CornerRadius(stemW / 2))
    drawRoundRect(Outline, Offset(stemX, stemTop), Size(stemW, stemH), CornerRadius(stemW / 2), style = Stroke(1f))
    // металлический/сеточный кончик
    drawRoundRect(
        Color(0xFFD6D8DD), Offset(stemX + stemW * 0.08f, stemTop + stemH - h * 0.045f),
        Size(stemW * 0.84f, h * 0.035f), CornerRadius(stemW / 3),
    )
    // блик и тень на ножке
    drawLine(
        Color.White.copy(alpha = 0.9f),
        Offset(stemX + stemW * 0.30f, stemTop + stemH * 0.30f),
        Offset(stemX + stemW * 0.30f, stemTop + stemH * 0.85f),
        strokeWidth = stemW * 0.16f, cap = StrokeCap.Round,
    )
    drawLine(
        Color(0x1A000000),
        Offset(stemX + stemW * 0.80f, stemTop + stemH * 0.25f),
        Offset(stemX + stemW * 0.80f, stemTop + stemH * 0.92f),
        strokeWidth = stemW * 0.14f, cap = StrokeCap.Round,
    )

    // голова
    val headL = w * 0.12f
    val headW = w * s.headW
    val headH = h * s.headH
    drawOval(bodyV(0f, headH), Offset(headL, 0f), Size(headW, headH))
    drawOval(Outline, Offset(headL, 0f), Size(headW, headH), style = Stroke(1f))
    // блик на голове
    drawOval(Color.White.copy(alpha = 0.85f), Offset(headL + headW * 0.14f, headH * 0.10f), Size(headW * 0.42f, headH * 0.26f))

    // сенсор / решётка
    when (f) {
        Family.AIRPODS_1 ->
            drawOval(Color(0xFF3A3C41), Offset(headL + headW * 0.04f, headH * 0.42f), Size(headW * 0.16f, headH * 0.22f))
        Family.AIRPODS_3, Family.AIRPODS_4 ->
            drawOval(Color(0xFF2E3034), Offset(headL + headW * 0.62f, headH * 0.30f), Size(headW * 0.14f, headH * 0.16f))
        else ->
            drawOval(Color(0xFF2B2D31), Offset(headL + headW * 0.60f, headH * 0.22f), Size(headW * 0.20f, headH * 0.18f))
    }
}

/** Пара наушников. */
fun DrawScope.drawPair(f: Family) {
    val podW = size.width * 0.42f
    val podH = size.height * 0.90f
    val top = size.height * 0.02f
    val s = spec(f)
    // тени под ножками
    val shadowY = top + podH * s.stemBottom + size.height * 0.03f
    shadow(Offset(size.width * 0.31f, shadowY), podW * 0.55f, size.height * 0.07f)
    shadow(Offset(size.width * 0.69f, shadowY), podW * 0.55f, size.height * 0.07f)

    translate(left = size.width * 0.52f, top = top) {
        rotate(6f, pivot = Offset(podW / 2, podH / 2)) { drawPod(podW, podH, f) }
    }
    translate(left = size.width * 0.06f, top = top) {
        rotate(-6f, pivot = Offset(podW / 2, podH / 2)) {
            scale(-1f, 1f, pivot = Offset(podW / 2, podH / 2)) { drawPod(podW, podH, f) }
        }
    }
}

/** Один наушник по центру — для маленьких иконок в кольцах. */
fun DrawScope.drawPodIcon(f: Family) {
    val pw = size.width * 0.62f
    val ph = size.height * 0.96f
    translate((size.width - pw) / 2 + pw * 0.04f, (size.height - ph) / 2) { drawPod(pw, ph, f) }
}

/** Закрытый кейс. */
fun DrawScope.drawCase(f: Family) {
    val cw: Float
    val ch: Float
    val r: Float
    val seam: Float
    when (f) {
        Family.AIRPODS_1 -> {
            ch = min(size.height * 0.90f, size.width * 1.5f); cw = ch * 0.66f; r = cw * 0.30f; seam = 0.28f
        }
        Family.AIRPODS_3, Family.AIRPODS_4 -> {
            cw = min(size.width, size.height * 1.16f); ch = cw * 0.80f; r = ch * 0.38f; seam = 0.34f
        }
        else -> {
            cw = min(size.width, size.height * 1.28f); ch = cw * 0.74f; r = ch * 0.30f; seam = 0.36f
        }
    }
    val left = (size.width - cw) / 2
    val top = (size.height - ch) / 2 - size.height * 0.03f

    shadow(Offset(left + cw / 2, top + ch + size.height * 0.02f), cw * 0.9f, ch * 0.16f)

    val origin = Offset(left, top)
    val box = Size(cw, ch)
    val corner = CornerRadius(r)
    drawRoundRect(bodyV(top, top + ch), origin, box, corner)
    // боковое затенение
    drawRoundRect(
        Brush.horizontalGradient(
            0f to Color(0x16000000), 0.18f to Color.Transparent, 0.82f to Color.Transparent, 1f to Color(0x1C000000),
            startX = left, endX = left + cw,
        ),
        origin, box, corner,
    )
    // глянец сверху
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color.Transparent), startY = top, endY = top + ch * 0.3f),
        Offset(left + cw * 0.05f, top + ch * 0.02f), Size(cw * 0.9f, ch * 0.26f), CornerRadius(r * 0.8f),
    )
    // линия крышки (учитываем скругление углов)
    val y = top + ch * seam
    val d = r - (y - top)
    val dx = if (d > 0) r - sqrt(r * r - d * d) else 0f
    val px = 1.dp.toPx()
    drawLine(Color(0x38000000), Offset(left + dx + px, y), Offset(left + cw - dx - px, y), 1.6f * px)
    drawLine(Color(0xAAFFFFFF), Offset(left + dx + px, y + 1.6f * px), Offset(left + cw - dx - px, y + 1.6f * px), px)
    // индикатор
    val led = Offset(left + cw / 2, top + ch * (if (f == Family.AIRPODS_1) 0.60f else 0.62f))
    drawCircle(
        Brush.radialGradient(listOf(Led.copy(alpha = 0.45f), Color.Transparent), center = led, radius = cw * 0.05f),
        radius = cw * 0.05f, center = led,
    )
    drawCircle(Led, radius = cw * 0.012f, center = led)
    // петля для ремешка у Pro 3
    if (f == Family.PRO3) {
        drawRoundRect(
            Color(0xFFBFC2C8), Offset(left + cw - cw * 0.012f, top + ch * 0.58f),
            Size(cw * 0.03f, ch * 0.14f), CornerRadius(cw * 0.015f),
        )
    }
    drawRoundRect(Outline, origin, box, corner, style = Stroke(px))
}

/** AirPods Max: оголовье + две чашки. */
fun DrawScope.drawMax() {
    val w = size.width
    val h = size.height
    val alu = Brush.horizontalGradient(listOf(Color(0xFFB9BCC3), Color(0xFFEDEEF1), Color(0xFFA9ADB4)))
    shadow(Offset(w / 2, h * 0.95f), w * 0.8f, h * 0.08f)
    // оголовье
    drawArc(
        Brush.verticalGradient(listOf(Color(0xFFEDEEF1), Color(0xFFB4B8BF)), startY = 0f, endY = h * 0.5f),
        startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.15f, h * 0.04f), size = Size(w * 0.70f, h * 0.90f),
        style = Stroke(width = w * 0.05f, cap = StrokeCap.Round),
    )
    // сетка-подвес
    drawArc(
        Color(0xFF8E9299).copy(alpha = 0.55f), 188f, 164f, false,
        Offset(w * 0.20f, h * 0.10f), Size(w * 0.60f, h * 0.80f),
        style = Stroke(width = w * 0.03f),
    )
    for (right in listOf(false, true)) {
        val cupL = if (right) w * 0.70f else w * 0.06f
        // штанга
        drawRoundRect(Color(0xFFC9CCD2), Offset(if (right) w * 0.83f else w * 0.15f, h * 0.44f), Size(w * 0.02f, h * 0.08f))
        // чашка
        drawRoundRect(alu, Offset(cupL, h * 0.48f), Size(w * 0.24f, h * 0.42f), CornerRadius(w * 0.09f))
        drawRoundRect(Outline, Offset(cupL, h * 0.48f), Size(w * 0.24f, h * 0.42f), CornerRadius(w * 0.09f), style = Stroke(1f))
        // амбушюра (внутренняя сторона)
        val cushL = if (right) cupL - w * 0.05f else cupL + w * 0.19f
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF45484F), Color(0xFF26282D))),
            Offset(cushL, h * 0.50f), Size(w * 0.10f, h * 0.38f), CornerRadius(w * 0.05f),
        )
    }
}
