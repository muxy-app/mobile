package com.muxy.app.design.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muxy.app.design.LocalAppTheme

@Composable
fun ThemedProminentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val theme = LocalAppTheme.current
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = theme.accent, contentColor = theme.onAccent),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).semantics { contentDescription = "Loading" },
                color = theme.accent,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
