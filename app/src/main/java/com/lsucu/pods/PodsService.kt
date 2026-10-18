package com.lsucu.pods

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.LifecycleService
import com.lsucu.pods.ui.Lang
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PodsService : LifecycleService() {

    companion object {
        const val TAG = "Pods"
        // тишина = кейс закрыли; порог подстраивается под частоту рекламы (~2.5 интервала)
        private const val LID_CLOSED_MIN_MS = 4_000L  // быстрое закрытие и так ловим по флагу крышки
        private const val LID_CLOSED_MAX_MS = 6_000L
        private const val NEW_SESSION_MS = 6_000L  // столько тишины перед сигналом = кейс открыли заново
        private const val LEARN_RSSI = -55         // так близко — точно наши, запоминаем модель
        private const val STRICT_RSSI = -60        // без ключа: дальше этого чужие не считаются нашими

        fun start(ctx: Context) {
            if (!Perms.bluetooth(ctx)) return
            ctx.startForegroundService(Intent(ctx, PodsService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PodsService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var popup: PopupController
    private var scanning = false
    private var lastSeen = 0L
    private var adInterval = 1_000f  // сглаженный интервал между пакетами «моих» наушников
    private var adCount = 0
    private var lidOpen = true  // false после флага «крышка закрыта» — следующее открытие покажет окно сразу
    private var lastRaw = ""
    private var lastNotif = ""

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate() {
        super.onCreate()
        Notif.channels(this)
        try {
            val n = Notif.status(this, null, false)
            if (Build.VERSION.SDK_INT >= 29) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                // Android 10–11: BLE-скан в фоне идёт через геолокацию — нужен тип location
                if (Build.VERSION.SDK_INT < 31 && Perms.bluetooth(this)) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                startForeground(Notif.ID_STATUS, n, type)
            } else {
                startForeground(Notif.ID_STATUS, n)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return
        }
        popup = PopupController(this)
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        registerReceiver(aclReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        if (getSystemService(PowerManager::class.java).isInteractive) startScan()
        handler.post(ticker)
        Aap.connect(this)  // если AirPods уже подключены
        lifecycleScope.launch {
            Aap.stem.collect { Gestures.handle(this@PodsService, it) }
        }
        // единый статус: BLE (кейс открыт) + точный заряд из канала (наушники в ушах)
        lifecycleScope.launch {
            // ticker: когда пакеты перестают приходить, статус сам становится «не подключено»
            combine(PodsRepo.mine, Aap.battery, Aap.connected, ticker(5_000)) { ble, exact, conn, now ->
                liveStatus(ble, exact, now) to conn
            }.collect { (s, conn) ->
                if (s != null) {
                    Charge.add(s)
                    Notif.checkAlerts(this@PodsService, s)
                    if (popup.isShowing && Aap.battery.value != null) popup.update(s)
                }
                updateNotification(s, conn)
                BatteryWidget.update(this@PodsService, s, conn)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(aclReceiver) }
        Aap.disconnect()
        stopScan()
        if (::popup.isInitialized) popup.hide(immediate = true)
        super.onDestroy()
    }

    /** action "test_popup" — показать окно с последними данными (или демо). */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == "refresh") refreshNotification()
        if (intent?.action == "test_popup" && ::popup.isInitialized) {
            val s = liveStatus(PodsRepo.mine.value, Aap.battery.value) ?: demoStatus()
            popup.show(s, podsName(this, s.modelName))
        }
        return START_STICKY
    }

    private fun demoStatus() = PodsStatus(
        model = 0x2027, left = 80, right = 70, case = 50,
        leftCharging = true, rightCharging = true, caseCharging = false,
        statusByte = 0, lidByte = 0, rssi = -40, raw = "демо", time = SystemClock.elapsedRealtime(),
    )

    // ---------- сканирование ----------

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanning || !Perms.bluetooth(this)) return
        val scanner = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setManufacturerData(AirPodsParser.APPLE_ID, byteArrayOf(0x07), byteArrayOf(0xFF.toByte()))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
            scanning = true
            PodsRepo.setScanning(true)
            Log.i(TAG, "scan started")
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        runCatching {
            getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(callback)
        }
        scanning = false
        PodsRepo.setScanning(false)
        Log.i(TAG, "scan stopped")
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            scanning = false
            PodsRepo.setScanning(false)
        }
    }

    private fun handle(r: ScanResult) {
        val data = r.scanRecord?.getManufacturerSpecificData(AirPodsParser.APPLE_ID) ?: return
        val now = SystemClock.elapsedRealtime()
        val s = AirPodsParser.parse(data, r.rssi, now) ?: return
        PodsRepo.onAny(s)
        if (s.raw != lastRaw) {
            lastRaw = s.raw
            Log.d(TAG, "ad ${s.modelHex} rssi=${s.rssi} st=%02X lid=%02X raw=${s.raw}".format(s.statusByte, s.lidByte))
        }
        if (!isMine(s, r.device.address)) return

        val gap = now - lastSeen
        lastSeen = now
        if (gap < NEW_SESSION_MS) {
            adInterval = adInterval * 0.8f + gap * 0.2f
            if (++adCount % 20 == 0) Log.d(TAG, "ad interval ~${adInterval.toInt()} ms (last $gap)")
        }
        PodsRepo.onMine(s)
        val shown = withExactBattery(s, Aap.battery.value) ?: s  // s только что пришёл — свежий

        if (s.lidClosed) {
            if (popup.isShowing && !popup.isDemo) {
                Log.i(TAG, "lid closed (flag)")
                popup.hide()
            }
            lidOpen = false
        } else if (popup.isShowing) {
            popup.update(shown)
        } else if ((gap > NEW_SESSION_MS || !lidOpen) && s.case != null && Prefs.popup && Settings.canDrawOverlays(this)) {
            lidOpen = true
            Log.i(TAG, "case opened -> popup")
            popup.show(shown, podsName(this, s.modelName))
        }
    }

    /**
     * Свои ли это AirPods.
     * 1) Есть ключ IRK (получен из канала управления) — только по нему: чужие той же модели не пройдут.
     * 2) Ключа нет — строгий запасной режим: на телефоне есть спаренные AirPods, модель совпадает
     *    и сигнал очень сильный (наушники буквально у телефона).
     */
    private fun isMine(s: PodsStatus, address: String): Boolean {
        Identity.isMine(address)?.let { mine ->
            if (mine && Prefs.model != s.model) {
                Prefs.model = s.model
                Log.i(TAG, "model by key ${s.modelHex}")
            }
            return mine
        }
        if (s.rssi < STRICT_RSSI) return false
        if (!Devices.hasPairedAirPods(this)) return false
        val saved = Prefs.model
        if (saved == 0) {
            if (s.rssi < LEARN_RSSI || s.case == null) return false
            Prefs.model = s.model
            Log.i(TAG, "learned model ${s.modelHex}")
            return true
        }
        return s.model == saved
    }

    /** 4 раза в секунду: прячем окно, если кейс закрыли (сигнал пропал). */
    private val ticker = object : Runnable {
        override fun run() {
            val limit = (adInterval * 2.5f).toLong().coerceIn(LID_CLOSED_MIN_MS, LID_CLOSED_MAX_MS)
            val silence = SystemClock.elapsedRealtime() - lastSeen
            if (popup.isShowing && !popup.isDemo && silence > limit) {
                Log.i(TAG, "lid closed: silence $silence ms > $limit")
                popup.hide()
            }
            handler.postDelayed(this, 250)
        }
    }

    /** AirPods подключились к телефону — открываем канал управления, отключились — закрываем. */
    private val aclReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            val d = (if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }) ?: return
            val name = runCatching { d.name }.getOrNull() ?: return
            if (!name.contains("AirPods", ignoreCase = true)) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.i(TAG, "AirPods ACL connected")
                    // даём системе поднять A2DP/HFP, потом лезем в L2CAP
                    handler.postDelayed({ Aap.connect(this@PodsService) }, 1_500)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.i(TAG, "AirPods ACL disconnected")
                    Aap.disconnect()
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startScan()
                Intent.ACTION_SCREEN_OFF -> {
                    stopScan()
                    popup.hide(immediate = true)
                }
            }
        }
    }

    // ---------- уведомление ----------

    private fun updateNotification(s: PodsStatus?, connected: Boolean) {
        val key = "${s?.let { batteryLine(this, it) }}|$connected|${Prefs.lockscreen}|${s?.model}"
        if (key == lastNotif) return
        lastNotif = key
        getSystemService(NotificationManager::class.java).notify(Notif.ID_STATUS, Notif.status(this, s, connected))
    }

    /** Настройки уведомлений поменялись — перерисовать карточку. */
    fun refreshNotification() {
        lastNotif = ""
        updateNotification(liveStatus(PodsRepo.mine.value, Aap.battery.value), Aap.connected.value)
    }
}

fun batteryLine(ctx: Context, s: PodsStatus): String {
    fun f(label: Int, v: Int?, ch: Boolean) =
        "${ctx.getString(label)} ${v?.let { "$it%" } ?: "—"}${if (ch) "⚡" else ""}"
    return listOf(
        f(R.string.label_left, s.left, s.leftCharging),
        f(R.string.label_right, s.right, s.rightCharging),
        f(R.string.label_case, s.case, s.caseCharging),
    ).joinToString("  ·  ")
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Prefs.enabled) PodsService.start(ctx)
    }
}
