package io.github.rajumark.hoverfly.gatekeeper.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.gatekeeper.Category
import io.github.rajumark.hoverfly.gatekeeper.Gatekeeper
import io.github.rajumark.hoverfly.gatekeeper.Sensitivity
import io.github.rajumark.hoverfly.gatekeeper.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private val examples = listOf(
    "hey are we still meeting at 6?", "this movie killed me 😂", "bhai tu toh kamaal hai 🔥",
    "you are a stupid idiot", "chup kar bsdk", "tujhe jaan se maar dunga", "कल सुबह मिलते हैं",
    "semma movie da", "send nudes", "the delivery was late, very bad service",
)

private val SAFE = Color(0xFF1B873F)
private val FLAGGED = Color(0xFFD1242F)

/** Result of one inference, with its wall-clock time. */
private class Result(val verdict: Verdict, val micros: Long)

/** The whole demo: type a message, see the verdict. [platform] is shown so screenshots say where they ran. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun App(platform: String, initial: String = examples[3]) {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            // Loading reads ~4 MB: do it once, off the main thread.
            val gatekeeper by produceState<Gatekeeper?>(null) { value = withContext(Dispatchers.Default) { Gatekeeper() } }
            var message by remember { mutableStateOf(initial) }
            var sensitivity by remember { mutableStateOf(Sensitivity.BALANCED) }

            val result by produceState<Result?>(null, gatekeeper, message, sensitivity) {
                val g = gatekeeper ?: return@produceState
                value = withContext(Dispatchers.Default) {
                    val t0 = TimeSource.Monotonic.markNow()
                    val v = g.check(message, sensitivity)
                    Result(v, t0.elapsedNow().inWholeMicroseconds)
                }
            }

            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Gatekeeper", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Kotlin Multiplatform · $platform · io.github.rajumark:gatekeeper:$GATEKEEPER_VERSION",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message or comment") },
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Sensitivity.entries.forEachIndexed { i, s ->
                        SegmentedButton(
                            selected = sensitivity == s,
                            onClick = { sensitivity = s },
                            shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                        ) { Text(title(s.name)) }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val r = result
                    if (gatekeeper == null || r == null) {
                        Box(Modifier.fillMaxWidth().height(48.dp), Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        VerdictCard(r, sensitivity)
                    }
                }
                Text("Try", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.forEach { SuggestionChip(onClick = { message = it }, label = { Text(it) }) }
                }
                Text(
                    "Runs on this device. No network, no permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(r: Result, sensitivity: Sensitivity) {
    val v = r.verdict
    val color = if (v.isToxic) FLAGGED else SAFE
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(12.dp).height(12.dp).background(color, RoundedCornerShape(6.dp)))
        Text(
            if (v.isToxic) "Flagged" + (v.topCategory?.let { " · ${title(it.name)}" } ?: "") else "Looks fine",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color,
        )
    }
    ScoreBar("Toxic score", v.score, color, marker = sensitivity.threshold)
    Text("Categories (hints)", style = MaterialTheme.typography.labelLarge)
    Category.entries.forEach { c -> ScoreBar(title(c.name), v.categories.getValue(c), MaterialTheme.colorScheme.primary) }
    Text(
        "threshold ${(sensitivity.threshold * 100).roundToInt() / 100.0}  ·  ${r.micros} µs",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ScoreBar(name: String, value: Float, color: Color, marker: Float? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(5.dp))) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0.02f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(5.dp)))
            if (marker != null) Row(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(marker.coerceAtLeast(0.001f)))
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.weight((1f - marker).coerceAtLeast(0.001f)))
            }
        }
        Text("${(value * 100).roundToInt()}%", Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
    }
}

private fun title(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

const val GATEKEEPER_VERSION = "2.0.0"
