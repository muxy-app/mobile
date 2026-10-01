package com.muxy.app.features.files

import androidx.navigation3.runtime.NavKey
import com.muxy.app.core.text.Graphemes
import com.muxy.app.models.FileLimits
import com.muxy.app.models.RemoteFileEntry
import com.muxy.app.models.RemoteFileStat
import com.muxy.app.models.RemoteTextFile
import kotlinx.serialization.Serializable

@Serializable
enum class FileRoute : NavKey { BROWSER, PREVIEW, MOVE }

data class FilePreviewState(
    val entry: RemoteFileEntry,
    val stat: RemoteFileStat? = null,
    val text: RemoteTextFile? = null,
    val image: ByteArray? = null,
    val isUnsupported: Boolean = false,
    val draft: String = "",
    val displayText: String = "",
    val isPreviewShortened: Boolean = false,
    val isEditing: Boolean = false,
    val wrapsLines: Boolean = true,
    val hasExternalChanges: Boolean = false,
) {
    val isDirty: Boolean get() = isEditing && draft != text?.text

    fun showing(file: RemoteTextFile): FilePreviewState {
        val displayed = Graphemes.prefix(file.text, FileLimits.PREVIEW_CHARACTERS)
        return copy(
            text = file,
            image = null,
            isUnsupported = false,
            draft = file.text,
            displayText = displayed,
            isPreviewShortened = displayed.length < file.text.length,
            isEditing = false,
            hasExternalChanges = false,
        )
    }
}

data class FileManagerState(
    val location: FileLocation,
    val routes: List<FileRoute> = listOf(FileRoute.BROWSER),
    val currentPath: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val isLoadingDirectory: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val isLoadingMove: Boolean = false,
    val isBusy: Boolean = false,
    val isConnected: Boolean = false,
    val scopeReady: Boolean = false,
    val locationReady: Boolean = false,
    val hasContextChanged: Boolean = false,
    val errorMessage: String? = null,
    val preview: FilePreviewState? = null,
    val movePath: String = "",
    val moveEntries: List<RemoteFileEntry> = emptyList(),
    val movingPaths: List<String> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val contextId: Long = 0,
) {
    val route: FileRoute get() = routes.last()
    val isDirty: Boolean get() = preview?.isDirty == true
    val canMutate: Boolean get() = isConnected && scopeReady && locationReady && !isBusy && !hasContextChanged
    val canMoveHere: Boolean get() =
        canMutate && !isLoadingMove && movingPaths.isNotEmpty() &&
            movingPaths.none { RemoteFilePath.contains(movePath, it) } && movingPaths.any { RemoteFilePath.parent(it) != movePath }
}
