package com.mrsohn.mrsohnlogcat

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

fun main() = application {
    val settings = remember { getSettingsRepository() }
    
    // Load saved state
    val savedWidth = settings.getString("windowWidth", "1000").toIntOrNull() ?: 1000
    val savedHeight = settings.getString("windowHeight", "800").toIntOrNull() ?: 800
    val savedX = settings.getString("windowX", "-1").toIntOrNull() ?: -1
    val savedY = settings.getString("windowY", "-1").toIntOrNull() ?: -1

    val windowState = rememberWindowState(
        size = DpSize(savedWidth.dp, savedHeight.dp),
        position = if (savedX != -1 && savedY != -1) {
            WindowPosition(savedX.dp, savedY.dp)
        } else {
            WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
        }
    )

    // Ensure window is within screen bounds
    LaunchedEffect(Unit) {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val screens = ge.screenDevices
        var isVisible = false
        val windowRect = Rectangle(savedX, savedY, savedWidth, savedHeight)

        for (screen in screens) {
            if (screen.defaultConfiguration.bounds.intersects(windowRect)) {
                isVisible = true
                break
            }
        }

        if (!isVisible && savedX != -1) {
            windowState.position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center)
        }
    }

    // Persist window state changes
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.size }
            .onEach { 
                settings.setString("windowWidth", it.width.value.toInt().toString())
                settings.setString("windowHeight", it.height.value.toInt().toString())
            }
            .collect()
    }

    LaunchedEffect(windowState) {
        snapshotFlow { windowState.position }
            .onEach { position ->
                if (position is WindowPosition.Absolute) {
                    settings.setString("windowX", position.x.value.toInt().toString())
                    settings.setString("windowY", position.y.value.toInt().toString())
                }
            }
            .collect()
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "MrSohnLogcat",
        state = windowState,
        icon = painterResource("icon.png")
    ) {
        App()
    }
}
