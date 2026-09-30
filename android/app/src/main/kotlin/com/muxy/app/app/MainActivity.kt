package com.muxy.app.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.muxy.app.design.AppTheme
import com.muxy.app.design.ThemedWindow
import com.muxy.app.features.navigation.MuxyApp
import com.muxy.app.features.navigation.RootViewModel

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as MuxyApplication).container

    private val viewModel: RootViewModel by viewModels {
        viewModelFactory { initializer { container.makeRootViewModel() } }
    }

    private val themedWindow = ThemedWindow(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.settings.value == null }
        super.onCreate(savedInstanceState)
        themedWindow.apply(AppTheme.muxy)
        setContent { MuxyApp(container, viewModel, themedWindow) }
    }
}
