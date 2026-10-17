package com.lsucu.pods

import android.os.SystemClock
import kotlin.math.ceil

/**
 * Оценка времени до 100%: по реальной скорости роста заряда, пока данных мало — по типичной.
 * Живёт в процессе приложения (сервис и экран видят одно и то же).
 */
object Charge {
    enum class Part { LEFT, RIGHT, CASE }

    private data class Sample(val t: Long, val v: Int)

    private val hist = mutableMapOf<Part, ArrayDeque<Sample>>()

    // Типичная скорость (%/мин) — наушники быстрее кейса; выше 80% зарядка замедляется.
    private const val POD_RATE = 1.6f
    private const val CASE_RATE = 0.7f
    private const val MIN_SPAN_MS = 3 * 60_000L

    data class Eta(val minutes: Int, val measured: Boolean)

    @Synchronized
    fun add(s: PodsStatus) {
        rec(Part.LEFT, s.left, s.leftCharging)
        rec(Part.RIGHT, s.right, s.rightCharging)
        rec(Part.CASE, s.case, s.caseCharging)
    }

    private fun rec(p: Part, v: Int?, charging: Boolean) {
        if (v == null) return
        if (!charging) { hist.remove(p); return }
        val q = hist.getOrPut(p) { ArrayDeque() }
        if (q.lastOrNull()?.v != v) q.addLast(Sample(SystemClock.elapsedRealtime(), v))
        while (q.size > 40) q.removeFirst()
    }

    @Synchronized
    fun eta(p: Part, v: Int?, charging: Boolean): Eta? {
        if (v == null || !charging) return null
        if (v >= 100) return Eta(0, true)
        val q = hist[p]
        var rate = if (p == Part.CASE) CASE_RATE else POD_RATE
        var measured = false
        if (q != null && q.size >= 2) {
            val span = q.last().t - q.first().t
            val gain = q.last().v - q.first().v
            if (span >= MIN_SPAN_MS && gain > 0) {
                rate = gain / (span / 60_000f)
                measured = true
            }
        }
        // до 80% — с текущей скоростью, дальше в полтора раза медленнее
        val fast = (minOf(v, 80).let { 80 - it }).coerceAtLeast(0)
        val slow = 100 - maxOf(v, 80)
        val minutes = fast / rate + slow / (rate / 1.5f)
        return Eta(ceil(minutes).toInt().coerceAtLeast(1), measured)
    }

    /** Самое долгое из того, что сейчас заряжается. */
    fun eta(s: PodsStatus): Eta? = listOfNotNull(
        eta(Part.LEFT, s.left, s.leftCharging),
        eta(Part.RIGHT, s.right, s.rightCharging),
        eta(Part.CASE, s.case, s.caseCharging),
    ).maxByOrNull { it.minutes }

    fun anyCharging(s: PodsStatus?) =
        s != null && (s.leftCharging || s.rightCharging || s.caseCharging)
}
