package io.github.rajumark.hoverfly.gatekeeper.internal

private fun jsNfkc(s: String): String = js("s.normalize('NFKC')")

internal actual fun nfkc(s: String): String = jsNfkc(s)
