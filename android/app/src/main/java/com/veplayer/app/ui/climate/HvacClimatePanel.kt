package com.veplayer.app.ui.climate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veplayer.app.ui.theme.Mist
import com.veplayer.app.ui.theme.Mute
import com.veplayer.app.ui.theme.Teal
import com.veplayer.app.vehicle.HvacClimate
import com.veplayer.app.vehicle.HvacClimateBus
import com.veplayer.app.vehicle.HvacClimateMonitor

/**
 * Compact HVAC controls — cabin / target / AC / fan.
 */
@Composable
fun HvacClimatePanel(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val st by HvacClimateMonitor.state.collectAsState()
    val sp by HvacClimateBus.setpoint.collectAsState()
    val accent = Color(HvacClimate.accentArgb(st.band))

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0xFF121212), RoundedCornerShape(12.dp))
                .padding(if (compact) 10.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Clima", color = Mist, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                st.band.uppercase(),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            st.label.ifBlank { "Sin datos HVAC" },
            color = if (st.showPanel) Mist else Mute,
            fontSize = 13.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { HvacClimateBus.nudgeTarget(-1f) }) {
                Text("−", color = Mist, fontSize = 18.sp)
            }
            Text(
                sp.targetC.let { "%.0f°".format(it) },
                color = Teal,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = { HvacClimateBus.nudgeTarget(1f) }) {
                Text("+", color = Mist, fontSize = 18.sp)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("AC", color = Mist)
            Switch(checked = sp.acOn, onCheckedChange = { HvacClimateBus.setAc(it) })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ventilador · ${sp.fanLevel}", color = Mist)
            OutlinedButton(onClick = { HvacClimateBus.cycleFan() }) {
                Text("Fan +", color = Mist)
            }
        }
        if (sp.override) {
            Text("Override local (mock/sim)", color = Mute, fontSize = 11.sp)
            TextButton(onClick = { HvacClimateBus.clearOverride() }) {
                Text("Liberar override", color = Mute, fontSize = 12.sp)
            }
        }
    }
}
