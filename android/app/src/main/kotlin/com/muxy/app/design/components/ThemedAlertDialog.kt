package com.muxy.app.design.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muxy.app.design.LocalAppTheme

class ThemedDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val isDestructive: Boolean = false,
)

@Composable
fun ThemedAlertDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirm: ThemedDialogAction,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismiss: List<ThemedDialogAction> = emptyList(),
    content: (@Composable () -> Unit)? = null,
) {
    val theme = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(SurfaceCorners.card),
        containerColor = theme.secondaryGroupedBackground,
        titleContentColor = theme.foreground,
        textContentColor = theme.secondaryForeground,
        title = { Text(title) },
        text = content ?: message?.let { { Text(it) } },
        confirmButton = { DialogButton(confirm, isPrimary = true) },
        dismissButton =
            if (dismiss.isEmpty()) {
                null
            } else {
                { Row { dismiss.forEach { DialogButton(it, isPrimary = false) } } }
            },
    )
}

@Composable
private fun DialogButton(
    action: ThemedDialogAction,
    isPrimary: Boolean,
) {
    val theme = LocalAppTheme.current
    TextButton(
        onClick = action.onClick,
        enabled = action.enabled,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = if (action.isDestructive) theme.red else theme.foreground,
                disabledContentColor = theme.secondaryForeground,
            ),
    ) {
        Text(action.label, fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal)
    }
}
