package com.muxy.app.features.billing

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.PageMargin
import com.muxy.app.design.components.ThemedBottomSheet

@Composable
fun TrialInfoSheet(
    state: BillingState,
    billing: BillingRepository,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val activity = LocalActivity.current
    ThemedBottomSheet(title = BillingCopy.sheetTitle(state.entitlement), onClose = onClose, dismissible = !state.purchasing) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = PageMargin.horizontal, end = PageMargin.horizontal, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("How it works", color = theme.secondaryForeground, style = MaterialTheme.typography.labelLarge)
            BillingCopy.sheetBullets(state.productPrice).forEach { bullet ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("•", color = theme.secondaryForeground)
                    Text(bullet, color = theme.secondaryForeground)
                }
            }
            BillingActions(
                state = state,
                primaryLabel = BillingCopy.primaryCtaLabel(state.entitlement, state.productPrice),
                onPrimary = {
                    if (state.entitlement == Entitlement.Unlocked) onClose() else activity?.let(billing::buy)
                },
                onRestore = billing::restore,
            )
        }
    }
}
