@file:OptIn(ExperimentalWasmJsInterop::class)

import kotlin.js.ExperimentalWasmJsInterop
import io.github.rajumark.hoverfly.gatekeeper.Gatekeeper
import io.github.rajumark.hoverfly.gatekeeper.Sensitivity

// Website live demo: docs/demo/worker.js calls load() once, then run() per input; run() returns JSON.

private fun q(s: String) = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\""); '\\' -> append("\\\\")
        else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

private var instance: Gatekeeper? = null

private fun model(): Gatekeeper = instance ?: Gatekeeper().also { instance = it }

/** Loads the bundled model and warms it up. */
@JsExport
fun load() {
    model()
}

/** The verdict at sensitivity [option] (STRICT, BALANCED or RELAXED): {toxic, score, threshold, categories}. */
@JsExport
fun run(input: String, option: String): String {
    val sensitivity = Sensitivity.entries.firstOrNull { it.name == option } ?: Sensitivity.BALANCED
    val v = model().check(input, sensitivity)
    val categories = v.categories.entries.joinToString(",", "{", "}") { "${q(it.key.name)}:${it.value}" }
    return "{\"toxic\":${v.isToxic},\"score\":${v.score},\"threshold\":${sensitivity.threshold},\"categories\":$categories}"
}

fun main() {}
