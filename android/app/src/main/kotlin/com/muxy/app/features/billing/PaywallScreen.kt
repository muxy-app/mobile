package com.muxy.app.features.billing

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.TopBarAction

@Composable
fun PaywallScreen(
    billing: BillingRepository,
    onClose: () -> Unit,
) {
    val state by billing.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val theme = LocalAppTheme.current
    BackHandler(enabled = state.purchasing) {}
    LaunchedEffect(state.entitlement) {
        if (state.entitlement == Entitlement.Unlocked) onClose()
    }
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "",
                navigationIcon = {
                    TopBarAction(R.drawable.ic_close, "Close", { if (!state.purchasing) onClose() }, enabled = !state.purchasing)
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                BillingCopy.paywallTitle(state.entitlement),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                BillingCopy.paywallSubtitle(state.entitlement),
                color = theme.secondaryForeground,
                textAlign = TextAlign.Center,
            )
            BillingActions(
                state = state,
                primaryLabel = BillingCopy.paywallButtonLabel(state.productPrice),
                onPrimary = { activity?.let(billing::buy) },
                onRestore = billing::restore,
            )
        }
    }
}
