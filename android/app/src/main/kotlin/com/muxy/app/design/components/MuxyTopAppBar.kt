package com.muxy.app.design.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.muxy.app.design.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuxyTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val theme = LocalAppTheme.current
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = navigationIcon,
        actions = actions,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = theme.groupedBackground,
                scrolledContainerColor = theme.groupedBackground,
                navigationIconContentColor = theme.foreground,
                titleContentColor = theme.foreground,
                actionIconContentColor = theme.foreground,
            ),
    )
}

@Composable
fun TopBarAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painter = painterResource(icon), contentDescription = label)
    }
}

@Composable
fun TopBarTextAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val theme = LocalAppTheme.current
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = theme.foreground, disabledContentColor = theme.secondaryForeground),
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}
