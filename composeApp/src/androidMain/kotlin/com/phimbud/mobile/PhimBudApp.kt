package com.phimbud.mobile

import android.app.Application
import com.phimbud.mobile.core.platform.PlatformContext
import com.phimbud.mobile.core.repository.PhimBudRepository

class PhimBudApp : Application() {

    lateinit var repository: PhimBudRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = PhimBudRepository(PlatformContext(this))
    }
}
