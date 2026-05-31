package io.github.pascalair.tuner

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Palette de l'app (sombre, accent vert, dans l'esprit demandé).
private val Bg = Color(0xFF121417)
private val Surface = Color(0xFF1E2126)
private val SurfaceActive = Color(0xFF2C313A)
private val Green = Color(0xFF3DDC84)
private val TextPrimary = Color(0xFFECECEC)
private val TextDim = Color(0xFF8A8F98)

/** Échelle de l'indicateur : ±50 cents en pleine largeur. */
private const val CENTS_SCALE = 50f

@Composable
fun TunerApp(
    controller: TunerController,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            if (hasPermission) {
                TunerContent(controller)
            } else {
                PermissionScreen(onRequestPermission)
            }
        }
    }
}

@Composable
private fun TunerContent(controller: TunerController) {
    val state = controller.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InstrumentSelector(state.instrument) { controller.selectInstrument(it) }

        Spacer(Modifier.weight(1f))
        NoteDisplay(state)
        Spacer(Modifier.height(20.dp))
        PitchIndicator(state)
        Spacer(Modifier.weight(1f))

        StringRow(state) { controller.toggleString(it) }
    }
}

@Composable
private fun InstrumentSelector(selected: Instrument, onSelect: (Instrument) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        INSTRUMENTS.forEach { instrument ->
            val active = instrument.name == selected.name
            Text(
                text = instrument.name,
                color = if (active) Bg else TextDim,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier
                    .padding(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (active) Green else Color.Transparent)
                    .clickable { onSelect(instrument) }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NoteDisplay(state: TunerState) {
    val label = state.instrument.strings[state.targetIndex].label
    val noteColor by animateColorAsState(
        if (state.inTune) Green else TextPrimary, label = "noteColor"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (state.hasSignal) label else "–",
            color = if (state.hasSignal) noteColor else TextDim,
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
        )
        val hint = when {
            !state.hasSignal -> "Jouez une corde"
            state.inTune -> "Juste"
            state.cents < 0 -> "Trop grave  ♭"
            else -> "Trop aigu  ♯"
        }
        val centsText = if (state.hasSignal && !state.inTune) {
            val c = state.cents.roundToInt()
            "  ${if (c > 0) "+$c" else "$c"}"
        } else ""
        Text(
            text = hint + centsText,
            color = if (state.inTune) Green else TextDim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PitchIndicator(state: TunerState) {
    val targetCents = if (state.hasSignal)
        state.cents.coerceIn(-CENTS_SCALE, CENTS_SCALE) else 0f
    val animated by animateFloatAsState(
        targetValue = targetCents,
        animationSpec = tween(durationMillis = 90),
        label = "centsPointer",
    )
    val dotColor = when {
        !state.hasSignal -> TextDim
        state.inTune -> Green
        else -> TextPrimary
    }
    val history = state.history

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("♭", color = TextDim, fontSize = 22.sp)
            Text("♯", color = TextDim, fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            val cx = size.width / 2f
            val half = size.width * 0.46f
            val gaugeY = 24f
            fun xForCents(c: Float) = cx + (c.coerceIn(-CENTS_SCALE, CENTS_SCALE) / CENTS_SCALE) * half

            // Graduations : centre (haute) et repères à ±25, ±50.
            listOf(-1f, -0.5f, 0f, 0.5f, 1f).forEach { frac ->
                val x = cx + frac * half
                val tall = frac == 0f
                drawLine(
                    color = if (tall) TextDim else TextDim.copy(alpha = 0.4f),
                    start = Offset(x, gaugeY - if (tall) 16f else 9f),
                    end = Offset(x, gaugeY + if (tall) 16f else 9f),
                    strokeWidth = if (tall) 5f else 3f,
                )
            }

            // Tracé défilant : historique des dernières secondes, du plus récent (haut)
            // au plus ancien (bas), qui s'efface en descendant.
            val traceTop = gaugeY + 34f
            val traceBottom = size.height
            val span = (HISTORY_SIZE - 1).coerceAtLeast(1)
            val n = history.size
            var prev: Offset? = null
            for (i in 0 until n) {
                val value = history[i]
                val age = n - 1 - i // 0 = mesure la plus récente
                if (value.isNaN()) {
                    prev = null
                    continue
                }
                val y = traceTop + (age.toFloat() / span) * (traceBottom - traceTop)
                val point = Offset(xForCents(value), y)
                val alpha = (1f - age.toFloat() / span).coerceIn(0.05f, 1f)
                prev?.let { drawLine(Green.copy(alpha = alpha), it, point, strokeWidth = 4f) }
                prev = point
            }

            // Point courant, par-dessus le reste.
            drawCircle(color = dotColor, radius = 22f, center = Offset(xForCents(animated), gaugeY))
        }
    }
}

@Composable
private fun StringRow(state: TunerState, onTap: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.instrument.strings.forEachIndexed { index, string ->
            val isTarget = state.hasSignal && state.targetIndex == index
            val isForced = state.forcedString == index
            val bg = when {
                isTarget && state.inTune -> Green
                isTarget -> SurfaceActive
                else -> Surface
            }
            val textColor = if (isTarget && state.inTune) Bg else TextPrimary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(
                        width = if (isForced) 2.dp else 0.dp,
                        color = if (isForced) Green else Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onTap(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(string.label, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "L'accordeur a besoin du micro pour entendre les cordes.",
            color = TextPrimary,
            fontSize = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Autoriser le micro",
            color = Bg,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Green)
                .clickable { onRequestPermission() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
