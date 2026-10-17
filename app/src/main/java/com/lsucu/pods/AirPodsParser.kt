package com.lsucu.pods

/**
 * Состояние наушников из BLE-рекламы Apple «Proximity Pairing» (тип 0x07).
 * Заряд приходит шагами по 10%; null = неизвестно (наушник не в кейсе / не виден).
 */
data class PodsStatus(
    val model: Int,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val statusByte: Int,
    val lidByte: Int,
    val rssi: Int,
    val raw: String,
    val time: Long,
) {
    /** Байт [8]: младшие биты — счётчик открытий, бит 0x08 — крышка закрыта (проверено на Pro 3). */
    val lidClosed: Boolean get() = lidByte and 0x08 != 0
    val modelName: String get() = AirPodsParser.modelName(model)
    val modelHex: String get() = "0x%04X".format(model)
}

object AirPodsParser {
    const val APPLE_ID = 0x004C

    /**
     * [data] — manufacturer data без company id, т.е. начинается с 0x07 0x19.
     * Раскладка (по разбору OpenPods / furiousmac continuity):
     *  [3..4] модель (little-endian, 0x20xx), [5] статус (бит 0x20 — какой наушник «основной»),
     *  [6] заряд наушников (два ниббла), [7] ниббл зарядки + ниббл кейса, [8] крышка/счётчик.
     */
    fun parse(data: ByteArray, rssi: Int, time: Long): PodsStatus? {
        if (data.size < 10 || data[0].toInt() != 0x07) return null
        fun u(i: Int) = data[i].toInt() and 0xFF

        val model = (u(4) shl 8) or u(3)
        if (model shr 8 != 0x20) return null

        val status = u(5)
        val flip = (status and 0x20) == 0
        val hi = u(6) shr 4
        val lo = u(6) and 0x0F
        val charge = u(7) shr 4
        val case = u(7) and 0x0F

        return PodsStatus(
            model = model,
            left = pct(if (flip) hi else lo),
            right = pct(if (flip) lo else hi),
            case = pct(case),
            leftCharging = charge and (if (flip) 0b0010 else 0b0001) != 0,
            rightCharging = charge and (if (flip) 0b0001 else 0b0010) != 0,
            caseCharging = charge and 0b0100 != 0,
            statusByte = status,
            lidByte = u(8),
            rssi = rssi,
            raw = data.joinToString("") { "%02X".format(it) },
            time = time,
        )
    }

    private fun pct(n: Int): Int? = if (n in 0..10) n * 10 else null

    fun modelName(model: Int): String = PodsModel.of(model).title
}
