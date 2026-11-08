package com.phimbud.mobile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.phimbud.mobile.core.platform.DesktopBackDispatcher
import com.phimbud.mobile.core.platform.DesktopKeyDispatcher
import com.phimbud.mobile.core.platform.LocalDesktopBackDispatcher
import com.phimbud.mobile.core.platform.LocalDesktopKeyDispatcher
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.repository.PhimBudRepository
import java.awt.Color
import java.awt.Dimension
import javax.imageio.ImageIO

fun main() = application {
    val repository = remember {
        PhimBudRepository(PlatformContext())
    }
    val windowState = rememberWindowState(
        size = DpSize(430.dp, 860.dp),
        position = WindowPosition.Aligned(Alignment.Center)
    )
    val windowController = remember(windowState) {
        DesktopWindowController(windowState)
    }
    val backDispatcher = remember {
        DesktopBackDispatcher()
    }
    val keyDispatcher = remember {
        DesktopKeyDispatcher()
    }

    @Suppress("DEPRECATION")
    val appIcon = runCatching { painterResource("icon.png") }.getOrNull()

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "PhimBud",
        icon = appIcon,
        resizable = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                backDispatcher.dispatchBack()
            } else {
                keyDispatcher.dispatchKeyEvent(event)
            }
        }
    ) {
        DisposableEffect(window) {
            window.background = Color(0x14, 0x14, 0x14)
            window.contentPane?.background = Color(0x14, 0x14, 0x14)
            window.minimumSize = Dimension(360, 640)
            windowController.awtWindow = window

            runCatching {
                val iconStream = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
                    ?: javaClass.getResourceAsStream("/icon.png")
                if (iconStream != null) {
                    val awtImage = ImageIO.read(iconStream)
                    if (awtImage != null) {
                        window.iconImages = listOf(awtImage)
                    }
                }
            }

            onDispose {
                if (windowController.awtWindow === window) {
                    windowController.awtWindow = null
                }
            }
        }

        CompositionLocalProvider(
            LocalDesktopWindowController provides windowController,
            LocalDesktopBackDispatcher provides backDispatcher,
            LocalDesktopKeyDispatcher provides keyDispatcher
        ) {
            App(
                repository = repository,
                onPlayerScreenActiveChanged = { active ->
                    windowController.setPlayerFullscreen(active)
                }
            )
        }
    }
}
