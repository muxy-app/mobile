package com.muxy.app.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class MuxyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.start(ProcessLifecycleOwner.get().lifecycle)
    }
}
