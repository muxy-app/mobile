package com.muxy.app.features.files

import com.muxy.app.features.projects.ProjectIcon

data class FileLocation(
    val name: String,
    val path: String,
    val icon: ProjectIcon,
    val host: FileHost,
)

sealed interface FileHost {
    data object Mac : FileHost

    data object Remote : FileHost

    data class Computer(
        val name: String,
    ) : FileHost

    val deletesPermanently: Boolean get() = this == Remote

    val shortName: String get() =
        when (this) {
            Mac -> "Mac"
            Remote -> "remote host"
            is Computer -> name
        }

    val label: String get() =
        when (this) {
            Mac -> "Mac · Active worktree"
            Remote -> "Remote host · Active worktree"
            is Computer -> name
        }

    val possessiveName: String get() =
        when (this) {
            Mac -> "your Mac"
            Remote -> "your remote host"
            is Computer -> name
        }

    val reconnectMessage: String
        get() = "Reconnect to ${if (this is Computer) name else "your Mac"} to browse files. Any unsaved edits are still here."

    val selectionGuidance: String
        get() = "Touch and hold a file to select it. Changes are made on ${if (this == Remote) "the remote host" else possessiveName}."

    val draftReplacementMessage: String
        get() = "Your edits are still here. Reload for the latest file. Saving this draft replaces the file on $possessiveName."

    val deletionTitle: String get() = if (deletesPermanently) "Delete permanently?" else "Move to Trash?"
    val deletionMenuTitle: String get() = if (deletesPermanently) "Delete permanently" else "Move to Trash"
    val deletionAction: String get() = if (deletesPermanently) "Delete" else "Move to Trash"
    val deletionShortTitle: String get() = if (deletesPermanently) "Delete" else "Trash"

    fun deletionMessage(items: String): String =
        when (this) {
            Mac -> "$items will be moved to Trash on the Mac."
            Remote -> "$items will be permanently deleted from the remote host."
            is Computer -> "$items will be moved to Trash on $name."
        }
}
