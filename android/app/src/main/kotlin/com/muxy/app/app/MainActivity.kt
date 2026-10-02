package com.muxy.app.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.muxy.app.design.AppTheme
import com.muxy.app.design.ThemedWindow
import com.muxy.app.features.navigation.DeepLink
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
        installSplashScreen().setKeepOnScreenCondition {
            !container.ready.value || viewModel.settings.value == null || !container.billing.state.value.trialLoaded
        }
        super.onCreate(savedInstanceState)
        themedWindow.apply(AppTheme.muxy)
        if (savedInstanceState == null) openLink(intent)
        setContent {
            val ready by container.ready.collectAsStateWithLifecycle()
            if (ready) MuxyApp(container, viewModel, themedWindow)
        }
    }

    override fun onResume() {
        super.onResume()
        container.billing.onActivityResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent)
    }

    private fun openLink(intent: Intent) {
        val link = DeepLink.linkToOpen(intent.action, intent.flags, intent.dataString) ?: return
        viewModel.openPairingLink(link)
    }
}
