package com.lsucu.pods

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}

object Prefs {
    private lateinit var sp: SharedPreferences
    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("pods", Context.MODE_PRIVATE)
    }

    /** Модель «моих» наушников; 0 = ещё не запомнили. */
    var model: Int
        get() = sp.getInt("model", 0)
        set(v) = sp.edit().putInt("model", v).apply()

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    var popup: Boolean
        get() = sp.getBoolean("popup", true)
        set(v) = sp.edit().putBoolean("popup", v).apply()

    /** IRK своих AirPods (hex, 16 байт) — по нему узнаём их BLE-адреса среди чужих. */
    var irk: String
        get() = sp.getString("irk", "") ?: ""
        set(v) = sp.edit().putString("irk", v).apply()

    /** Ключ шифрования рекламы (на будущее: точный заряд из BLE). */
    var encKey: String
        get() = sp.getString("encKey", "") ?: ""
        set(v) = sp.edit().putString("encKey", v).apply()

    var lockscreen: Boolean
        get() = sp.getBoolean("lockscreen", true)
        set(v) = sp.edit().putBoolean("lockscreen", v).apply()

    var alertLow: Boolean
        get() = sp.getBoolean("alertLow", true)
        set(v) = sp.edit().putBoolean("alertLow", v).apply()

    var alertFull: Boolean
        get() = sp.getBoolean("alertFull", true)
        set(v) = sp.edit().putBoolean("alertFull", v).apply()

    /** Порог «мало заряда», %. */
    var lowThreshold: Int
        get() = sp.getInt("lowThreshold", 20)
        set(v) = sp.edit().putInt("lowThreshold", v).apply()

    /** "" = как в системе, "en", "ru". */
    var lang: String
        get() = sp.getString("lang", "") ?: ""
        set(v) = sp.edit().putString("lang", v).apply()

    /** 0 = как в системе, 1 = тёмное, 2 = светлое. */
    var appearance: Int
        get() = sp.getInt("appearance", 0)
        set(v) = sp.edit().putInt("appearance", v).apply()

    fun gesture(left: Boolean, press: String): String? =
        sp.getString("g_${if (left) "L" else "R"}_$press", null)

    fun setGesture(left: Boolean, press: String, action: String) =
        sp.edit().putString("g_${if (left) "L" else "R"}_$press", action).apply()

    /** Ближе этого порога сигнал считаем своим (дБм). */
    var minRssi: Int
        get() = sp.getInt("minRssi", -70)
        set(v) = sp.edit().putInt("minRssi", v).apply()
}

/** Общее состояние между сервисом и экраном. */
object PodsRepo {
    private val _mine = MutableStateFlow<PodsStatus?>(null)
    val mine: StateFlow<PodsStatus?> = _mine.asStateFlow()

    /** Все AirPods-подобные устройства рядом (по модели) — для привязки и отладки. */
    private val _nearby = MutableStateFlow<Map<Int, PodsStatus>>(emptyMap())
    val nearby: StateFlow<Map<Int, PodsStatus>> = _nearby.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** Когда последний раз пришли данные о заряде (wall clock) — «Обновлено: 20:42». */
    private val _updatedAt = MutableStateFlow(0L)
    val updatedAt: StateFlow<Long> = _updatedAt.asStateFlow()

    fun touch() { _updatedAt.value = System.currentTimeMillis() }

    fun onMine(s: PodsStatus) { _mine.value = s; touch() }
    fun onAny(s: PodsStatus) {
        val old = _nearby.value[s.model]
        // берём ближайший сигнал; устаревшие (>10 c) заменяем любым
        if (old == null || s.rssi >= old.rssi || s.time - old.time > 10_000) {
            _nearby.value = _nearby.value + (s.model to s)
        }
    }
    fun setScanning(v: Boolean) { _scanning.value = v }
    fun clearNearby() { _nearby.value = emptyMap() }
}

object Perms {
    /** Android 12+: «Устройства поблизости»; Android 8–11: геолокация (без неё BLE-скан пустой). */
    val runtime: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun granted(ctx: Context, p: String) =
        ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun bluetooth(ctx: Context) = if (Build.VERSION.SDK_INT >= 31) {
        granted(ctx, Manifest.permission.BLUETOOTH_SCAN) && granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** На Android 8–11 сканирование BLE работает только при включённой геолокации. */
    val needsLocationService get() = Build.VERSION.SDK_INT < 31

    fun locationOn(ctx: Context): Boolean {
        if (!needsLocationService) return true
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun bluetoothOn(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)
}

/** Сколько BLE-данные считаются свежими: открытый кейс шлёт пакет раз в ~2.5 с. */
const val BLE_FRESH_MS = 30_000L

/**
 * Текущий статус для показа: BLE — только свежий, точный заряд — только пока канал подключён
 * (Aap.battery обнуляется при отключении). null = наушники сейчас не на связи.
 */
fun liveStatus(ble: PodsStatus?, exact: Aap.Battery?, now: Long = SystemClock.elapsedRealtime()): PodsStatus? =
    withExactBattery(ble?.takeIf { now - it.time < BLE_FRESH_MS }, exact)

/** Тикает каждые [periodMs] — чтобы устаревшие данные пропадали сами, без новых событий. */
fun ticker(periodMs: Long) = kotlinx.coroutines.flow.flow {
    while (true) {
        emit(SystemClock.elapsedRealtime())
        kotlinx.coroutines.delay(periodMs)
    }
}

/**
 * Подменяет заряд из BLE (шаг 10%) точным из канала управления, где он есть.
 * Без BLE-данных собирает статус только из канала (raw = "aap").
 */
fun withExactBattery(ble: PodsStatus?, b: Aap.Battery?): PodsStatus? {
    if (b == null) return ble
    val base = ble ?: PodsStatus(
        model = Prefs.model, left = null, right = null, case = null,
        leftCharging = false, rightCharging = false, caseCharging = false,
        statusByte = 0, lidByte = 0, rssi = 0, raw = "aap", time = SystemClock.elapsedRealtime(),
    )
    return base.copy(
        left = b.left ?: base.left,
        right = b.right ?: base.right,
        case = b.case ?: base.case,
        leftCharging = if (b.left != null) b.leftCharging else base.leftCharging,
        rightCharging = if (b.right != null) b.rightCharging else base.rightCharging,
        caseCharging = if (b.case != null) b.caseCharging else base.caseCharging,
    )
}
