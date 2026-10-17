package com.lsucu.pods.ui

import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lsucu.pods.Aap
import com.lsucu.pods.Charge
import com.lsucu.pods.Devices
import com.lsucu.pods.Family
import com.lsucu.pods.Part
import com.lsucu.pods.Perms
import com.lsucu.pods.PodsArt
import com.lsucu.pods.PodsHero
import com.lsucu.pods.PodsModel
import com.lsucu.pods.PodsRepo
import com.lsucu.pods.PodsService
import com.lsucu.pods.Prefs
import com.lsucu.pods.R
import com.lsucu.pods.podsName
import com.lsucu.pods.withExactBattery
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/** Все ли разрешения выданы (пересчитывается при возврате на экран). */
/** Растёт при каждом возврате на экран — чтобы перепроверять системные настройки. */
@Composable
fun rememberResumes(): Int {
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    return resumes
}

@Composable
fun rememberPermsOk(): Boolean {
    val ctx = LocalContext.current
    val resumes = rememberResumes()
    return remember(resumes) {
        Perms.runtime.all { Perms.granted(ctx, it) } &&
            Settings.canDrawOverlays(ctx) &&
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
}

@Composable
fun HomeScreen(
    openGestures: () -> Unit,
    openPermissions: () -> Unit,
    openSettings: () -> Unit,
    openCharging: () -> Unit,
) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    val permsOk = rememberPermsOk()
    val bt = Perms.bluetooth(ctx)

    LaunchedEffect(bt) {
        if (bt) {
            if (Prefs.enabled) PodsService.start(ctx)
            Aap.connect(ctx)
        }
    }

    val mine by PodsRepo.mine.collectAsStateWithLifecycle()
    val exact by Aap.battery.collectAsStateWithLifecycle()
    val aapConnected by Aap.connected.collectAsStateWithLifecycle()
    val updatedAt by PodsRepo.updatedAt.collectAsStateWithLifecycle()

    // системное «подключено» — опрашиваем раз в 3 с
    var btConnected by remember { mutableStateOf(false) }
    LaunchedEffect(bt) {
        while (bt) { btConnected = Devices.anyConnected(ctx); delay(3_000) }
    }
    val isConnected = aapConnected || btConnected

    val s = withExactBattery(mine, exact)
    val model = PodsModel.of(mine?.model ?: Prefs.model)
    val name = remember(model, isConnected) { podsName(ctx, model.title) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ---- шапка ----
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(name, color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isConnected)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (isConnected) R.string.connected else R.string.not_connected),
                        color = c.text2, fontSize = 14.sp,
                    )
                }
            }
            IconButton(onClick = openSettings) { Icon(Icons.Outlined.Settings, null, tint = c.text) }
        }

        if (!permsOk) {
            Spacer(Modifier.height(14.dp))
            PodsCard(Modifier.fillMaxWidth(), onClick = openPermissions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Glyph.SHIELD, c.yellow)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.perm_banner), color = c.text, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.perm_banner_sub), color = c.text2, fontSize = 13.sp)
                    }
                    Chevron()
                }
            }
        }

        val resumes = rememberResumes()
        var btOn by remember { mutableStateOf(true) }
        var locOn by remember { mutableStateOf(true) }
        LaunchedEffect(resumes) {
            while (true) { btOn = Perms.bluetoothOn(ctx); locOn = Perms.locationOn(ctx); delay(3_000) }
        }
        if (!btOn) {
            Spacer(Modifier.height(14.dp))
            Banner(Glyph.HEADPHONES, stringResource(R.string.bt_off), stringResource(R.string.bt_off_sub)) {
                ctx.startActivity(android.content.Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }
        if (!locOn) {
            Spacer(Modifier.height(14.dp))
            Banner(Glyph.GLOBE, stringResource(R.string.loc_off), stringResource(R.string.loc_off_sub)) {
                ctx.startActivity(android.content.Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }

        // ---- картинка ----
        Spacer(Modifier.height(18.dp))
        PodsHero(model.family, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(model.title, Modifier.fillMaxWidth(), color = c.text, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (updatedAt > 0) {
            Text(
                stringResource(R.string.updated, DateFormat.getTimeFormat(ctx).format(Date(updatedAt))),
                Modifier.fillMaxWidth().padding(top = 2.dp),
                color = c.text2, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }

        // ---- заряд ----
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(CardShape).clickable(onClick = openCharging),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (model.family == Family.MAX) {
                BatteryTile(Modifier.weight(1f), stringResource(R.string.battery), s?.left ?: s?.right, s?.leftCharging == true) {
                    PodsArt(Family.MAX, Part.POD, Modifier.size(46.dp))
                }
            } else {
                BatteryTile(Modifier.weight(1f), stringResource(R.string.label_left), s?.left, s?.leftCharging == true) {
                    PodsArt(model.family, Part.POD, Modifier.graphicsLayer(scaleX = -1f).size(width = 34.dp, height = 44.dp))
                }
                BatteryTile(Modifier.weight(1f), stringResource(R.string.label_right), s?.right, s?.rightCharging == true) {
                    PodsArt(model.family, Part.POD, Modifier.size(width = 34.dp, height = 44.dp))
                }
                BatteryTile(Modifier.weight(1f), stringResource(R.string.label_case), s?.case, s?.caseCharging == true) {
                    PodsArt(model.family, Part.CASE, Modifier.size(width = 46.dp, height = 38.dp))
                }
            }
        }
        if (s == null) {
            Text(
                stringResource(if (Prefs.model == 0) R.string.hint_learn else R.string.hint_open),
                Modifier.fillMaxWidth().padding(top = 12.dp),
                color = c.text2, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
        }

        // ---- зарядка ----
        if (s != null && Charge.anyCharging(s)) {
            val eta = remember(s) { Charge.eta(s) }
            Spacer(Modifier.height(12.dp))
            PodsCard(Modifier.fillMaxWidth(), onClick = openCharging, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(Glyph.BOLT, c.green, 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (eta != null && eta.minutes > 0) stringResource(R.string.charging_banner, etaText(ctx, eta))
                        else stringResource(if (eta?.minutes == 0) R.string.charged else R.string.charging_now),
                        Modifier.weight(1f), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    )
                    Chevron()
                }
            }
        }

        // ---- управление ----
        if (model.anc) {
            Spacer(Modifier.height(14.dp))
            NoiseCard(model)
        }
        if (model.stem) {
            Spacer(Modifier.height(14.dp))
            PodsCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                SettingsItem(
                    stringResource(R.string.buttons),
                    stringResource(R.string.buttons_sub),
                    icon = { LineIcon(Glyph.TOUCH, c.text) },
                    divider = false,
                    onClick = openGestures,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Banner(icon: Glyph, title: String, sub: String, onClick: () -> Unit) {
    val c = LocalPods.current
    PodsCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(icon, c.yellow)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = c.text, fontWeight = FontWeight.SemiBold)
                Text(sub, color = c.text2, fontSize = 13.sp)
            }
            Chevron()
        }
    }
}

@Composable
private fun BatteryTile(
    modifier: Modifier,
    label: String,
    value: Int?,
    charging: Boolean,
    icon: @Composable () -> Unit,
) {
    val c = LocalPods.current
    PodsCard(modifier, padding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BatteryRing(value, charging, ringSize = 74.dp, stroke = 6.dp) { icon() }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) {
                    LineIcon(Glyph.BOLT, c.green, 13.dp)
                    Spacer(Modifier.width(2.dp))
                }
                Text(label, color = if (value != null) batteryColor(c, value, charging) else c.text2,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(2.dp))
            Text(value?.let { "$it%" } ?: "—", color = c.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoiseCard(model: PodsModel) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    val link by Aap.link.collectAsStateWithLifecycle()
    val connected by Aap.connected.collectAsStateWithLifecycle()
    val mode by Aap.mode.collectAsStateWithLifecycle()
    val conversation by Aap.conversation.collectAsStateWithLifecycle()
    val level by Aap.adaptiveLevel.collectAsStateWithLifecycle()

    PodsCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.noise_control), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        if (!connected) {
            Text(
                when (link) {
                    Aap.Link.CONNECTING -> stringResource(R.string.link_connecting)
                    Aap.Link.NO_ACL -> stringResource(R.string.link_no_acl)
                    Aap.Link.NOT_PAIRED -> stringResource(R.string.link_not_paired)
                    Aap.Link.CLOSED -> stringResource(R.string.link_closed)
                    Aap.Link.ERROR -> stringResource(R.string.link_error, Aap.lastError)
                    else -> stringResource(R.string.link_idle)
                },
                color = c.text2, fontSize = 14.sp,
            )
            if (link != Aap.Link.CONNECTING) {
                TextButton(onClick = { Aap.connect(ctx) }) { Text(stringResource(R.string.connect)) }
            }
            return@PodsCard
        }

        val modes = buildList {
            add(Aap.Mode.OFF); add(Aap.Mode.ANC); add(Aap.Mode.TRANSPARENCY)
            if (model.adaptive) add(Aap.Mode.ADAPTIVE)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.track.copy(alpha = 0.5f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            modes.forEach { m ->
                val sel = m == mode
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) c.accent else Color.Transparent)
                        .clickable { Aap.setMode(m) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ModeIcon(m, if (sel) Color.White else c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(m.label), color = if (sel) Color.White else c.text,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        if (model.conversation) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Glyph.CHAT, c.text)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.conversation), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.conversation_sub), color = c.text2, fontSize = 12.sp)
                }
                Switch(
                    checked = conversation == true,
                    onCheckedChange = { Aap.setConversation(it) },
                    enabled = conversation != null,
                )
            }
        }

        if (model.adaptive) {
            Spacer(Modifier.height(14.dp))
            // локальное положение; в наушники шлём только по отпусканию
            var slider by remember { mutableFloatStateOf(50f) }
            var dragging by remember { mutableStateOf(false) }
            LaunchedEffect(level) { if (!dragging) level?.let { slider = it.toFloat() } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(Glyph.WAVES, c.text)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.adaptive_level), Modifier.weight(1f), color = c.text,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(slider.roundToInt().toString(), color = c.text2, fontSize = 14.sp)
            }
            Slider(
                value = slider,
                onValueChange = { dragging = true; slider = it },
                onValueChangeFinished = { dragging = false; Aap.setAdaptiveLevel(slider.roundToInt()) },
                valueRange = 0f..100f,
                enabled = level != null,
            )
            if (mode != Aap.Mode.ADAPTIVE) {
                Text(stringResource(R.string.adaptive_hint), color = c.text2, fontSize = 12.sp)
            }
        }
    }
}

