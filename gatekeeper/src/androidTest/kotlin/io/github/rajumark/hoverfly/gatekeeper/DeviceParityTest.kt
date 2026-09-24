package io.github.rajumark.hoverfly.gatekeeper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Same check as the JVM ParityTest, on a real device: Android's ICU-backed Unicode tables
 * (NFKC, lowercase, character types) must give the reference scores on every vector.
 */
@RunWith(AndroidJUnit4::class)
class DeviceParityTest {
    @Test
    fun matchesReferenceOnDevice() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val lines = inst.context.assets.open("testvectors.tsv").bufferedReader().readLines()
        val t0 = System.nanoTime()
        val gk = Gatekeeper(inst.targetContext)
        val loadMs = (System.nanoTime() - t0) / 1e6
        var same = 0
        var maxDiff = 0f
        for (line in lines) {
            val c = line.split('\t')
            val text = c[0].replace("\\t", "\t").replace("\\n", "\n")
            val want = c[3].split(',').map { it.toFloat() }
            val p = gk.probabilities(text)
            for (i in p.indices) maxDiff = maxOf(maxDiff, abs(p[i] - want[i]))
            if ((p[0] >= Sensitivity.BALANCED.threshold) == (want[0] >= Sensitivity.BALANCED.threshold)) same++
            else println("DIFF: $text")
        }
        val texts = lines.map { it.substringBefore('\t') }
        repeat(300) { gk.check(texts[it % texts.size]) }
        val n = 3000
        val s0 = System.nanoTime()
        repeat(n) { gk.check(texts[it % texts.size]) }
        val ms = (System.nanoTime() - s0) / 1e6 / n
        println("GATEKEEPER_DEVICE verdict $same/${lines.size} maxdiff=$maxDiff load=${"%.0f".format(loadMs)}ms " +
            "latency=${"%.3f".format(ms)}ms")
        assertTrue("$same/${lines.size}", same == lines.size)
        assertTrue("maxdiff $maxDiff", maxDiff < 1e-3f)
    }
}
