package com.lsucu.pods.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lsucu.pods.Aap
import com.lsucu.pods.Action
import com.lsucu.pods.AirPodsParser
import com.lsucu.pods.Gestures
import com.lsucu.pods.Perms
import com.lsucu.pods.PodsRepo
import com.lsucu.pods.PodsService
import com.lsucu.pods.Prefs
import com.lsucu.pods.R
import com.lsucu.pods.Sub
import com.lsucu.pods.batteryLine
import kotlinx.coroutines.delay

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------- Настройки ----------------

@Composable
fun SettingsScreen(open: (Sub) -> Unit, onAppearance: (Int) -> Unit, onLanguage: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    var popup by remember { mutableStateOf(Prefs.popup) }
    var enabled by remember { mutableStateOf(Prefs.enabled) }
    var appearance by remember { mutableIntStateOf(Prefs.appearance) }
    val appearanceNames = listOf(
        stringResource(R.string.appearance_system),
        stringResource(R.string.appearance_dark),
        stringResource(R.string.appearance_light),
    )
    val langNames = listOf(stringResource(R.string.language_system), "English", "Русский")
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    ScreenColumn {
        ScreenTitle(stringResource(R.string.tab_settings), Modifier.padding(top = 12.dp, bottom = 10.dp))

        SectionLabel(stringResource(R.string.section_general))
        SettingsGroup {
            SettingsItem(
                stringResource(R.string.notifications), stringResource(R.string.notifications_sub),
                icon = { LineIcon(Glyph.BELL) },
                onClick = { open(Sub.NOTIFICATIONS) },
            )
            SettingsItem(
                stringResource(R.string.widget), stringResource(R.string.widget_sub),
                icon = { LineIcon(Glyph.HEADPHONES) },
                onClick = {
                    val mgr = ctx.getSystemService(android.appwidget.AppWidgetManager::class.java)
                    if (mgr.isRequestPinAppWidgetSupported) {
                        mgr.requestPinAppWidget(
                            android.content.ComponentName(ctx, com.lsucu.pods.BatteryWidget::class.java), null, null,
                        )
                    }
                },
            )
            SettingsItem(
                stringResource(R.string.popup), stringResource(R.string.popup_sub),
                icon = { LineIcon(Glyph.WINDOW) },
                trailing = { Switch(popup, { Prefs.popup = it; popup = it }) },
            )
            SettingsItem(
                stringResource(R.string.background), stringResource(R.string.background_sub),
                icon = { LineIcon(Glyph.POWER) },
                trailing = {
                    Switch(enabled, {
                        Prefs.enabled = it; enabled = it
                        if (it) PodsService.start(ctx) else PodsService.stop(ctx)
                    })
                },
            )
            Choice(
                title = stringResource(R.string.appearance),
                icon = Glyph.MOON,
                options = appearanceNames,
                selected = appearance,
            ) { appearance = it; onAppearance(it) }
            Choice(
                title = stringResource(R.string.language),
                icon = Glyph.GLOBE,
                options = langNames,
                selected = Lang.options.indexOf(Prefs.lang).coerceAtLeast(0),
            ) { onLanguage(Lang.options[it]) }
            SettingsItem(
                stringResource(R.string.permissions),
                icon = { LineIcon(Glyph.SHIELD) },
                divider = false,
                onClick = { open(Sub.PERMISSIONS) },
            )
        }

        SectionLabel(stringResource(R.string.section_headphones), Modifier.padding(top = 12.dp))
        SettingsGroup {
            SettingsItem(
                stringResource(R.string.my_airpods),
                if (Prefs.model == 0) stringResource(R.string.not_remembered) else AirPodsParser.modelName(Prefs.model),
                icon = { LineIcon(Glyph.HEADPHONES) },
                onClick = { open(Sub.HEADPHONES) },
            )
            SettingsItem(
                stringResource(R.string.test_popup), stringResource(R.string.test_popup_sub),
                icon = { LineIcon(Glyph.WINDOW) },
                divider = false,
                onClick = {
                    if (Perms.bluetooth(ctx) && Settings.canDrawOverlays(ctx)) {
                        ctx.startForegroundService(Intent(ctx, PodsService::class.java).setAction("test_popup"))
                    } else {
                        open(Sub.PERMISSIONS)
                    }
                },
            )
        }

        SectionLabel(stringResource(R.string.section_about), Modifier.padding(top = 12.dp))
        SettingsGroup {
            SettingsItem(
                stringResource(R.string.version), version,
                icon = { LineIcon(Glyph.INFO) },
                divider = false,
            )
        }
        Text(
            "Pods", Modifier.fillMaxWidth().padding(top = 16.dp),
            color = c.text2, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
    }
}

/** Строка настроек с выпадающим выбором. */
@Composable
private fun Choice(title: String, icon: Glyph, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingsItem(title, options[selected], icon = { LineIcon(icon) }, onClick = { open = true })
        DropdownMenu(open, { open = false }, Modifier.widthIn(min = 200.dp)) {
            options.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { open = false; onPick(i) },
                    trailingIcon = if (i == selected) ({ Icon(Icons.Filled.Check, null) }) else null,
                )
            }
        }
    }
}

