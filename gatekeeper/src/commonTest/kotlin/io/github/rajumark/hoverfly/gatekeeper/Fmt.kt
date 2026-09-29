package io.github.rajumark.hoverfly.gatekeeper

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/** "%.{digits}f" for test output (String.format is JVM-only). */
internal fun fmt(x: Number, digits: Int): String {
    val v = x.toDouble()
    val m = 10.0.pow(digits)
    val r = (abs(v) * m).roundToLong()
    val int = r / m.toLong()
    val frac = (r % m.toLong()).toString().padStart(digits, '0')
    return (if (v < 0 && r != 0L) "-" else "") + if (digits == 0) "$int" else "$int.$frac"
}
