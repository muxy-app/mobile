package com.muxy.app.features.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.ThemedProminentButton

@Composable
internal fun BillingActions(
    state: BillingState,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onRestore: () -> Unit,
) {
    val theme = LocalAppTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        state.error?.let { Text(it, color = theme.red, textAlign = TextAlign.Center) }
        ThemedProminentButton(
            text = primaryLabel,
            onClick = onPrimary,
            enabled = !state.busy,
            loading = state.purchasing,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onRestore,
            enabled = !state.busy,
            colors = ButtonDefaults.textButtonColors(contentColor = theme.secondaryForeground),
        ) {
            Text(if (state.restoring) "Restoring…" else "Restore purchase")
        }
    }
}
