package com.lsucu.pods

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource
import com.lsucu.pods.ui.BatteryRing
import com.lsucu.pods.ui.Glyph
import com.lsucu.pods.ui.LineIcon
import com.lsucu.pods.ui.LocalPods
import com.lsucu.pods.ui.PodsTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** Окно поверх всех приложений (TYPE_APPLICATION_OVERLAY) с Compose внутри. */
class PopupController(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private val status = mutableStateOf<PodsStatus?>(null)
    private val title = mutableStateOf("AirPods")
    private var anim = MutableTransitionState(false)

    val isShowing get() = view != null
    var isDemo = false
        private set

    fun show(s: PodsStatus, name: String) {
        status.value = s
        title.value = name
        isDemo = s.raw == "демо"
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, if (isDemo) 8_000 else 30_000)
        if (view != null) {
            anim.targetState = true
            return
        }

        val o = OverlayOwner().also { it.start() }
        val a = MutableTransitionState(false).also { anim = it }
        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                val dark = when (Prefs.appearance) {
                    1 -> true
                    2 -> false
                    else -> isSystemInDarkTheme()
                }
                PodsTheme(dark) {
                AnimatedVisibility(
                    visibleState = a,
                    enter = slideInVertically(spring(dampingRatio = 0.78f, stiffness = 380f)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
                ) {
                    PopupCard(status.value, title.value, onClose = { hide() })
                }
                }
            }
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE) hide()
                false
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            o.stop()
            return
        }
        view = v
        owner = o
        a.targetState = true
        buzz()
    }

    fun update(s: PodsStatus) {
        if (!isDemo) status.value = s
    }

    fun hide(immediate: Boolean = false) {
        val v = view ?: return
        handler.removeCallbacks(autoHide)
        anim.targetState = false
        val remove = Runnable {
            if (view === v) {
                runCatching { wm.removeView(v) }
                owner?.stop()
                view = null
                owner = null
            }
        }
        if (immediate) remove.run() else handler.postDelayed(remove, 320)
    }

    private val autoHide = Runnable { hide() }

    private fun buzz() = runCatching {
        val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        v.vibrate(
            if (Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            else VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}

private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

// ---------------- UI ----------------

@Composable
fun PopupCard(s: PodsStatus?, title: String, onClose: () -> Unit) {
    val c = LocalPods.current
    val model = PodsModel.of(s?.model ?: Prefs.model)
    val family = model.family
    var drag by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(36.dp)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .offset { IntOffset(0, drag.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag > 120.dp.toPx()) onClose() else drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag = (drag + dy).coerceAtLeast(0f) }
            }
            .shadow(24.dp, shape)
            .background(c.card, shape)
            .border(1.dp, c.border, shape)
            .padding(top = 18.dp, bottom = 24.dp, start = 20.dp, end = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    color = c.text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                        .background(c.track, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(10.dp)) {
                        val col = c.text.copy(alpha = 0.6f)
                        drawLine(col, Offset.Zero, Offset(size.width, size.height), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(col, Offset(size.width, 0f), Offset(0f, size.height), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (family == Family.MAX) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PodsArt(Family.MAX, Part.PODS, Modifier.width(160.dp).height(130.dp))
                        Spacer(Modifier.height(14.dp))
                        PopupRing("", s?.left ?: s?.right, s?.leftCharging == true)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PodsArt(family, Part.PODS, Modifier.width(140.dp).height(120.dp))
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PopupRing(stringResource(R.string.label_left), s?.left, s?.leftCharging == true)
                            PopupRing(stringResource(R.string.label_right), s?.right, s?.rightCharging == true)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PodsArt(family, Part.CASE, Modifier.width(120.dp).height(120.dp))
                        Spacer(Modifier.height(14.dp))
                        PopupRing(stringResource(R.string.label_case), s?.case, s?.caseCharging == true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupRing(label: String, value: Int?, charging: Boolean) {
    val c = LocalPods.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BatteryRing(value, charging, ringSize = 50.dp, stroke = 5.dp) {
            if (charging) LineIcon(Glyph.BOLT, c.green, 18.dp)
            else Text(label, color = c.text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Text(value?.let { "$it%" } ?: "—", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
