package com.lsucu.pods

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsucu.pods.ui.ChargingScreen
import com.lsucu.pods.ui.DevicesScreen
import com.lsucu.pods.ui.NotificationsScreen
import com.lsucu.pods.ui.GesturesScreen
import com.lsucu.pods.ui.Glyph
import com.lsucu.pods.ui.HeadphonesScreen
import com.lsucu.pods.ui.HomeScreen
import com.lsucu.pods.ui.Lang
import com.lsucu.pods.ui.LineIcon
import com.lsucu.pods.ui.LocalPods
import com.lsucu.pods.ui.PermissionsScreen
import com.lsucu.pods.ui.PodsTheme
import com.lsucu.pods.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Lang.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var appearance by remember { mutableIntStateOf(Prefs.appearance) }
            val dark = when (appearance) {
                1 -> true
                2 -> false
                else -> isSystemInDarkTheme()
            }
            // цвет иконок статус-бара под тему
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PodsTheme(dark) {
                App(
                    onAppearance = { Prefs.appearance = it; appearance = it },
                    onLanguage = {
                        Prefs.lang = it
                        // сервис держит строки уведомления — перезапускаем его на новом языке
                        if (Prefs.enabled) { PodsService.stop(this); PodsService.start(this) }
                        recreate()
                    },
                )
            }
        }
    }
}

enum class Tab { HOME, DEVICES, SETTINGS }
enum class Sub { GESTURES, PERMISSIONS, HEADPHONES, CHARGING, NOTIFICATIONS }

@Composable
private fun App(onAppearance: (Int) -> Unit, onLanguage: (String) -> Unit) {
    val c = LocalPods.current
    var tab by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
    var sub by rememberSaveable { mutableIntStateOf(-1) }
    BackHandler(sub >= 0) { sub = -1 }
    BackHandler(sub < 0 && tab != Tab.HOME.ordinal) { tab = Tab.HOME.ordinal }
    val open: (Sub) -> Unit = { sub = it.ordinal }
    val back = { sub = -1 }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bg, c.bg))),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                // вкладки сменяются затуханием, вложенные экраны въезжают справа и уезжают обратно
                AnimatedContent(
                    targetState = sub to tab,
                    transitionSpec = {
                        val (fromSub, _) = initialState
                        val (toSub, _) = targetState
                        when {
                            fromSub < 0 && toSub >= 0 ->
                                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(320)) { -it / 8 } + fadeOut(tween(200)))
                            fromSub >= 0 && toSub < 0 ->
                                (slideInHorizontally(tween(320)) { -it / 8 } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(320)) { it / 3 } + fadeOut(tween(200)))
                            // новый экран проявляется после того, как старый ушёл — без «двойной экспозиции»
                            else -> fadeIn(tween(200, delayMillis = 110)) togetherWith fadeOut(tween(110))
                        }
                    },
                    label = "screens",
                ) { (shownSub, shownTab) ->
                when {
                    shownSub == Sub.GESTURES.ordinal -> GesturesScreen(back)
                    shownSub == Sub.PERMISSIONS.ordinal -> PermissionsScreen(back)
                    shownSub == Sub.HEADPHONES.ordinal -> HeadphonesScreen(back)
                    shownSub == Sub.CHARGING.ordinal -> ChargingScreen(back)
                    shownSub == Sub.NOTIFICATIONS.ordinal -> NotificationsScreen(back)
                    shownTab == Tab.HOME.ordinal -> HomeScreen(
                        openGestures = { open(Sub.GESTURES) },
                        openPermissions = { open(Sub.PERMISSIONS) },
                        openSettings = { tab = Tab.SETTINGS.ordinal },
                        openCharging = { open(Sub.CHARGING) },
                    )
                    shownTab == Tab.DEVICES.ordinal -> DevicesScreen(openHome = { tab = Tab.HOME.ordinal })
                    else -> SettingsScreen(
                        open = open,
                        onAppearance = onAppearance,
                        onLanguage = onLanguage,
                    )
                }
                }
            }
            // нижнее меню плавно уезжает вниз на вложенных экранах
            AnimatedVisibility(sub < 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                BottomBar(Tab.entries[tab]) { tab = it.ordinal }
            }
            if (sub >= 0) Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    val c = LocalPods.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(c.nav),
    ) {
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BarItem(stringResource(R.string.tab_home), current == Tab.HOME, { onSelect(Tab.HOME) }) { tint ->
                Icon(Icons.Outlined.Home, null, tint = tint)
            }
            BarItem(stringResource(R.string.tab_devices), current == Tab.DEVICES, { onSelect(Tab.DEVICES) }) { tint ->
                LineIcon(Glyph.HEADPHONES, tint, 24.dp)
            }
            BarItem(stringResource(R.string.tab_settings), current == Tab.SETTINGS, { onSelect(Tab.SETTINGS) }) { tint ->
                Icon(Icons.Outlined.Settings, null, tint = tint)
            }
        }
    }
}

@Composable
private fun BarItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val c = LocalPods.current
    val tint by animateColorAsState(if (selected) c.accent else c.text2, tween(250), label = "tabTint")
    val scale by animateFloatAsState(if (selected) 1.08f else 1f, spring(dampingRatio = 0.5f), label = "tabScale")
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(tint)
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
