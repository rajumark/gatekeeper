package io.github.rajumark.hoverfly.gatekeeper.sample

import io.github.rajumark.hoverfly.gatekeeper.Category
import io.github.rajumark.hoverfly.gatekeeper.Gatekeeper
import io.github.rajumark.hoverfly.gatekeeper.Sensitivity
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/** "Kotlin/JS" or "Kotlin/Wasm". */
expect val runtime: String

private val examples = listOf(
    "hey are we still meeting at 6?", "this movie killed me 😂", "bhai tu toh kamaal hai 🔥",
    "you are a stupid idiot", "chup kar bsdk", "tujhe jaan se maar dunga", "कल सुबह मिलते हैं",
    "semma movie da", "send nudes", "the delivery was late, very bad service",
)

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun title(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

private fun bar(name: String, v: Float, cls: String, marker: Float? = null) =
    "<div class=\"bar\"><span class=\"lbl\">${esc(name)}</span><div class=\"track\"><div class=\"fill $cls\" style=\"width:${(v * 100).roundToInt()}%\"></div>" +
        (marker?.let { "<div class=\"marker\" style=\"left:${(it * 100).roundToInt()}%\"></div>" } ?: "") +
        "</div><span class=\"pct\">${(v * 100).roundToInt()}%</span></div>"

fun main() {
    fun el(id: String) = document.getElementById(id) as HTMLElement
    val input = document.getElementById("text") as HTMLInputElement
    el("platform").textContent = "Kotlin Multiplatform · $runtime · io.github.rajumark:gatekeeper:2.0.0"

    val t0 = TimeSource.Monotonic.markNow()
    val gatekeeper = Gatekeeper()
    el("load").textContent = "Model loaded in ${t0.elapsedNow().inWholeMilliseconds} ms"
    var sensitivity = Sensitivity.BALANCED

    fun render() {
        val mark = TimeSource.Monotonic.markNow()
        val v = gatekeeper.check(input.value, sensitivity)
        val micros = mark.elapsedNow().inWholeMicroseconds
        val cls = if (v.isToxic) "bad" else "ok"
        el("verdict").className = "verdict $cls"
        el("verdict").textContent = if (v.isToxic) "Flagged" + (v.topCategory?.let { " · ${title(it.name)}" } ?: "") else "Looks fine"
        el("score").innerHTML = bar("Toxic score", v.score, cls, sensitivity.threshold)
        el("cats").innerHTML = Category.entries.joinToString("") { bar(title(it.name), v.categories.getValue(it), "") }
        el("timing").textContent = "threshold ${(sensitivity.threshold * 100).roundToInt() / 100.0}  ·  $micros µs"
        val seg = el("sens").querySelectorAll("button")
        for (i in 0 until seg.length) (seg.item(i) as HTMLElement).className = if (Sensitivity.entries[i] == sensitivity) "seg on" else "seg"
    }

    el("sens").innerHTML = Sensitivity.entries.joinToString("") { "<button class=\"seg\">${title(it.name)}</button>" }
    val seg = el("sens").querySelectorAll("button")
    for (i in 0 until seg.length) (seg.item(i) as HTMLElement).onclick = { sensitivity = Sensitivity.entries[i]; render(); null }
    el("examples").innerHTML = examples.joinToString("") { "<button class=\"chip\">${esc(it)}</button>" }
    val list = el("examples").querySelectorAll("button")
    for (i in 0 until list.length) {
        val b = list.item(i) as HTMLElement
        b.onclick = { input.value = b.textContent ?: ""; render(); null }
    }
    input.oninput = { render(); null }
    input.value = examples[3]
    render()
}
