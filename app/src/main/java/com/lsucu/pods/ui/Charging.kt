package com.lsucu.pods.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lsucu.pods.Aap
import com.lsucu.pods.Charge
import com.lsucu.pods.Family
import com.lsucu.pods.Part
import com.lsucu.pods.PodsArt
import com.lsucu.pods.PodsModel
import com.lsucu.pods.PodsRepo
import com.lsucu.pods.Prefs
import com.lsucu.pods.R
import com.lsucu.pods.withExactBattery
import kotlinx.coroutines.delay

/** «~ 20 мин» / «≈ 1 ч 10 мин» (≈ — пока по типичной скорости). */
fun etaText(ctx: Context, eta: Charge.Eta): String {
    val m = eta.minutes
    val t = if (m < 60) ctx.getString(R.string.minutes, m) else ctx.getString(R.string.hours, m / 60, m % 60)
    return (if (eta.measured) "~ " else "≈ ") + t
}

@Composable
fun ChargingScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = LocalPods.current
    val mine by PodsRepo.mine.collectAsStateWithLifecycle()
    val exact by Aap.battery.collectAsStateWithLifecycle()
    // оценка меняется со временем — пересчитываем раз в 20 с
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); tick++ } }

    val s = withExactBattery(mine, exact)
    val model = PodsModel.of(s?.model ?: Prefs.model)
    val charging = Charge.anyCharging(s)
    val eta = remember(s, tick) { s?.let { Charge.eta(it) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SubTopBar(stringResource(R.string.charging), onBack)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (model.family == Family.MAX) {
                PodsArt(Family.MAX, Part.PODS, Modifier.fillMaxWidth(0.6f).height(190.dp))
            } else {
                PodsArt(model.family, Part.CASE, Modifier.fillMaxWidth(0.62f).height(190.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                when {
                    s == null -> R.string.not_charging
                    eta?.minutes == 0 -> R.string.charged
                    charging -> R.string.charging_now
                    else -> R.string.not_charging
                }
            ),
            Modifier.fillMaxWidth(), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (model.family == Family.MAX) {
                ChargeRing(stringResource(R.string.battery), s?.left ?: s?.right, s?.leftCharging == true) {
                    PodsArt(Family.MAX, Part.POD, Modifier.size(40.dp))
                }
            } else {
                ChargeRing(stringResource(R.string.label_left), s?.left, s?.leftCharging == true) {
                    PodsArt(model.family, Part.POD, Modifier.graphicsLayer(scaleX = -1f).size(width = 30.dp, height = 40.dp))
                }
                ChargeRing(stringResource(R.string.label_right), s?.right, s?.rightCharging == true) {
                    PodsArt(model.family, Part.POD, Modifier.size(width = 30.dp, height = 40.dp))
                }
                ChargeRing(stringResource(R.string.label_case), s?.case, s?.caseCharging == true) {
                    PodsArt(model.family, Part.CASE, Modifier.size(width = 42.dp, height = 34.dp))
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        PodsCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.green.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Glyph.BOLT, c.green, 22.dp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.est_time), color = c.text2, fontSize = 14.sp)
                    Text(
                        when {
                            eta == null -> "—"
                            eta.minutes == 0 -> stringResource(R.string.charged)
                            else -> etaText(ctx, eta)
                        },
                        color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                if (eta != null && eta.minutes > 0) {
                    Text(stringResource(R.string.to_full), color = c.text2, fontSize = 14.sp)
                }
            }
            // подсказка: нет данных вообще — как их получить; оценка по типичной скорости — что уточнится
            if (s == null || (eta != null && !eta.measured && eta.minutes > 0)) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(if (s == null) R.string.est_unknown else R.string.est_typical),
                    color = c.text2, fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChargeRing(label: String, value: Int?, charging: Boolean, icon: @Composable () -> Unit) {
    val c = LocalPods.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BatteryRing(value, charging, ringSize = 84.dp, stroke = 6.dp) { icon() }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (charging) { LineIcon(Glyph.BOLT, c.green, 13.dp); Spacer(Modifier.width(2.dp)) }
            Text(label, color = if (value != null) batteryColor(c, value, charging) else c.text2,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(value?.let { "$it%" } ?: "—", color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
