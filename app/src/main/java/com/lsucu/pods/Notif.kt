package com.lsucu.pods

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/** Каналы, карточка с зарядом (видна на экране блокировки) и оповещения. */
object Notif {
    const val CH_BATTERY = "battery"
    private const val CH_ALERTS = "alerts"
    const val ID_STATUS = 1
    private const val ID_LOW = 2
    private const val ID_FULL = 3

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("status") // старый канал с IMPORTANCE_LOW (тихие прячутся с экрана блокировки)
        nm.createNotificationChannel(
            NotificationChannel(CH_BATTERY, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, ctx.getString(R.string.notif_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    /** Постоянная карточка: картинка, имя, точка «подключено», три батарейки с процентами. */
    fun status(ctx: Context, s: PodsStatus?, connected: Boolean): Notification {
        val b = Notification.Builder(ctx, CH_BATTERY)
            .setSmallIcon(R.drawable.ic_stat_pods)
            .setContentIntent(openApp(ctx))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(if (Prefs.lockscreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        val model = PodsModel.of(s?.model ?: Prefs.model)
        val title = podsName(ctx, model.title)
        if (s == null) {
            // данных сейчас нет: если наушники уже знакомы — «Не подключено», иначе «Ищу AirPods…»
            val text = if (Prefs.model != 0) R.string.not_connected else R.string.notif_searching
            return b.setContentTitle(title).setContentText(ctx.getString(text)).build()
        }

        val rv = RemoteViews(ctx.packageName, R.layout.notif_battery)
        rv.setImageViewBitmap(R.id.pods, podsBitmap(ctx, model.family, dp(ctx, 88)))
        rv.setTextViewText(R.id.title, title)
        rv.setViewVisibility(R.id.dot, if (connected) View.VISIBLE else View.GONE)
        fun slot(img: Int, txt: Int, v: Int?, ch: Boolean, show: Boolean) {
            rv.setViewVisibility(img, if (show) View.VISIBLE else View.GONE)
            rv.setViewVisibility(txt, if (show) View.VISIBLE else View.GONE)
            rv.setImageViewBitmap(img, batteryBitmap(ctx, v, ch))
            rv.setTextViewText(txt, (if (ch) "⚡" else "") + (v?.let { "$it%" } ?: "—"))
        }
        val max = model.family == Family.MAX
        slot(R.id.bat_l, R.id.txt_l, if (max) s.left ?: s.right else s.left, s.leftCharging, true)
        slot(R.id.bat_r, R.id.txt_r, s.right, s.rightCharging, !max)
        slot(R.id.bat_c, R.id.txt_c, s.case, s.caseCharging, !max)

        return b
            .setContentTitle(title)
            .setContentText(batteryLine(ctx, s)) // для мест, где кастомный вид не показывается
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(rv)
            .build()
    }

    // ---------------- оповещения ----------------

    private val lowFired = mutableSetOf<Charge.Part>()
    private val fullFired = mutableSetOf<Charge.Part>()

    /** «Мало заряда» — один раз за разрядку, «Заряжено» — один раз за зарядку. */
    @Synchronized
    fun checkAlerts(ctx: Context, s: PodsStatus) {
        val parts = listOf(
            Triple(Charge.Part.LEFT, s.left, s.leftCharging) to R.string.name_left,
            Triple(Charge.Part.RIGHT, s.right, s.rightCharging) to R.string.name_right,
            Triple(Charge.Part.CASE, s.case, s.caseCharging) to R.string.name_case,
        )
        val low = mutableListOf<String>()
        val full = mutableListOf<String>()
        val threshold = Prefs.lowThreshold
        for ((t, name) in parts) {
            val (part, v, ch) = t
            if (v == null) continue
            if (!ch && v <= threshold) {
                if (Prefs.alertLow && lowFired.add(part)) low += "${ctx.getString(name)} $v%"
            } else if (ch || v > threshold + 5) {
                lowFired.remove(part)
            }
            if (ch && v >= 100) {
                if (Prefs.alertFull && fullFired.add(part)) full += ctx.getString(name)
            } else if (v < 95) {
                fullFired.remove(part)
            }
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (low.isNotEmpty()) nm.notify(ID_LOW, alert(ctx, ctx.getString(R.string.alert_low_title), low.joinToString("  ·  ")))
        if (full.isNotEmpty()) {
            nm.notify(ID_FULL, alert(ctx, ctx.getString(R.string.alert_full_title),
                ctx.getString(R.string.alert_full_text, full.joinToString(", "))))
        }
    }

    private fun alert(ctx: Context, title: String, text: String) =
        Notification.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pods)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

    // ---------------- картинки ----------------

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val podsCache = mutableMapOf<Family, Bitmap>()

    @SuppressLint("DiscouragedApi")
    fun podsBitmap(ctx: Context, family: Family, px: Int): Bitmap = podsCache.getOrPut(family) {
        val res = ctx.resources.getIdentifier("img_${family.key}_pods", "drawable", ctx.packageName)
        if (res != 0) {
            BitmapFactory.decodeResource(ctx.resources, res)?.let { return@getOrPut Bitmap.createScaledBitmap(it, px, px * it.height / it.width, true) }
        }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(ctx), LayoutDirection.Ltr, ComposeCanvas(bmp.asImageBitmap()), Size(px.toFloat(), px.toFloat()),
        ) {
            if (family == Family.MAX) drawMax() else drawPair(family)
        }
        bmp
    }

    /** Мини-батарейка: серая рамка, заливка зелёная / красная (≤20%). */
    fun batteryBitmap(ctx: Context, v: Int?, charging: Boolean, wDp: Int = 20, hDp: Int = 11): Bitmap {
        val w = dp(ctx, wDp)
        val h = dp(ctx, hDp)
        val d = ctx.resources.displayMetrics.density
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        val stroke = 1.3f * d
        val body = w * 0.86f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA3B0.toInt(); style = Paint.Style.STROKE; strokeWidth = stroke }
        c.drawRoundRect(RectF(stroke / 2, stroke / 2, body - stroke / 2, h - stroke / 2), 2.5f * d, 2.5f * d, p)
        p.style = Paint.Style.FILL
        c.drawRect(body + stroke * 0.3f, h * 0.32f, w.toFloat(), h * 0.68f, p)
        if (v != null) {
            p.color = if (charging || v > 20) 0xFF30D158.toInt() else 0xFFFF453A.toInt()
            val inner = (body - stroke * 4) * v.coerceIn(0, 100) / 100f
            c.drawRoundRect(RectF(stroke * 2, stroke * 2, stroke * 2 + inner, h - stroke * 2), 1.5f * d, 1.5f * d, p)
        }
        return bmp
    }
}
