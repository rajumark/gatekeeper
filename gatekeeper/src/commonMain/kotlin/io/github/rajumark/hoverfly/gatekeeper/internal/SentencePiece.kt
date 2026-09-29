package io.github.rajumark.hoverfly.gatekeeper.internal

/**
 * Minimal SentencePiece *unigram* encoder, matching the Gatekeeper tokenizer
 * (identity normalization, add_dummy_prefix, whitespace -> "▁", no byte fallback).
 *
 * Viterbi over code points: a piece scores its log-prob; a character with no
 * single-char piece becomes an unknown node scored minScore - 10 (SentencePiece's
 * kUnkPenalty). Consecutive unknowns are merged into one unk id, as SentencePiece does.
 */
internal class SentencePiece(pieces: String) {
    private val table = HashMap<String, Piece>()
    private var maxLen = 1
    private val unkScore: Float

    private class Piece(val id: Int, val score: Float)

    init {
        var minScore = Float.MAX_VALUE
        for (line in pieces.lineSequence()) {
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3) {
                val score = parts[1].toFloat()
                table[parts[2]] = Piece(parts[0].toInt(), score)
                maxLen = maxOf(maxLen, parts[2].cpCount())
                minScore = minOf(minScore, score)
            }
        }
        unkScore = minScore - 10f
    }

    /** [text] must already be normalized (see [Featurizer.normalize]). */
    fun encode(text: String): IntArray {
        if (text.isEmpty()) return IntArray(0)
        val s = "▁" + text.replace(' ', '▁')
        val cps = Featurizer.codePoints(s)
        val n = cps.size
        // char offsets of each code point, to cut substrings cheaply
        val off = IntArray(n + 1)
        run {
            var o = 0
            for (i in 0 until n) { off[i] = o; o += cpLen(cps[i]) }
            off[n] = o
        }
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val backLen = IntArray(n + 1)
        val backId = IntArray(n + 1)
        best[0] = 0f
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            var hasSingle = false
            val maxL = minOf(maxLen, n - i)
            for (l in 1..maxL) {
                val p = table[s.substring(off[i], off[i + l])] ?: continue
                if (l == 1) hasSingle = true
                val sc = best[i] + p.score
                if (sc > best[i + l]) { best[i + l] = sc; backLen[i + l] = l; backId[i + l] = p.id }
            }
            if (!hasSingle) {
                val sc = best[i] + unkScore
                if (sc > best[i + 1]) { best[i + 1] = sc; backLen[i + 1] = 1; backId[i + 1] = UNK }
            }
        }
        val ids = ArrayList<Int>()
        var pos = n
        while (pos > 0) { ids.add(backId[pos]); pos -= backLen[pos] }
        ids.reverse()
        val merged = ArrayList<Int>(ids.size)
        for (id in ids) if (!(id == UNK && merged.isNotEmpty() && merged.last() == UNK)) merged.add(id)
        return merged.toIntArray()
    }

    companion object { const val UNK = 1 }
}
