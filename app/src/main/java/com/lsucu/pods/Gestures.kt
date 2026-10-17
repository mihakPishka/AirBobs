package com.lsucu.pods

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.StringRes

/** Что делать по нажатию на ножку. */
enum class Action(@StringRes val label: Int) {
    DEFAULT(R.string.act_default),
    PLAY_PAUSE(R.string.act_play_pause),
    NEXT(R.string.act_next),
    PREV(R.string.act_prev),
    VOL_UP(R.string.act_vol_up),
    VOL_DOWN(R.string.act_vol_down),
    ANC(R.string.act_anc),
    TRANSPARENCY(R.string.act_transparency),
    ADAPTIVE(R.string.act_adaptive),
    NOISE_OFF(R.string.act_noise_off),
    CYCLE(R.string.act_cycle),
    CONVERSATION(R.string.act_conversation),
    NONE(R.string.act_none),
}

/**
 * Свои действия на жесты. Наушники отдают нам только жесты из [mask] (команда 0x39),
 * остальные обрабатывают сами — так «Стандартно» работает без задержки.
 */
object Gestures {
    private const val TAG = "PodsGest"

    fun get(left: Boolean, press: Aap.Press): Action =
        Prefs.gesture(left, press.name)?.let { n -> Action.entries.firstOrNull { it.name == n } } ?: Action.DEFAULT

    fun set(left: Boolean, press: Aap.Press, a: Action) {
        Prefs.setGesture(left, press.name, a.name)
        if (Aap.connected.value) Aap.setStemIntercept(mask())
    }

    /** Бит жеста в маске 0x39. */
    private fun bit(p: Aap.Press) = when (p) {
        Aap.Press.SINGLE -> 0x01
        Aap.Press.DOUBLE -> 0x02
        Aap.Press.TRIPLE -> 0x04
        Aap.Press.LONG -> 0x08
    }

    fun mask(): Int = Aap.Press.entries
        .filter { p -> get(true, p) != Action.DEFAULT || get(false, p) != Action.DEFAULT }
        .fold(0) { m, p -> m or bit(p) }

    /** Перехваченный жест с «Стандартно» на этом наушнике — повторяем поведение AirPods сами. */
    private fun standard(p: Aap.Press) = when (p) {
        Aap.Press.SINGLE -> Action.PLAY_PAUSE
        Aap.Press.DOUBLE -> Action.NEXT
        Aap.Press.TRIPLE -> Action.PREV
        Aap.Press.LONG -> Action.CYCLE
    }

    fun handle(ctx: Context, e: Aap.StemPress) {
        val a = get(e.left, e.press).let { if (it == Action.DEFAULT) standard(e.press) else it }
        Log.i(TAG, "${if (e.left) "L" else "R"} ${e.press} -> $a")
        val audio = ctx.getSystemService(AudioManager::class.java)
        when (a) {
            Action.PLAY_PAUSE -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Action.NEXT -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_NEXT)
            Action.PREV -> mediaKey(audio, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Action.VOL_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            Action.VOL_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            Action.ANC -> Aap.setMode(Aap.Mode.ANC)
            Action.TRANSPARENCY -> Aap.setMode(Aap.Mode.TRANSPARENCY)
            Action.ADAPTIVE -> Aap.setMode(Aap.Mode.ADAPTIVE)
            Action.NOISE_OFF -> Aap.setMode(Aap.Mode.OFF)
            Action.CYCLE -> Aap.setMode(
                if (Aap.mode.value == Aap.Mode.ANC) Aap.Mode.TRANSPARENCY else Aap.Mode.ANC
            )
            Action.CONVERSATION -> Aap.setConversation(Aap.conversation.value != true)
            Action.NONE, Action.DEFAULT -> Unit
        }
    }

    private fun mediaKey(audio: AudioManager, code: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
