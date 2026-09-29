package io.github.rajumark.hoverfly.gatekeeper.internal

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The Gatekeeper network in plain Kotlin:
 *
 *   lexical:  mean of hashed n-gram embeddings -> LayerNorm
 *   semantic: token + position embeddings -> LayerNorm -> 2 transformer layers (4 heads, post-LN)
 *             -> attention pooling
 *   head:     [lexical, semantic] -> Linear -> GELU -> Linear -> sigmoid per output
 *
 * Outputs: P(toxic), then insult, profanity, threat, hate, sexual.
 *
 * Only real (non-padding) token positions are computed: padded keys get a -1e4 score in the reference
 * model, whose softmax weight underflows to exactly 0, so the result is the same.
 * Stateless after loading, so one instance can serve several threads.
 */
internal class Network(bin: ByteArray) {

    /** Row-wise symmetric int8 matrix: w[r][c] = scale[r] * q[r * cols + c]. */
    class Q8(val rows: Int, val cols: Int, val scale: FloatArray, val q: ByteArray) {
        /** Dequantized row-major copy, for the dense layers (float math is faster than int8 on ART). */
        fun dense(): Dense = Dense(rows, cols, FloatArray(rows * cols) { scale[it / cols] * q[it] })
    }

    class Dense(val rows: Int, val cols: Int, val w: FloatArray)

    private class Layer(
        val qkv: Dense, val qkvB: FloatArray,
        val out: Dense, val outB: FloatArray,
        val ln1: Pair<FloatArray, FloatArray>,
        val ff1: Dense, val ff1B: FloatArray,
        val ff2: Dense, val ff2B: FloatArray,
        val ln2: Pair<FloatArray, FloatArray>,
    )

    private val lex: Q8
    private val tok: Q8
    private val pos: Q8
    private val lnLex: Pair<FloatArray, FloatArray>
    private val ln0: Pair<FloatArray, FloatArray>
    private val layers: List<Layer>
    private val pool: Dense
    private val poolB: FloatArray
    private val head1: Dense
    private val head1B: FloatArray
    private val head2: Dense
    private val head2B: FloatArray

    val nOutputs: Int get() = head2.rows
    val maxTokens: Int get() = pos.rows
    private val dSem: Int
    private val dHead: Int

    init {
        val t = read(bin)
        fun q(n: String) = t[n] as? Q8 ?: error("gatekeeper.bin: missing matrix $n")
        fun f(n: String) = t[n] as? FloatArray ?: error("gatekeeper.bin: missing vector $n")
        fun ln(n: String) = f("$n.weight") to f("$n.bias")
        lex = q("lex.weight"); tok = q("tok.weight"); pos = q("pos.weight")
        lnLex = ln("ln_lex"); ln0 = ln("ln0")
        layers = generateSequence(0) { it + 1 }.takeWhile { t.containsKey("blocks.$it.qkv.weight") }.map { i ->
            val p = "blocks.$i"
            Layer(
                q("$p.qkv.weight").dense(), f("$p.qkv.bias"),
                q("$p.out.weight").dense(), f("$p.out.bias"),
                ln("$p.ln1"),
                q("$p.ff.0.weight").dense(), f("$p.ff.0.bias"),
                q("$p.ff.2.weight").dense(), f("$p.ff.2.bias"),
                ln("$p.ln2"),
            )
        }.toList()
        check(layers.isNotEmpty()) { "gatekeeper.bin: no transformer layers" }
        pool = q("pool.weight").dense(); poolB = f("pool.bias")
        head1 = q("head.0.weight").dense(); head1B = f("head.0.bias")
        head2 = q("head.3.weight").dense(); head2B = f("head.3.bias")
        dSem = tok.cols
        dHead = dSem / HEADS
    }

