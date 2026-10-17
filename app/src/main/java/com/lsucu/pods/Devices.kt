package com.lsucu.pods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Спаренные наушники Apple (AirPods / Beats) из системного списка Bluetooth. */
data class AppleDevice(
    val device: BluetoothDevice,
    val name: String,
    val family: Family,
    val connected: Boolean,
    /** Заряд, который сама система знает по HFP (у AirPods — средний по наушникам). */
    val systemBattery: Int?,
)

object Devices {
    private fun isApple(name: String) =
        name.contains("AirPods", ignoreCase = true) || name.contains("Beats", ignoreCase = true)

    @SuppressLint("MissingPermission")
    fun list(ctx: Context): List<AppleDevice> = try {
        if (Build.VERSION.SDK_INT >= 28) HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/")
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .mapNotNull { d ->
                val name = d.name ?: return@mapNotNull null
                if (!isApple(name)) return@mapNotNull null
                AppleDevice(d, name, PodsModel.familyByName(name), isConnected(d), systemBattery(d))
            }
            .sortedWith(compareByDescending<AppleDevice> { it.connected }.thenBy { it.name })
    } catch (_: SecurityException) {
        emptyList()
    }

    /** С какими наушниками работаем: подключённые AirPods, иначе первые спаренные. */
    fun target(ctx: Context): BluetoothDevice? {
        val all = list(ctx).filter { it.name.contains("AirPods", ignoreCase = true) }
        return (all.firstOrNull { it.connected } ?: all.firstOrNull())?.device
    }

    fun anyConnected(ctx: Context) = list(ctx).any { it.connected }

    private fun isConnected(d: BluetoothDevice): Boolean =
        runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)

    private fun systemBattery(d: BluetoothDevice): Int? =
        runCatching { (d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int).takeIf { it in 0..100 } }.getOrNull()
}

/** Имя «моих» AirPods из системы. */
fun podsName(ctx: Context, fallback: String): String =
    Devices.list(ctx).firstOrNull { it.name.contains("AirPods", ignoreCase = true) }?.name ?: fallback
