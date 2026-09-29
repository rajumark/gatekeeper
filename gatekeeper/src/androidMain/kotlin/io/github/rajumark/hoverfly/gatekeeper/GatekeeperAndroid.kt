@file:JvmName("GatekeeperAndroid")

package io.github.rajumark.hoverfly.gatekeeper

import android.content.Context

/** Kept so 1.x code (`Gatekeeper(context)`) still compiles; the model no longer needs a [Context]. */
@Deprecated("The model is bundled without assets now; use Gatekeeper().", ReplaceWith("Gatekeeper()"))
@Suppress("UNUSED_PARAMETER", "FunctionName")
public fun Gatekeeper(context: Context): Gatekeeper = Gatekeeper()
