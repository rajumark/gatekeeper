package io.github.rajumark.hoverfly.gatekeeper.internal

// Code point helpers for common code (java.lang.Character is JVM-only). They behave like their
// Java counterparts, including for unpaired surrogates, so every platform walks text the same way.

/** The code point at [i]; a lone surrogate is returned as itself, like String.codePointAt. */
internal fun String.cpAt(i: Int): Int {
    val hi = this[i]
    if (hi.isHighSurrogate() && i + 1 < length) {
        val lo = this[i + 1]
        if (lo.isLowSurrogate()) return ((hi.code - 0xD800) shl 10) + (lo.code - 0xDC00) + 0x10000
    }
    return hi.code
}

/** Number of UTF-16 units of [cp], like Character.charCount. */
internal fun cpLen(cp: Int): Int = if (cp >= 0x10000) 2 else 1

/** Number of code points in this string, like String.codePointCount(0, length). */
internal fun String.cpCount(): Int {
    var n = 0
    var i = 0
    while (i < length) { i += cpLen(cpAt(i)); n++ }
    return n
}

/** Appends [cp] as one or two UTF-16 units, like StringBuilder.appendCodePoint. */
internal fun StringBuilder.appendCp(cp: Int): StringBuilder {
    if (cp < 0x10000) return append(cp.toChar())
    val v = cp - 0x10000
    return append((0xD800 + (v shr 10)).toChar()).append((0xDC00 + (v and 0x3FF)).toChar())
}

/** [cp] as a string, like String(Character.toChars(cp)). */
internal fun cpToString(cp: Int): String = StringBuilder(2).appendCp(cp).toString()

/** Unicode NFKC normalization, from the platform (java.text.Normalizer, NSString, String.normalize). */
internal expect fun nfkc(s: String): String

/** Bytes of a bundled model file: gatekeeper.bin or spm_pieces.tsv. */
internal expect fun readModelFile(name: String): ByteArray
