package io.github.rajumark.hoverfly.gatekeeper.sample

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Gatekeeper (desktop)", state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), width = 800.dp, height = 600.dp)) {
        App("Desktop (JVM)")
    }
}
