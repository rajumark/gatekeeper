package io.github.rajumark.hoverfly.gatekeeper

import io.github.rajumark.hoverfly.gatekeeper.internal.Featurizer
import io.github.rajumark.hoverfly.gatekeeper.internal.SentencePiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Checks the Kotlin port against the reference implementation on testvectors.tsv: identical token and
 * n-gram ids, and the same 6 probabilities (toxic + 5 categories).
 */
class ParityTest {
    private class Vector(val text: String, val tok: List<Int>, val grams: List<Int>, val probs: List<Float>)

    private fun ints(s: String) = if (s.isEmpty()) emptyList() else s.split(',').map { it.toInt() }

    private val vectors = javaClass.getResourceAsStream("/testvectors.tsv")!!.bufferedReader().readLines()
        .map { it.split('\t') }
        .map { Vector(unescape(it[0]), ints(it[1]), ints(it[2]), it[3].split(',').map(String::toFloat)) }

    @Test
    fun featurizerMatchesReference() {
        val f = Featurizer(File("$ASSETS/spm_pieces.tsv").inputStream().use { SentencePiece(it) })
        var bad = 0
        for (v in vectors) {
            val got = f.featurize(v.text)
            if (got.tokIds.toList() != v.tok || got.gramIds.toList() != v.grams) {
                bad++
                println("MISMATCH: ${v.text}\n  tok  ${got.tokIds.toList()}\n  want ${v.tok}\n" +
                    "  grams same=${got.gramIds.toList() == v.grams}")
            }
        }
        println("featurizer: ${vectors.size - bad}/${vectors.size} identical")
        assertEquals(0, bad)
    }

    @Test
    fun modelMatchesReference() {
        val gk = testGatekeeper()
        var maxDiff = 0f
        var sameVerdict = 0
        for (v in vectors) {
            val p = gk.probabilities(v.text)
            for (i in p.indices) maxDiff = maxOf(maxDiff, abs(p[i] - v.probs[i]))
            if ((p[0] >= Sensitivity.BALANCED.threshold) == (v.probs[0] >= Sensitivity.BALANCED.threshold)) sameVerdict++
        }
        println("model: same verdict ${sameVerdict}/${vectors.size}, max |dp| = $maxDiff")
        assertEquals(vectors.size, sameVerdict)
        assertTrue("probabilities drift: $maxDiff", maxDiff < 1e-4f)
    }

    companion object {
        const val ASSETS = "src/main/assets/gatekeeper"
        fun testGatekeeper() = Gatekeeper { name -> File("$ASSETS/$name").inputStream() }

        /** The vectors file writes tabs and newlines inside a text as \t and \n. */
        fun unescape(s: String) = s.replace("\\t", "\t").replace("\\n", "\n")
    }
}
