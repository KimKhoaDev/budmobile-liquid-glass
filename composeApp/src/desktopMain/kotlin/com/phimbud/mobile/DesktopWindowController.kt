package com.phimbud.mobile

import androidx.compose.runtime.compositionLocalOf
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

    private var savedPlacement: WindowPlacement = WindowPlacement.Floating
    private var savedSize: DpSize = DpSize(430.dp, 860.dp)
    private var savedPosition: WindowPosition = WindowPosition.PlatformDefault
    private var inPlayerMode: Boolean = false
    private var cursorCurrentlyHidden: Boolean = false

    private val invisibleCursor: Cursor by lazy {
        val blankImg = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        Toolkit.getDefaultToolkit().createCustomCursor(blankImg, Point(0, 0), "phimbud_blank_cursor")
    }

    fun setPlayerFullscreen(active: Boolean) {
        if (active) {
            if (!inPlayerMode) {
                inPlayerMode = true
                savedPlacement = windowState.placement
                if (windowState.placement == WindowPlacement.Floating) {
                    savedSize = windowState.size
                    savedPosition = windowState.position
                }
            }
            if (windowState.placement != WindowPlacement.Fullscreen) {
                windowState.placement = WindowPlacement.Fullscreen
            }
        } else {
            setCursorHidden(false)
            if (inPlayerMode) {
                inPlayerMode = false
                restoreWindowFromFullscreen()
            }
        }
    }

    fun toggleFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            setCursorHidden(false)
            restoreWindowFromFullscreen()
        } else {
            if (windowState.placement == WindowPlacement.Floating) {
                savedSize = windowState.size
                savedPosition = windowState.position
                savedPlacement = WindowPlacement.Floating
            } else if (windowState.placement == WindowPlacement.Maximized) {
                savedPlacement = WindowPlacement.Maximized
            }
            windowState.placement = WindowPlacement.Fullscreen
        }
    }

    private fun restoreWindowFromFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = WindowPlacement.Floating
            windowState.size = savedSize
            if (savedPosition is WindowPosition.Absolute) {
                windowState.position = savedPosition
            }
            if (savedPlacement == WindowPlacement.Maximized) {
                SwingUtilities.invokeLater {
                    windowState.placement = WindowPlacement.Maximized
                }
            }
        }
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
