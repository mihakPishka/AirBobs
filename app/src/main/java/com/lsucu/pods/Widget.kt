package com.lsucu.pods

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.lsucu.pods.ui.Lang

/** Виджет «как на макете»: картинка, имя, статус, три батарейки. Обновляет его сервис. */
class BatteryWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // система просит перерисовать (добавили виджет, перезагрузка) — берём последнее известное
        render(ctx, mgr, ids, liveStatus(PodsRepo.mine.value, Aap.battery.value), Aap.connected.value)
    }

    companion object {
        private var lastKey = ""

        fun update(ctx: Context, s: PodsStatus?, connected: Boolean) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, BatteryWidget::class.java))
            if (ids.isEmpty()) return
            val key = "${s?.left}|${s?.right}|${s?.case}|${s?.leftCharging}|${s?.rightCharging}|${s?.caseCharging}|$connected|${s?.model}"
            if (key == lastKey) return
            lastKey = key
            render(ctx, mgr, ids, s, connected)
        }

        private fun render(base: Context, mgr: AppWidgetManager, ids: IntArray, s: PodsStatus?, connected: Boolean) {
            val ctx = Lang.wrap(base)
            val model = PodsModel.of(s?.model ?: Prefs.model)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_battery)
            rv.setImageViewBitmap(R.id.pods, Notif.podsBitmap(ctx, model.family, Notif.dp(ctx, 88)))
            rv.setTextViewText(R.id.title, podsName(ctx, model.title))
            rv.setViewVisibility(R.id.dot, if (connected) View.VISIBLE else View.GONE)
            rv.setTextViewText(R.id.status, ctx.getString(if (connected) R.string.connected else R.string.not_connected))

            val max = model.family == Family.MAX
            fun slot(slot: Int, img: Int, txt: Int, v: Int?, ch: Boolean, show: Boolean) {
                rv.setViewVisibility(slot, if (show) View.VISIBLE else View.GONE)
                rv.setImageViewBitmap(img, Notif.batteryBitmap(ctx, v, ch, 26, 14))
                rv.setTextViewText(txt, (if (ch) "⚡" else "") + (v?.let { "$it%" } ?: "—"))
            }
            slot(R.id.slot_l, R.id.bat_l, R.id.txt_l, if (max) s?.left ?: s?.right else s?.left, s?.leftCharging == true, true)
            slot(R.id.slot_r, R.id.bat_r, R.id.txt_r, s?.right, s?.rightCharging == true, !max)
            slot(R.id.slot_c, R.id.bat_c, R.id.txt_c, s?.case, s?.caseCharging == true, !max)

            rv.setOnClickPendingIntent(
                R.id.root,
                PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            mgr.updateAppWidget(ids, rv)
        }
    }
}
