package com.phimbud.mobile.ui.aurora

import com.phimbud.mobile.core.platform.PlatformContext

/** No pixel access here yet: the controller falls back to a deterministic palette per poster. */
actual suspend fun extractPosterPalette(platformContext: PlatformContext, url: String): AuroraPalette? = null
