package com.muxy.app.features.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.design.LocalAppTheme

@Composable
fun EntitlementFooter(billing: BillingRepository) {
    if (!billing.enforcement.enforced) return
    val state by billing.state.collectAsStateWithLifecycle()
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val theme = LocalAppTheme.current
    val text = BillingCopy.footerText(state.entitlement, state.productPrice)
    if (text != null) {
        Surface(
            onClick = { showDetails = true },
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            color = theme.secondaryGroupedBackground,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, theme.separator),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text,
                    modifier = Modifier.weight(1f),
                    color = theme.secondaryForeground,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Tap for details", color = theme.secondaryForeground, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (showDetails) TrialInfoSheet(state, billing, onClose = { showDetails = false })
}