// ---------------- Разрешения ----------------

@SuppressLint("BatteryLife")
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        resumes++
        if (Perms.bluetooth(ctx) && Prefs.enabled) PodsService.start(ctx)
    }
    val bt = remember(resumes) { Perms.bluetooth(ctx) }
    val notif = remember(resumes) { Perms.runtime.all { Perms.granted(ctx, it) } }
    val overlay = remember(resumes) { Settings.canDrawOverlays(ctx) }
    val battery = remember(resumes) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    val pkg = Uri.parse("package:${ctx.packageName}")

    ScreenColumn {
        SubTopBar(stringResource(R.string.permissions), onBack)
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            if (Perms.needsLocationService) {
                PermItem(R.string.perm_location, R.string.perm_location_sub, Glyph.GLOBE, bt) { launcher.launch(Perms.runtime) }
                val locOn = remember(resumes) { Perms.locationOn(ctx) }
                PermItem(R.string.loc_service, R.string.loc_service_sub, Glyph.GLOBE, locOn, button = R.string.turn_on) {
                    ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            } else {
                PermItem(R.string.perm_bt, R.string.perm_bt_sub, Glyph.HEADPHONES, bt) { launcher.launch(Perms.runtime) }
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                PermItem(R.string.perm_notif, R.string.perm_notif_sub, Glyph.BELL, notif) { launcher.launch(Perms.runtime) }
            }
            PermItem(R.string.perm_overlay, R.string.perm_overlay_sub, Glyph.WINDOW, overlay) {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
            }
            PermItem(R.string.perm_battery, R.string.perm_battery_sub, Glyph.POWER, battery, divider = false) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            }
        }
    }
}

@Composable
private fun PermItem(
    title: Int,
    sub: Int,
    icon: Glyph,
    ok: Boolean,
    divider: Boolean = true,
    button: Int = R.string.allow,
    onGrant: () -> Unit,
) {
    val c = LocalPods.current
    SettingsItem(
        stringResource(title), stringResource(sub),
        icon = { LineIcon(icon) },
        divider = divider,
        trailing = {
            if (ok) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = c.green)
                }
            } else {
                Button(onClick = onGrant) { Text(stringResource(button)) }
            }
        },
    )
}

// ---------------- Мои наушники (привязка и отладка) ----------------

@Composable
fun HeadphonesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    var model by remember { mutableIntStateOf(Prefs.model) }
    val mine by PodsRepo.mine.collectAsStateWithLifecycle()
    val nearby by PodsRepo.nearby.collectAsStateWithLifecycle()
    val scanning by PodsRepo.scanning.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = SystemClock.elapsedRealtime() } }
    LaunchedEffect(mine?.model) { if (model == 0) model = Prefs.model }

    ScreenColumn {
        SubTopBar(stringResource(R.string.my_airpods), onBack)
        Spacer(Modifier.height(8.dp))
        PodsCard(Modifier.fillMaxWidth()) {
            Text(
                if (model == 0) stringResource(R.string.not_remembered)
                else "${AirPodsParser.modelName(model)}  ·  ${"0x%04X".format(model)}",
                color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.model_hint), color = c.text2, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "● " + stringResource(if (scanning) R.string.scanning else R.string.not_scanning),
                color = if (scanning) c.green else c.text2, fontSize = 12.sp,
            )
            if (model != 0) {
                TextButton(onClick = { Prefs.model = 0; model = 0; PodsRepo.clearNearby() }) {
                    Text(stringResource(R.string.forget))
                }
            }
        }

        if (nearby.isNotEmpty()) {
            SectionLabel(stringResource(R.string.nearby_list), Modifier.padding(top = 12.dp))
            PodsCard(Modifier.fillMaxWidth()) {
                nearby.values.sortedByDescending { it.rssi }.forEach { s ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.modelName} ${s.modelHex} · ${s.rssi} dBm", color = c.text, fontSize = 14.sp)
                            Text(batteryLine(ctx, s), color = c.text2, fontSize = 12.sp)
                        }
                        if (s.model != model) {
                            OutlinedButton(onClick = { Prefs.model = s.model; model = s.model }) {
                                Text(stringResource(R.string.mine))
                            }
                        }
                    }
                }
            }
        }

        mine?.let { s ->
            SectionLabel(stringResource(R.string.debug), Modifier.padding(top = 12.dp))
            PodsCard(Modifier.fillMaxWidth()) {
                val ago = (now - s.time) / 1000
                Text(
                    "BLE ${ago}s · ${s.rssi} dBm · status=%02X lid=%02X\n%s".format(
                        s.statusByte, s.lidByte, s.raw.chunked(2).joinToString(" "),
                    ),
                    color = c.text2, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                )
            }
        }
    }
}

