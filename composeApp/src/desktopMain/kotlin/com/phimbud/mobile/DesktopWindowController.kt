package com.phimbud.mobile

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

class DesktopWindowController(
    val windowState: WindowState
) {
    var awtWindow: ComposeWindow? = null

    var isFullscreen: Boolean by mutableStateOf(windowState.placement == WindowPlacement.Fullscreen)
        private set

    private var savedPlacement: WindowPlacement = WindowPlacement.Floating
    private var savedSize: DpSize = DpSize(1200.dp, 750.dp)
    private var savedPosition: WindowPosition = WindowPosition.PlatformDefault
    private var cursorCurrentlyHidden: Boolean = false

    private val invisibleCursor: Cursor by lazy {
        val blankImg = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        Toolkit.getDefaultToolkit().createCustomCursor(blankImg, Point(0, 0), "phimbud_blank_cursor")
    }

    fun syncPlacement() {
        isFullscreen = (windowState.placement == WindowPlacement.Fullscreen)
    }

    fun toggleFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            exitFullscreen()
        } else {
            enterFullscreen()
        }
    }

    fun enterFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            isFullscreen = true
            return
        }
        savedPlacement = windowState.placement
        if (windowState.placement == WindowPlacement.Floating) {
            savedSize = windowState.size
            savedPosition = windowState.position
        }
        windowState.placement = WindowPlacement.Fullscreen
        isFullscreen = true
    }

    fun exitFullscreen() {
        if (windowState.placement != WindowPlacement.Fullscreen) {
            isFullscreen = false
            return
        }
        setCursorHidden(false)
        if (savedPlacement == WindowPlacement.Maximized) {
            windowState.placement = WindowPlacement.Maximized
        } else {
            windowState.placement = WindowPlacement.Floating
            windowState.size = savedSize
            if (savedPosition is WindowPosition.Absolute) {
                windowState.position = savedPosition
            }
        }
        isFullscreen = false
    }

    fun setCursorHidden(hidden: Boolean) {
        if (cursorCurrentlyHidden == hidden) return
        cursorCurrentlyHidden = hidden
        val applyBlock = Runnable {
            val win = awtWindow ?: return@Runnable
            val targetCursor = if (hidden) invisibleCursor else Cursor.getDefaultCursor()
            win.cursor = targetCursor
            win.rootPane?.cursor = targetCursor
            win.contentPane?.cursor = targetCursor
        }
        if (SwingUtilities.isEventDispatchThread()) {
            applyBlock.run()
        } else {
            SwingUtilities.invokeLater(applyBlock)
        }
    }
}

val LocalDesktopWindowController = compositionLocalOf<DesktopWindowController?> { null }
