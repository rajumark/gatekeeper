package io.github.rajumark.hoverfly.gatekeeper.internal

import java.text.Normalizer
import java.util.Locale

/**
 * Turns a message into model inputs. Must produce exactly the ids of the reference implementation;
 * ParityTest checks it against the vectors in src/test/resources/testvectors.tsv.
 *
 * Python-semantics notes: strings are walked by code point (not UTF-16 unit), `\w` means a letter or
 * number of any script or "_", and whitespace follows Python's str.isspace().
 */
internal class Featurizer(private val sp: SentencePiece) {

    /** Model inputs without padding: at most [MAX_TOKENS] token ids and [MAX_GRAMS] n-gram ids. */
    class Features(val tokIds: IntArray, val gramIds: IntArray)

    fun featurize(text: String): Features {
        val norm = normalize(text)
        val toks = sp.encode(norm)
        return Features(if (toks.size > MAX_TOKENS) toks.copyOf(MAX_TOKENS) else toks, gramIds(norm))
    }

    companion object {
        const val MAX_TOKENS = 64
        const val MAX_GRAMS = 192
        const val N_BUCKETS = 1 shl 15

        /** String.codePoints() needs API 24; this works on every API level. */
        fun codePoints(s: String): IntArray {
            val out = IntArray(s.codePointCount(0, s.length))
            var i = 0
            var k = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                out[k++] = cp
                i += Character.charCount(cp)
            }
            return out
        }

        fun isPySpace(cp: Int): Boolean =
            Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x85

        /** Python's `\w`: a letter or number of any script, or "_". */
        fun isPyWord(cp: Int): Boolean {
            if (cp == '_'.code) return true
            return when (Character.getType(cp).toByte()) {
                Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                Character.MODIFIER_LETTER, Character.OTHER_LETTER,
                Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
                else -> false
            }
        }

        /** A word character for the lexical features: `\w` plus the combining marks of Indic and other scripts,
         *  so "मुझे" stays one word instead of splitting at every vowel sign. */
        fun isWordChar(cp: Int) = isPyWord(cp) ||
            cp in 0x0300..0x036F || cp in 0x0900..0x0DFF || cp == 0x0E31 || cp in 0x0E34..0x0E3A || cp in 0x0E47..0x0E4E ||
            cp in 0x1000..0x109F || cp in 0x1780..0x17FF || cp in 0x0591..0x05C7 || cp in 0x064B..0x065F || cp == 0x0670 ||
            cp in 0x06D6..0x06ED || cp in 0x1200..0x139F || cp in 0x0530..0x058F || cp in 0x10A0..0x10FF

        private fun isSpaceless(cp: Int) =
            cp in 0x0E00..0x0E7F || cp in 0x1000..0x109F || cp in 0x1780..0x17FF ||
                cp in 0x3040..0x30FF || cp in 0x3400..0x9FFF || cp in 0xAC00..0xD7AF

        /** NFKC, lowercase, @handles -> "@user", URLs -> " @url ", a character repeated 3+ times -> 2,
         *  collapse whitespace. */
        fun normalize(text: String): String {
            var t = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            t = replaceUrls(replaceMentions(t))
            t = collapseRepeats(t)
            val sb = StringBuilder(t.length)
            var pendingSpace = false
            var i = 0
            while (i < t.length) {
                val cp = t.codePointAt(i)
                if (isPySpace(cp)) pendingSpace = true
                else {
                    if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
                    pendingSpace = false
                    sb.appendCodePoint(cp)
                }
                i += Character.charCount(cp)
            }
            return sb.toString()
        }

        /** Python re.sub(r"(https?://|www\.)\S+", " @url ", t). */
        fun replaceUrls(t: String): String {
            val sb = StringBuilder(t.length)
            var i = 0
            while (i < t.length) {
                val prefix = when {
                    t.startsWith("https://", i) -> 8
                    t.startsWith("http://", i) -> 7
                    t.startsWith("www.", i) -> 4
                    else -> 0
                }
                val end = if (prefix > 0) nonSpaceRunEnd(t, i + prefix) else i
                if (prefix > 0 && end > i + prefix) {
                    sb.append(" @url ")
                    i = end
                } else {
                    val cp = t.codePointAt(i)
                    sb.appendCodePoint(cp)
                    i += Character.charCount(cp)
                }
            }
            return sb.toString()
        }

        /** Python re.sub(r"@\w+", "@user", t). */
        fun replaceMentions(t: String): String {
            val sb = StringBuilder(t.length)
            var i = 0
            while (i < t.length) {
                if (t[i] == '@') {
                    var j = i + 1
                    while (j < t.length) {
                        val cp = t.codePointAt(j)
                        if (!isPyWord(cp)) break
                        j += Character.charCount(cp)
                    }
                    if (j > i + 1) { sb.append("@user"); i = j; continue }
                }
                val cp = t.codePointAt(i)
                sb.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            return sb.toString()
        }

        /** Python re.sub(r"(.)\1{2,}", r"\1\1", t): runs of 3+ of one character (not "\n") become 2. */
        fun collapseRepeats(t: String): String {
            val cps = codePoints(t)
            val sb = StringBuilder(t.length)
            var k = 0
            while (k < cps.size) {
                val cp = cps[k]
                var j = k + 1
                while (j < cps.size && cps[j] == cp) j++
                val run = j - k
                val keep = if (cp != '\n'.code && run >= 3) 2 else run
                repeat(keep) { sb.appendCodePoint(cp) }
                k = j
            }
            return sb.toString()
        }

        private fun nonSpaceRunEnd(t: String, from: Int): Int {
            var j = from
            while (j < t.length) {
                val cp = t.codePointAt(j)
                if (isPySpace(cp)) break
                j += Character.charCount(cp)
            }
            return j
        }

        fun fnv1a(s: String): Long {
            var h = 0x811C9DC5L
            for (b in s.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xFF)
                h = (h * 0x01000193L) and 0xFFFFFFFFL
            }
            return h
        }

        private fun cpString(cps: IntArray, from: Int, to: Int): String {
            val sb = StringBuilder()
            for (k in from until to) sb.appendCodePoint(cps[k])
            return sb.toString()
        }

        fun lexicalFeatures(norm: String): List<String> {
            val feats = ArrayList<String>()
            val cps = codePoints(norm)
            val words = ArrayList<String>()
            var start = -1
            for (k in 0..cps.size) {
                val w = k < cps.size && isWordChar(cps[k])
                if (w && start < 0) start = k
                if (!w && start >= 0) { words.add(cpString(cps, start, k)); start = -1 }
            }
            for (w in words) feats.add("w:$w")
            for (k in 0 until words.size - 1) feats.add("b:${words[k]} ${words[k + 1]}")
            val padded = intArrayOf(' '.code) + cps + intArrayOf(' '.code)
            for (n in 2..4) {
                for (k in 0..padded.size - n) {
                    var allSpace = true
                    for (j in k until k + n) if (!isPySpace(padded[j])) { allSpace = false; break }
                    if (!allSpace) feats.add(cpString(padded, k, k + n))
                }
            }
            for (cp in cps) if (isSpaceless(cp)) feats.add("c:" + String(Character.toChars(cp)))
            return feats
        }

        fun gramIds(norm: String): IntArray {
            val ids = LinkedHashSet<Int>()
            for (f in lexicalFeatures(norm)) {
                ids.add((fnv1a(f) % (N_BUCKETS - 1) + 1).toInt())
                if (ids.size >= MAX_GRAMS) break
            }
            return ids.toIntArray()
        }
    }
}