    /** Sigmoid probabilities: [P(toxic), insult, profanity, threat, hate, sexual]. */
    fun probs(tokIds: IntArray, gramIds: IntArray): FloatArray {
        // ---- lexical stream: masked mean of n-gram embeddings
        val dLex = lex.cols
        val lexV = FloatArray(dLex)
        var nGram = 0
        for (g in gramIds) if (g > 0) { addRow(lex, g, lexV); nGram++ }
        if (nGram > 0) for (c in 0 until dLex) lexV[c] /= nGram.toFloat()
        layerNorm(lexV, 0, dLex, lnLex)

        // ---- semantic stream. An empty input still attends to position 0 (token 0 = padding).
        val n = maxOf(1, tokIds.size)
        val d = dSem
        val x = FloatArray(n * d)
        for (i in 0 until n) {
            if (i < tokIds.size) addRow(tok, tokIds[i], x, i * d)
            addRow(pos, i, x, i * d)
            layerNorm(x, i * d, d, ln0)
        }
        val qkv = FloatArray(n * 3 * d)
        val att = FloatArray(n * d)
        val s = FloatArray(n)
        val tmp = FloatArray(d)
        val hid = FloatArray(layers[0].ff1.rows)
        val inv = 1f / sqrt(dHead.toFloat())
        for (l in layers) {
            // multi-head self-attention
            for (i in 0 until n) linear(l.qkv, l.qkvB, x, i * d, qkv, i * 3 * d)
            att.fill(0f)
            for (h in 0 until HEADS) {
                val ho = h * dHead
                for (i in 0 until n) {
                    val qo = i * 3 * d + ho
                    for (j in 0 until n) {
                        val ko = j * 3 * d + d + ho
                        var dot = 0f
                        for (c in 0 until dHead) dot += qkv[qo + c] * qkv[ko + c]
                        s[j] = dot * inv
                    }
                    softmax(s, n)
                    val ao = i * d + ho
                    for (j in 0 until n) {
                        val w = s[j]
                        val vo = j * 3 * d + 2 * d + ho
                        for (c in 0 until dHead) att[ao + c] += w * qkv[vo + c]
                    }
                }
            }
            for (i in 0 until n) {
                linear(l.out, l.outB, att, i * d, tmp, 0)
                for (c in 0 until d) x[i * d + c] += tmp[c]
                layerNorm(x, i * d, d, l.ln1)
            }
            // feed-forward
            for (i in 0 until n) {
                linear(l.ff1, l.ff1B, x, i * d, hid, 0)
                for (c in hid.indices) hid[c] = gelu(hid[c])
                linear(l.ff2, l.ff2B, hid, 0, tmp, 0)
                for (c in 0 until d) x[i * d + c] += tmp[c]
                layerNorm(x, i * d, d, l.ln2)
            }
        }
        // attention pooling
        val w = FloatArray(n)
        val pw = FloatArray(1)
        for (i in 0 until n) { linear(pool, poolB, x, i * d, pw, 0); w[i] = pw[0] }
        softmax(w, n)

        // ---- head on [lexical, semantic]
        val z = FloatArray(dLex + d)
        lexV.copyInto(z)
        for (i in 0 until n) for (c in 0 until d) z[dLex + c] += w[i] * x[i * d + c]
        val h1 = FloatArray(head1.rows)
        linear(head1, head1B, z, 0, h1, 0)
        for (c in h1.indices) h1[c] = gelu(h1[c])
        val out = FloatArray(head2.rows)
        linear(head2, head2B, h1, 0, out, 0)
        for (c in out.indices) out[c] = (1.0 / (1.0 + exp(-out[c].toDouble()))).toFloat()
        return out
    }

