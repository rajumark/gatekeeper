package io.github.rajumark.hoverfly.gatekeeper

import io.github.rajumark.hoverfly.gatekeeper.internal.Featurizer
import io.github.rajumark.hoverfly.gatekeeper.internal.Network
import io.github.rajumark.hoverfly.gatekeeper.internal.SentencePiece
import io.github.rajumark.hoverfly.gatekeeper.internal.readModelFile
import kotlin.jvm.JvmOverloads

/**
 * On-device text safety: tells you whether a chat message, comment or review is toxic
 * (abusive, offensive, hateful, threatening or sexual harassment).
 *
 * ```
 * Gatekeeper().use { gk ->
 *     gk.check("you are an idiot").isToxic        // true
 *     gk.check("bhai tu toh kamaal hai").isToxic  // false
 * }
 * ```
 *
 * Works in English, Hindi, Tamil, Telugu, Malayalam, Kannada and romanised Hinglish / Tanglish, plus about
 * 15 other languages.
 *
 * Everything runs locally on Android, iOS, macOS, the JVM and the web: the ~4 MB model ships inside the library, there is no network, no permission
 * and no dependency. Creating an instance reads the model (tens of ms), so create it off the main thread
 * and keep it around; [check] takes about a millisecond and is safe to call from several threads.
 */
public class Gatekeeper internal constructor(open: (String) -> ByteArray) : AutoCloseable {

    /** Loads the model bundled in the library. */
    public constructor() : this(::readModelFile)

    private var network: Network? = Network(open("gatekeeper.bin"))
    private val featurizer = Featurizer(SentencePiece(open("spm_pieces.tsv").decodeToString()))

    init {
        check(requireNotNull(network).nOutputs == 1 + Category.entries.size) { "gatekeeper.bin has unexpected outputs" }
        // The first calls run interpreted; pay that here (off the UI thread) instead of on the first message.
        repeat(WARM_UP) { probabilities("warm up the model $it") }
    }

    /**
     * Checks one message.
     *
     * @param sensitivity how eager to flag. [Sensitivity.BALANCED] by default.
     * @return the verdict, with the toxic score and a score per [Category]. A blank message is never toxic.
     */
    @JvmOverloads
    public fun check(text: String, sensitivity: Sensitivity = Sensitivity.BALANCED): Verdict =
        check(text, sensitivity.threshold)

    /**
     * Checks one message against your own [threshold] (0..1): the message is toxic when its score is at least
     * [threshold]. Lower catches more, higher gives fewer false alarms.
     */
    public fun check(text: String, threshold: Float): Verdict {
        require(threshold in 0f..1f) { "threshold must be in 0..1" }
        if (text.isBlank()) return Verdict(false, 0f, Category.entries.associateWith { 0f })
        val p = probabilities(text)
        return Verdict(p[0] >= threshold, p[0], Category.entries.associateWith { p[1 + it.ordinal] })
    }

    /** Shortcut for `check(text, sensitivity).isToxic`. */
    @JvmOverloads
    public fun isToxic(text: String, sensitivity: Sensitivity = Sensitivity.BALANCED): Boolean =
        check(text, sensitivity).isToxic

    /** The toxic score alone, 0..1. */
    public fun score(text: String): Float = if (text.isBlank()) 0f else probabilities(text)[0]

    /** Releases the model (about 12 MB of heap). The instance cannot be used afterwards. */
    override fun close() {
        network = null
    }

    internal fun probabilities(text: String): FloatArray {
        val net = checkNotNull(network) { "Gatekeeper is closed" }
        val f = featurizer.featurize(text)
        return net.probs(f.tokIds, f.gramIds)
    }

    internal companion object {
        const val WARM_UP = 20
    }
}
