package com.lsucu.pods

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * «Это мои AirPods?» — по ключу IRK, который AirPods отдают по каналу управления.
 *
 * В BLE-рекламе AirPods используют случайный резолвируемый адрес (RPA): старшие 3 байта — prand,
 * младшие 3 — hash = ah(IRK, prand). Совпал hash — адрес сгенерирован нашими наушниками.
 * Чужие AirPods той же модели этот тест не пройдут.
 */
object Identity {
    private const val TAG = "PodsId"

    fun hasKeys() = Prefs.irk.length == 32

    private val cache = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 64
    }

    /** null — ключа нет, проверить нельзя. */
    @Synchronized
    fun isMine(address: String): Boolean? {
        if (!hasKeys()) return null
        cache[address]?.let { return it }
        val irk = Prefs.irk.hexToBytes()
        val ok = runCatching { resolves(address, irk) }.getOrDefault(false)
        cache[address] = ok
        if (ok) Log.i(TAG, "RPA $address — наши AirPods")
        return ok
    }

    @Synchronized
    fun forget() {
        cache.clear()
        Prefs.irk = ""
        Prefs.encKey = ""
    }

    /** Ключи пришли из канала управления — запоминаем. */
    @Synchronized
    fun store(irk: ByteArray?, enc: ByteArray?) {
        if (irk != null && irk.size == 16) Prefs.irk = irk.hexString()
        if (enc != null && enc.size == 16) Prefs.encKey = enc.hexString()
        cache.clear()
        Log.i(TAG, "keys stored: irk=${irk != null} enc=${enc != null}")
    }

    /**
     * ah(k, r) = e(k, 0…0 || r) mod 2^24 (Bluetooth Core, Vol 3 Part H, 2.2.2).
     * Порядок байт ключа из AAP не задокументирован — проверяем оба варианта.
     */
    private fun resolves(address: String, irk: ByteArray): Boolean {
        val a = address.split(":").map { it.toInt(16).toByte() }.toByteArray() // MSB первым
        if (a.size != 6) return false
        if ((a[0].toInt() and 0xC0) != 0x40) return false // не резолвируемый случайный адрес
        val prand = a.copyOfRange(0, 3)
        val hash = a.copyOfRange(3, 6)
        return ah(irk, prand).contentEquals(hash) || ah(irk.reversedArray(), prand).contentEquals(hash)
    }

    private fun ah(key: ByteArray, prand: ByteArray): ByteArray {
        val block = ByteArray(16)
        prand.copyInto(block, 13)
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(block).copyOfRange(13, 16)
    }
}

fun ByteArray.hexString() = joinToString("") { "%02X".format(it) }

fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
