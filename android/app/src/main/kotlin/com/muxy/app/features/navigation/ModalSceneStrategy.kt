package com.muxy.app.features.navigation

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.SurfaceCorners
import com.muxy.app.design.components.WindowWidth

enum class ModalSize {
    FORM,
    PAGE,
}

class ModalSceneStrategy(
    private val windowWidth: WindowWidth,
) : SceneStrategy<AppRoute> {
    override fun SceneStrategyScope<AppRoute>.calculateScene(entries: List<NavEntry<AppRoute>>): Scene<AppRoute>? {
        if (!windowWidth.isWide) return null
        val entry = entries.lastOrNull() ?: return null
        val size = entry.metadata[SIZE_KEY] as? ModalSize ?: return null
        val below = entries.dropLast(1)
        if (below.isEmpty()) return null
        return ModalScene(entry, below, size, onBack)
    }

    companion object {
        private const val SIZE_KEY = "muxy.modalSize"

        fun form(): Map<String, Any> = NavigationTransitions.modal + (SIZE_KEY to ModalSize.FORM)

        fun page(): Map<String, Any> = NavigationTransitions.modal + (SIZE_KEY to ModalSize.PAGE)
    }
}

private data class ModalScene(
    private val entry: NavEntry<AppRoute>,
    private val below: List<NavEntry<AppRoute>>,
    private val size: ModalSize,
    private val onBack: () -> Unit,
) : OverlayScene<AppRoute> {
    override val key: Any = entry.contentKey

    override val entries: List<NavEntry<AppRoute>> = listOf(entry)

    override val previousEntries: List<NavEntry<AppRoute>> = below

    override val overlaidEntries: List<NavEntry<AppRoute>> = below

    override val content: @Composable () -> Unit = {
        Dialog(
            onDismissRequest = onBack,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(
                modifier = Modifier.frame(size),
                shape = RoundedCornerShape(SurfaceCorners.card),
                color = LocalAppTheme.current.groupedBackground,
            ) {
                entry.Content()
            }
        }
    }
}

private fun Modifier.frame(size: ModalSize): Modifier =
    when (size) {
        ModalSize.PAGE -> fillMaxWidth(0.92f).widthIn(max = 1100.dp).fillMaxHeight(0.92f)
        ModalSize.FORM -> fillMaxWidth(0.9f).widthIn(max = 640.dp).fillMaxHeight(0.85f).heightIn(max = 900.dp)
    }