    companion object {
        private const val HEADS = 4
        private const val EPS = 1e-5f

        /** out[oo + r] = b[r] + sum_c W[r][c] * x[xo + c] */
        private fun linear(m: Dense, b: FloatArray, x: FloatArray, xo: Int, out: FloatArray, oo: Int) {
            val cols = m.cols
            val w = m.w
            for (r in 0 until m.rows) {
                var acc = b[r]
                var k = r * cols
                for (c in xo until xo + cols) acc += w[k++] * x[c]
                out[oo + r] = acc
            }
        }

        /** out[oo..] += row r of the matrix (an embedding lookup). */
        private fun addRow(m: Q8, r: Int, out: FloatArray, oo: Int = 0) {
            val sc = m.scale[r]
            val base = r * m.cols
            for (c in 0 until m.cols) out[oo + c] += sc * m.q[base + c]
        }

        private fun layerNorm(v: FloatArray, o: Int, n: Int, p: Pair<FloatArray, FloatArray>) {
            var mean = 0f
            for (c in 0 until n) mean += v[o + c]
            mean /= n
            var varc = 0f
            for (c in 0 until n) { val t = v[o + c] - mean; varc += t * t }
            val inv = 1f / sqrt(varc / n + EPS)
            val (g, b) = p
            for (c in 0 until n) v[o + c] = (v[o + c] - mean) * inv * g[c] + b[c]
        }

        private fun softmax(v: FloatArray, n: Int) {
            var mx = Float.NEGATIVE_INFINITY
            for (i in 0 until n) if (v[i] > mx) mx = v[i]
            var sum = 0f
            for (i in 0 until n) { v[i] = exp(v[i] - mx); sum += v[i] }
            for (i in 0 until n) v[i] /= sum
        }

        /** Exact GELU, 0.5 x (1 + erf(x / sqrt 2)), as torch.nn.GELU(). */
        private fun gelu(x: Float): Float = (0.5 * x * (1.0 + erf(x / 1.4142135623730951))).toFloat()

        /** erf via the Numerical Recipes erfc Chebyshev fit, fractional error < 1.2e-7. */
        private fun erf(z: Double): Double {
            val a = kotlin.math.abs(z)
            val t = 1.0 / (1.0 + 0.5 * a)
            val r = t * exp(-a * a - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
                t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
                t * (-0.82215223 + t * 0.17087277)))))))))
            return if (z >= 0) 1.0 - r else r - 1.0
        }

        /** Parses gatekeeper.bin: "MOJI" magic, version 1, then named tensors (int8 matrices, fp32 vectors). */
        private fun read(bytes: ByteArray): Map<String, Any> {
            val buf = LittleEndianReader(bytes)
            require(buf.bytes(4).decodeToString() == "MOJI") { "not a gatekeeper.bin file" }
            val version = buf.int()
            require(version == 1) { "unsupported gatekeeper.bin version $version" }
            val out = HashMap<String, Any>()
            repeat(buf.int()) {
                val name = buf.bytes(buf.short() and 0xFFFF).decodeToString()
                val dtype = buf.byte().toInt()
                val dims = IntArray(buf.byte().toInt()) { buf.int() }
                val size = dims.fold(1) { a, b -> a * b }
                out[name] = when (dtype) {
                    0 -> buf.floats(size)
                    1 -> {
                        val scale = buf.floats(dims[0])
                        Q8(dims[0], dims[1], scale, buf.bytes(size))
                    }
                    else -> error("gatekeeper.bin: unknown dtype $dtype for $name")
                }
            }
            return out
        }
    }

    /** Sequential little-endian reads over a byte array (java.nio.ByteBuffer is JVM-only). */
    private class LittleEndianReader(private val b: ByteArray) {
        private var pos = 0

        fun byte(): Byte = b[pos++]

        fun short(): Int = (b[pos++].toInt() and 0xFF) or ((b[pos++].toInt() and 0xFF) shl 8)

        fun int(): Int = (b[pos++].toInt() and 0xFF) or ((b[pos++].toInt() and 0xFF) shl 8) or
            ((b[pos++].toInt() and 0xFF) shl 16) or ((b[pos++].toInt() and 0xFF) shl 24)

        fun floats(n: Int): FloatArray = FloatArray(n) { Float.fromBits(int()) }

        fun bytes(n: Int): ByteArray = b.copyOfRange(pos, pos + n).also { pos += n }
    }
}
