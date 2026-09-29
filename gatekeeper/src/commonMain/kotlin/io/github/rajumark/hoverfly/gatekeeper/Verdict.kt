package io.github.rajumark.hoverfly.gatekeeper

/**
 * The result of [Gatekeeper.check].
 *
 * @property isToxic true when [score] is at or above the threshold of the chosen [Sensitivity].
 * @property score how likely the message is toxic, 0..1.
 * @property categories a score per [Category], 0..1 each (independent, they don't sum to 1). Treat them as hints:
 *   they are most reliable for English. Decide on [isToxic] / [score].
 */
public data class Verdict(
    val isToxic: Boolean,
    val score: Float,
    val categories: Map<Category, Float>,
) {
    /** The category with the highest score, or null when the message is not toxic. */
    val topCategory: Category? get() = if (isToxic) categories.maxByOrNull { it.value }?.key else null
}

/** What kind of toxic content a message looks like. */
public enum class Category {
    /** Name-calling, personal attacks. */
    INSULT,

    /** Swearing, obscene words. */
    PROFANITY,

    /** Threats of violence or harm. */
    THREAT,

    /** Attacks on a group: religion, caste, race, gender, sexuality, origin, disability. */
    HATE,

    /** Sexual harassment, unwanted sexual content. */
    SEXUAL,
}

/** How eager [Gatekeeper] is to flag a message. */
public enum class Sensitivity(public val threshold: Float) {
    /** Catches the most (about 85% of toxic messages); flags about 2–3% of normal chat. Good for kids' apps
     *  or for sending messages to a human moderator. */
    STRICT(0.25f),

    /** The default: the best balance (about 77% caught, about 1% of normal chat flagged). */
    BALANCED(0.42f),

    /** Fewest false alarms (about 0.6% of normal chat flagged); catches about 64%. Good for auto-hiding content. */
    RELAXED(0.70f),
}
