package io.github.rajumark.hoverfly.gatekeeper.internal

import io.github.rajumark.hoverfly.gatekeeper.Gatekeeper
import java.text.Normalizer

internal actual fun nfkc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFKC)

// The model ships as Java resources in the jar/AAR (src/modelData), so no Context or copy is needed.
internal actual fun readModelFile(name: String): ByteArray =
    Gatekeeper::class.java.getResourceAsStream("/io/github/rajumark/hoverfly/gatekeeper/model/$name")?.use { it.readBytes() }
        ?: error("Gatekeeper model file $name is missing from the library jar")
