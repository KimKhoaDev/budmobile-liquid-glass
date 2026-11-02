package com.phimbud.mobile

import androidx.compose.ui.window.ComposeUIViewController
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.repository.PhimBudRepository
import platform.UIKit.UIViewController

private val iosRepository: PhimBudRepository by lazy {
    PhimBudRepository(PlatformContext())
}

fun MainViewController(): UIViewController = ComposeUIViewController {
    App(repository = iosRepository)
}