// ---------------- Кнопки ----------------

@Composable
fun GesturesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    var version by remember { mutableIntStateOf(0) }
    var lastPress by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Aap.stem.collect {
            lastPress = "${ctx.getString(if (it.left) R.string.left_pod else R.string.right_pod)}: " +
                "${ctx.getString(it.press.label)} → ${ctx.getString(Gestures.get(it.left, it.press).label)}"
        }
    }

    ScreenColumn {
        SubTopBar(stringResource(R.string.buttons), onBack)
        Text(stringResource(R.string.gestures_sub), color = c.text2, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        lastPress?.let {
            Text(stringResource(R.string.last_press, it), color = c.accent, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
        listOf(true to R.string.left_pod, false to R.string.right_pod).forEach { (left, title) ->
            SectionLabel(stringResource(title), Modifier.padding(top = 8.dp))
            SettingsGroup {
                Aap.Press.entries.forEachIndexed { i, press ->
                    key(version, left, press) {
                        val current = Gestures.get(left, press)
                        var open by remember { mutableStateOf(false) }
                        Box {
                            SettingsItem(
                                stringResource(press.label),
                                stringResource(current.label),
                                divider = i < Aap.Press.entries.lastIndex,
                                onClick = { open = true },
                            )
                            DropdownMenu(open, { open = false }) {
                                Action.entries.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(a.label)) },
                                        onClick = {
                                            open = false
                                            Gestures.set(left, press, a)
                                            version++
                                        },
                                        trailingIcon = if (a == current) ({ Icon(Icons.Filled.Check, null) }) else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Уведомления ----------------

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var low by remember { mutableStateOf(Prefs.alertLow) }
    var full by remember { mutableStateOf(Prefs.alertFull) }
    var lock by remember { mutableStateOf(Prefs.lockscreen) }
    var threshold by remember { mutableIntStateOf(Prefs.lowThreshold) }
    val thresholds = listOf(10, 20, 30)
    // карточку в шторке перерисовывает сервис
    val refresh = {
        if (Perms.bluetooth(ctx)) {
            ctx.startForegroundService(Intent(ctx, PodsService::class.java).setAction("refresh"))
        }
    }

    ScreenColumn {
        SubTopBar(stringResource(R.string.notifications), onBack)
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            SettingsItem(
                stringResource(R.string.alert_low), stringResource(R.string.alert_low_sub, threshold),
                icon = { LineIcon(Glyph.BELL) },
                trailing = { Switch(low, { Prefs.alertLow = it; low = it }) },
            )
            Choice(
                title = stringResource(R.string.threshold),
                icon = Glyph.POWER,
                options = thresholds.map { "$it%" },
                selected = thresholds.indexOf(threshold).coerceAtLeast(0),
            ) { Prefs.lowThreshold = thresholds[it]; threshold = thresholds[it] }
            SettingsItem(
                stringResource(R.string.alert_full), stringResource(R.string.alert_full_sub),
                icon = { LineIcon(Glyph.BOLT) },
                trailing = { Switch(full, { Prefs.alertFull = it; full = it }) },
            )
            SettingsItem(
                stringResource(R.string.lockscreen), stringResource(R.string.lockscreen_sub),
                icon = { LineIcon(Glyph.SHIELD) },
                trailing = { Switch(lock, { Prefs.lockscreen = it; lock = it; refresh() }) },
            )
            SettingsItem(
                stringResource(R.string.system_notif),
                icon = { LineIcon(Glyph.INFO) },
                divider = false,
                onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                },
            )
        }
    }
}
