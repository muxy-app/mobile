package com.muxy.app.app

import android.app.Application

class MuxyApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
