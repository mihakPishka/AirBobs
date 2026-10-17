package com.lsucu.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Apple Accessory Protocol поверх классического L2CAP (PSM 0x1001).
 * Пакеты — по разбору LibrePods (github.com/kavishdevar/librepods).
 */
object Aap {
    private const val TAG = "PodsAap"
    private const val PSM = 0x1001

    enum class Mode(val code: Int, @StringRes val label: Int) {
        OFF(1, R.string.mode_off),
        ANC(2, R.string.mode_anc),
        TRANSPARENCY(3, R.string.mode_transparency),
        ADAPTIVE(4, R.string.mode_adaptive),
    }

    /** Состояние канала управления — для экрана. */
    enum class Link { IDLE, CONNECTING, CONNECTED, NO_ACL, NOT_PAIRED, CLOSED, ERROR }

    private val HANDSHAKE = hex("00 00 04 00 01 00 02 00 00 00 00 00 00 00 00 00")
    // D7 — как в LibrePods (FF наушники, похоже, не принимают: адаптивный откатывался в «Выкл»)
    private val FEATURES = hex("04 00 04 00 4D 00 D7 00 00 00 00 00 00 00")
    private val NOTIFICATIONS = hex("04 00 04 00 0F 00 FF FF FF FF")
    private val CONTROL = hex("04 00 04 00 09 00")
    private val BATTERY = hex("04 00 04 00 04 00")
    private const val ID_NOISE = 0x0D
    private const val ID_CONVERSATION = 0x28  // 01 = вкл, 02 = выкл
    private const val ID_ADAPTIVE_LEVEL = 0x2E // 0..100
    // перехват нажатий: маска 01 одно, 02 двойное, 04 тройное, 08 удержание (по LibrePods, на Pro 3 не проверено)
    private const val ID_STEM_CONFIG = 0x39
    private val STEM_EVENT = hex("04 00 04 00 19 00")

    enum class Press(val code: Int, @StringRes val label: Int) {
        SINGLE(0x05, R.string.press_single),
        DOUBLE(0x06, R.string.press_double),
        TRIPLE(0x07, R.string.press_triple),
        LONG(0x08, R.string.press_long),
    }

    /** Нажатие на ножку: [left] = true для левого наушника. */
    data class StemPress(val press: Press, val left: Boolean)

    private val _stem = MutableSharedFlow<StemPress>(extraBufferCapacity = 8)
    val stem: SharedFlow<StemPress> = _stem.asSharedFlow()

    private val _stemMask = MutableStateFlow(0)
    val stemMask: StateFlow<Int> = _stemMask.asStateFlow()

    /** Точный заряд из канала управления (шаг 1%). null = нет данных (наушник/кейс не на связи). */
    data class Battery(
        val left: Int?,
        val right: Int?,
        val case: Int?,
        val leftCharging: Boolean,
        val rightCharging: Boolean,
        val caseCharging: Boolean,
    )

    private val _battery = MutableStateFlow<Battery?>(null)
    val battery: StateFlow<Battery?> = _battery.asStateFlow()

    private val _conversation = MutableStateFlow<Boolean?>(null)
    val conversation: StateFlow<Boolean?> = _conversation.asStateFlow()

    private val _adaptiveLevel = MutableStateFlow<Int?>(null)
    val adaptiveLevel: StateFlow<Int?> = _adaptiveLevel.asStateFlow()

    private val _link = MutableStateFlow(Link.IDLE)
    val link: StateFlow<Link> = _link.asStateFlow()

    /** Текст последней ошибки (для Link.ERROR). */
    @Volatile var lastError: String = ""
        private set

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _mode = MutableStateFlow<Mode?>(null)
    val mode: StateFlow<Mode?> = _mode.asStateFlow()

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var busy = false
    @Volatile private var logFile: File? = null

    /** Журнал пакетов в files/aap.log (logcat на Pixel быстро перетирается). */
    private fun log(line: String) {
        Log.d(TAG, line)
        val f = logFile ?: return
        runCatching {
            if (f.length() > 512 * 1024) f.writeText("")
            f.appendText("${stamp.format(Date())} $line\n")
        }
    }

    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun connect(ctx: Context) {
        if (busy || _connected.value) return
        busy = true
        logFile = File(ctx.filesDir, "aap.log")
        thread(name = "aap") {
            try {
                run(ctx)
            } catch (e: Throwable) {
                Log.e(TAG, "aap failed", e)
                log("ошибка: ${e.javaClass.simpleName}: ${e.message}")
                lastError = "${e.javaClass.simpleName}: ${e.message}"
                _link.value = if (e.message?.contains("ACL") == true) Link.NO_ACL else Link.ERROR
            } finally {
                close()
                busy = false
            }
        }
    }

    fun setMode(m: Mode) = control(ID_NOISE, m.code)

    fun setConversation(on: Boolean) = control(ID_CONVERSATION, if (on) 1 else 2)

    fun setAdaptiveLevel(v: Int) = control(ID_ADAPTIVE_LEVEL, v.coerceIn(0, 100))

