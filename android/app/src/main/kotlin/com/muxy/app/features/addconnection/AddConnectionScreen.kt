package com.muxy.app.features.addconnection

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.MuxyTopAppBar
import com.muxy.app.design.components.RowPosition
import com.muxy.app.design.components.ThemedCell
import com.muxy.app.design.components.ThemedList
import com.muxy.app.design.components.ThemedListItem
import com.muxy.app.design.components.ThemedSectionHeader
import com.muxy.app.design.components.ThemedTextField
import com.muxy.app.design.components.TopBarAction
import com.muxy.app.design.components.TopBarTextAction
import com.muxy.app.design.components.themedSection
import com.muxy.app.models.Connection
import com.muxy.app.networking.muxy1.discovery.DiscoveredService
import kotlinx.coroutines.launch

private val rowIconSize = 24.dp
private val rowSeparatorInset = 16.dp + rowIconSize + 16.dp

@Composable
fun AddConnectionScreen(
    viewModel: AddConnectionViewModel,
    onCancel: () -> Unit,
    onAdded: (Connection) -> Unit,
) {
    val services by viewModel.discoveredServices.collectAsStateWithLifecycle()
    val isWorking = viewModel.isWorking
    val scanner = rememberQrCodeScanner()
    val scope = rememberCoroutineScope()
    val added = viewModel.addedConnection
    LaunchedEffect(added) { added?.let(onAdded) }
    BackHandler(enabled = isWorking) {}
    Scaffold(
        topBar = {
            MuxyTopAppBar(
                title = "Add Connection",
                navigationIcon = { TopBarAction(R.drawable.ic_close, "Cancel", onCancel, enabled = !isWorking) },
                actions = { TopBarTextAction(text = "Add", onClick = viewModel::submit, enabled = viewModel.canSubmit) },
            )
        },
    ) { padding ->
        ThemedList(contentPadding = padding) {
            item(key = "kind") { KindPicker(enabled = !isWorking) }
            nearbySection(services, enabled = !isWorking, onSelect = viewModel::applyDiscovered)
            sectionGap(key = "scan-gap")
            item(key = "scan") {
                ThemedCell(RowPosition.SINGLE) {
                    ActionRow(
                        icon = R.drawable.ic_qr_code_scanner,
                        title = "Scan QR Code",
                        enabled = !isWorking,
                        onClick = { scope.launch { viewModel.onScanResult(scanner.scan()) } },
                    )
                }
            }
            macSection(viewModel, enabled = !isWorking)
            if (viewModel.status != AddConnectionStatus.Idle) {
                sectionGap(key = "status-gap")
                item(key = "status") {
                    ThemedCell(RowPosition.SINGLE) { StatusRow(viewModel.status) }
                }
            }
        }
    }
    viewModel.alert?.let { alert ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAlert,
            confirmButton = { TextButton(onClick = viewModel::dismissAlert) { Text("OK") } },
            title = { Text(alert.title) },
            text = { Text(alert.message) },
        )
    }
}

@Composable
private fun rememberQrCodeScanner(): QrCodeScanner {
    val context = LocalActivity.current ?: LocalContext.current
    return remember(context) { QrCodeScanner(context) }
}

@Composable
private fun KindPicker(enabled: Boolean) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SegmentedButton(
            selected = true,
            onClick = {},
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
            enabled = enabled,
            icon = {},
            label = { Text("Muxy 1") },
        )
    }
}

private fun LazyListScope.sectionGap(key: String) {
    item(key = key) { Spacer(modifier = Modifier.height(24.dp)) }
}

private fun LazyListScope.nearbySection(
    services: List<DiscoveredService>,
    enabled: Boolean,
    onSelect: (DiscoveredService) -> Unit,
) {
    item(key = "nearby-header") { ThemedSectionHeader("Nearby") }
    if (services.isEmpty()) {
        item(key = "nearby-searching") {
            ThemedCell(RowPosition.SINGLE) { SearchingRow() }
        }
        return
    }
    themedSection(items = services, key = { "nearby:${it.name}" }, separatorInset = rowSeparatorInset) { service ->
        ThemedListItem(
            headline = { Text(service.name) },
            supporting = { Text("${service.host}:${service.port}") },
            leading = { RowIcon(R.drawable.ic_desktop_mac) },
            modifier = Modifier.clickable(enabled = enabled) { onSelect(service) },
        )
    }
}

private fun LazyListScope.macSection(
    viewModel: AddConnectionViewModel,
    enabled: Boolean,
) {
    item(key = "mac-header") { ThemedSectionHeader("Mac") }
    item(key = "mac-name") {
        ThemedCell(RowPosition.FIRST) {
            ThemedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = "Name",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "mac-host") {
        ThemedCell(RowPosition.MIDDLE) {
            ThemedTextField(
                value = viewModel.host,
                onValueChange = { viewModel.host = it },
                label = "Host",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
        }
    }
    item(key = "mac-port") {
        ThemedCell(RowPosition.LAST) {
            ThemedTextField(
                value = viewModel.portText,
                onValueChange = { viewModel.portText = it },
                label = "Port",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
        }
    }
}

@Composable
private fun SearchingRow() {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = theme.secondaryForeground, strokeWidth = 2.dp)
        Text(text = "Searching for Macs…", style = MaterialTheme.typography.bodyLarge, color = theme.secondaryForeground)
    }
}

@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ThemedListItem(
        headline = { Text(title) },
        leading = { RowIcon(icon) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun RowIcon(
    @DrawableRes icon: Int,
    tint: Color = LocalAppTheme.current.foreground,
) {
    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(rowIconSize), tint = tint)
}

@Composable
private fun StatusRow(status: AddConnectionStatus) {
    val theme = LocalAppTheme.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            AddConnectionStatus.Idle -> {
                Unit
            }

            AddConnectionStatus.Connecting -> {
                Progress("Connecting…")
            }

            AddConnectionStatus.Authenticating -> {
                Progress("Authenticating…")
            }

            AddConnectionStatus.AwaitingApproval -> {
                RowIcon(R.drawable.ic_back_hand_fill, theme.yellow)
                Text(text = "Approve this device on your Mac.", color = theme.foreground)
            }

            AddConnectionStatus.Succeeded -> {
                RowIcon(R.drawable.ic_check_circle_fill, theme.green)
                Text(text = "Connected", color = theme.green)
            }

            is AddConnectionStatus.Failed -> {
                RowIcon(R.drawable.ic_warning_fill, theme.red)
                Text(text = status.message, color = theme.red)
            }
        }
    }
}

@Composable
private fun Progress(text: String) {
    val theme = LocalAppTheme.current
    CircularProgressIndicator(modifier = Modifier.size(rowIconSize), color = theme.accent, strokeWidth = 2.dp)
    Text(text = text, color = theme.foreground)
}
