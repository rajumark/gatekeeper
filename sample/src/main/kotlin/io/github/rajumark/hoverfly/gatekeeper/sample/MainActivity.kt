package io.github.rajumark.hoverfly.gatekeeper.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rajumark.hoverfly.gatekeeper.Category
import io.github.rajumark.hoverfly.gatekeeper.Gatekeeper
import io.github.rajumark.hoverfly.gatekeeper.Sensitivity
import io.github.rajumark.hoverfly.gatekeeper.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GatekeeperTheme { GatekeeperScreen() } }
    }
}

private val EXAMPLES = listOf(
    "hey are we still meeting at 6?", "this movie killed me 😂", "bhai tu toh kamaal hai 🔥",
    "you are a stupid idiot", "chup kar bsdk", "tujhe jaan se maar dunga", "कल सुबह मिलते हैं",
    "semma movie da", "send nudes", "the delivery was late, very bad service",
)

private val SAFE = Color(0xFF1B873F)
private val FLAGGED = Color(0xFFD1242F)

/** Result of one inference, with its wall-clock time. */
private class Result(val verdict: Verdict, val micros: Long)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GatekeeperScreen() {
    val context = LocalContext.current.applicationContext

    // Loading reads ~4 MB: do it once, off the main thread.
    val gatekeeper by produceState<Gatekeeper?>(null) {
        value = withContext(Dispatchers.Default) { Gatekeeper(context) }
        awaitDispose { value?.close() }
    }
    var message by remember { mutableStateOf(EXAMPLES[0]) }
    var sensitivity by remember { mutableStateOf(Sensitivity.BALANCED) }

    val result by produceState<Result?>(null, gatekeeper, message, sensitivity) {
        val g = gatekeeper ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            val v = g.check(message, sensitivity)
            Result(v, (System.nanoTime() - t0) / 1000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Gatekeeper") }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message or comment") },
                trailingIcon = {
                    if (message.isNotEmpty()) IconButton(onClick = { message = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Sensitivity.entries.forEachIndexed { i, s ->
                    SegmentedButton(
                        selected = sensitivity == s,
                        onClick = { sensitivity = s },
                        shape = SegmentedButtonDefaults.itemShape(i, Sensitivity.entries.size),
                    ) { Text(s.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }) }
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
                EXAMPLES.forEach { SuggestionChip(onClick = { message = it }, label = { Text(it) }) }
            }
            Text(
                "Runs on this device. No network, no permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            if (v.isToxic) "Flagged" + (v.topCategory?.let { " · ${label(it)}" } ?: "") else "Looks fine",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color,
        )
    }
    ScoreBar("Toxic score", v.score, color, marker = sensitivity.threshold)
    Text("Categories (hints)", style = MaterialTheme.typography.labelLarge)
    Category.entries.forEach { c -> ScoreBar(label(c), v.categories.getValue(c), MaterialTheme.colorScheme.primary) }
    Text(
        "threshold ${"%.2f".format(Locale.ROOT, sensitivity.threshold)}  ·  ${r.micros} µs",
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
        Text("%.0f%%".format(Locale.ROOT, value * 100), Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
    }
}

private fun label(c: Category) = c.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
fun GatekeeperTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