    /** Какие нажатия наушники отдают нам вместо своей обработки (маска, 0 = все свои). */
    fun setStemIntercept(mask: Int) {
        _stemMask.value = mask
        control(ID_STEM_CONFIG, mask)
    }

    private fun control(id: Int, value: Int) =
        send(CONTROL + byteArrayOf(id.toByte(), value.toByte(), 0, 0, 0))

    fun disconnect() = close()

    @SuppressLint("MissingPermission")
    private fun run(ctx: Context) {
        val device = Devices.target(ctx) ?: run {
            _link.value = Link.NOT_PAIRED
            return
        }
        _link.value = Link.CONNECTING
        val s = createSocket(device)
        socket = s
        Log.i(TAG, "connecting L2CAP psm=0x%04X".format(PSM))
        s.connect()
        log("L2CAP connected")
        out = s.outputStream
        _connected.value = true
        _link.value = Link.CONNECTED

        send(HANDSHAKE)
        Thread.sleep(150)
        send(FEATURES)
        Thread.sleep(150)
        send(NOTIFICATIONS)
        // перехват жестов живёт только в рамках соединения — включаем заново
        Gestures.mask().let { if (it != 0) { Thread.sleep(150); setStemIntercept(it) } }

        val input = s.inputStream
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            onPacket(buf.copyOf(n))
        }
        _link.value = Link.CLOSED
    }

    private fun onPacket(p: ByteArray) {
        log("<- ${p.toHex()}")
        when {
            p.size >= 8 && p.startsWith(CONTROL) -> {
                val v = p[7].toInt() and 0xFF
                when (p[6].toInt() and 0xFF) {
                    ID_NOISE -> _mode.value = Mode.entries.firstOrNull { it.code == v }
                    ID_CONVERSATION -> _conversation.value = v == 1
                    ID_ADAPTIVE_LEVEL -> _adaptiveLevel.value = v
                }
            }
            p.size >= 7 && p.startsWith(BATTERY) -> parseBattery(p)
            p.size >= 8 && p.startsWith(STEM_EVENT) -> {
                val press = Press.entries.firstOrNull { it.code == (p[6].toInt() and 0xFF) }
                val bud = p[7].toInt() and 0xFF
                log("stem: ${press ?: "?"} bud=$bud")
                if (press != null) _stem.tryEmit(StemPress(press, left = bud == 0x01))
            }
        }
    }

    /** [6] = число блоков, дальше по 5 байт: компонент, 01, уровень, статус, 01. */
    private fun parseBattery(p: ByteArray) {
        val n = p[6].toInt() and 0xFF
        var left: Int? = null; var right: Int? = null; var case: Int? = null
        var lc = false; var rc = false; var cc = false
        for (i in 0 until n) {
            val o = 7 + i * 5
            if (o + 3 >= p.size) break
            val level = p[o + 2].toInt() and 0xFF
            val status = p[o + 3].toInt() and 0xFF
            if (status == 0x04 || level > 100) continue // 04 = нет на связи
            val charging = status == 0x01
            when (p[o].toInt() and 0xFF) {
                0x04 -> { left = level; lc = charging }
                0x02 -> { right = level; rc = charging }
                0x08 -> { case = level; cc = charging }
            }
        }
        _battery.value = Battery(left, right, case, lc, rc, cc)
        PodsRepo.touch()
    }

    private fun send(p: ByteArray) {
        val o = out ?: return
        thread {
            try {
                synchronized(this) { o.write(p); o.flush() }
                log("-> ${p.toHex()}")
            } catch (e: Exception) {
                Log.w(TAG, "send failed", e)
            }
        }
    }

    private fun close() {
        runCatching { socket?.close() }
        socket = null
        out = null
        if (_connected.value) _link.value = Link.CLOSED
        _connected.value = false
        _battery.value = null
        _conversation.value = null
        _adaptiveLevel.value = null
        _stemMask.value = 0
    }

    /** Классический L2CAP-сокет — только скрытым API. Пробуем по очереди. */
    private fun createSocket(d: BluetoothDevice): BluetoothSocket {
        // ограничения скрытого API появились в Android 9; на 8.x и так всё доступно
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
        val errors = mutableListOf<String>()
        for (name in listOf("createInsecureL2capSocket", "createL2capSocket")) {
            try {
                val m = BluetoothDevice::class.java.getDeclaredMethod(name, Int::class.javaPrimitiveType)
                m.isAccessible = true
                Log.i(TAG, "using $name")
                return m.invoke(d, PSM) as BluetoothSocket
            } catch (e: Throwable) {
                errors += "$name: ${e.javaClass.simpleName}"
            }
        }
        val ctors = BluetoothSocket::class.java.declaredConstructors.joinToString("\n") { it.toString() }
        Log.e(TAG, "no L2CAP factory; ctors:\n$ctors")
        error("нет L2CAP API (${errors.joinToString()})")
    }
}

private fun hex(s: String): ByteArray =
    s.split(' ').filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

private fun ByteArray.startsWith(p: ByteArray) = size >= p.size && p.indices.all { this[it] == p[it] }
