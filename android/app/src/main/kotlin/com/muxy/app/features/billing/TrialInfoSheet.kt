package com.muxy.app.features.billing

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.TopBarAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialInfoSheet(
    state: BillingState,
    billing: BillingRepository,
    onClose: () -> Unit,
) {
    val theme = LocalAppTheme.current
    val activity = LocalActivity.current
    val purchasing by rememberUpdatedState(state.purchasing)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !purchasing },
        )
    ModalBottomSheet(
        onDismissRequest = { if (!purchasing) onClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = !purchasing,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !purchasing),
        containerColor = theme.groupedBackground,
        contentColor = theme.foreground,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    BillingCopy.sheetTitle(state.entitlement),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                TopBarAction(R.drawable.ic_close, "Close", onClose, enabled = !purchasing)
            }
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
