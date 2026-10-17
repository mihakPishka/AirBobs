package com.lsucu.pods.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lsucu.pods.Aap
import com.lsucu.pods.AppleDevice
import com.lsucu.pods.Devices
import com.lsucu.pods.Family
import com.lsucu.pods.Part
import com.lsucu.pods.PodsArt
import com.lsucu.pods.PodsModel
import com.lsucu.pods.PodsRepo
import com.lsucu.pods.Prefs
import com.lsucu.pods.R
import com.lsucu.pods.withExactBattery
import kotlinx.coroutines.delay

@Composable
fun DevicesScreen(openHome: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    var devices by remember { mutableStateOf<List<AppleDevice>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) { devices = Devices.list(ctx); delay(3_000) }
    }
    val mine by PodsRepo.mine.collectAsStateWithLifecycle()
    val exact by Aap.battery.collectAsStateWithLifecycle()
    val my = withExactBattery(mine, exact)
    val myTarget = remember(devices) { Devices.target(ctx)?.address }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle(stringResource(R.string.devices_title), Modifier.weight(1f))
            IconButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                Icon(Icons.Filled.Add, stringResource(R.string.pair_new), tint = c.text)
            }
        }
        if (devices.isEmpty()) {
            Text(stringResource(R.string.no_devices), color = c.text2, fontSize = 14.sp)
        }
        devices.forEach { d ->
            val isMine = d.device.address == myTarget
            val family = if (isMine && Prefs.model != 0) PodsModel.of(Prefs.model).family else d.family
            PodsCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = if (isMine) openHome else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        PodsArt(family, Part.PODS, Modifier.size(60.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(d.connected, 7.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (d.connected) R.string.connected else R.string.not_connected),
                                color = c.text2, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (isMine && my != null) {
                                if (family == Family.MAX) {
                                    MiniBattery(my.left ?: my.right)
                                } else {
                                    MiniBattery(my.left); MiniBattery(my.right); MiniBattery(my.case)
                                }
                            } else if (d.connected && d.systemBattery != null) {
                                MiniBattery(d.systemBattery)
                            } else {
                                repeat(if (family == Family.MAX) 1 else 3) { MiniBattery(null) }
                            }
                        }
                    }
                    if (isMine) Chevron()
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Маленькая батарейка с процентом, как в списке на макете. */
@Composable
private fun MiniBattery(value: Int?) {
    val c = LocalPods.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 11.dp)) {
            val bodyW = size.width * 0.86f
            val r = CornerRadius(size.height * 0.25f)
            val st = 1.2.dp.toPx()
            drawRoundRect(c.text2, Offset(st / 2, st / 2), Size(bodyW - st, size.height - st), r, style = Stroke(st))
            drawRoundRect(c.text2, Offset(bodyW + st * 0.5f, size.height * 0.3f), Size(size.width - bodyW - st * 0.5f, size.height * 0.4f))
            if (value != null) {
                val inner = (bodyW - st * 4) * value.coerceIn(0, 100) / 100f
                drawRoundRect(batteryColor(c, value, false), Offset(st * 2, st * 2), Size(inner, size.height - st * 4), r)
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(value?.let { "$it%" } ?: "—", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
