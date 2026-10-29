package com.muxy.app.design.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemedBottomSheet(
    title: String,
    onClose: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val canDismiss by rememberUpdatedState(dismissible)
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || canDismiss },
        )
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = dismissible,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        containerColor = theme.groupedBackground,
        contentColor = theme.foreground,
    ) {
        Row(
            modifier = Modifier.padding(start = PageMargin.horizontal, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                color = theme.foreground,
            )
            TopBarAction(R.drawable.ic_close, "Close", onClose, enabled = dismissible)
        }
        content()
    }
}