/** Иконки режимов: выкл — пустой круг, шумодав — закрашенный, прозрачность — пунктир, адаптивный — половина. */
@Composable
private fun ModeIcon(m: Aap.Mode, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val r = size.minDimension * 0.40f
        val st = Stroke(width = size.minDimension * 0.09f)
        val center = Offset(size.width / 2, size.height / 2)
        when (m) {
            Aap.Mode.OFF -> {
                drawCircle(tint, r, center, style = st)
                drawLine(tint, Offset(center.x - r * 0.7f, center.y + r * 0.7f), Offset(center.x + r * 0.7f, center.y - r * 0.7f), st.width)
            }
            Aap.Mode.ANC -> {
                drawCircle(tint, r, center, style = st)
                drawCircle(tint, r * 0.55f, center)
            }
            Aap.Mode.TRANSPARENCY -> {
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    drawCircle(tint, size.minDimension * 0.06f,
                        Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()))
                }
                drawCircle(tint, r * 0.35f, center, style = st)
            }
            Aap.Mode.ADAPTIVE -> {
                drawCircle(tint, r, center, style = st)
                drawArc(tint, 90f, 180f, true, Offset(center.x - r * 0.6f, center.y - r * 0.6f),
                    androidx.compose.ui.geometry.Size(r * 1.2f, r * 1.2f))
            }
        }
    }
}
