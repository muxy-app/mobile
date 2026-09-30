package com.muxy.app.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme

@Composable
fun ThemedBorderedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalAppTheme.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.separator),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = theme.secondaryGroupedBackground, contentColor = theme.accent),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
