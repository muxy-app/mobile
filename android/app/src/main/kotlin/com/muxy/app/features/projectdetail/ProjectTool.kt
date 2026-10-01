package com.muxy.app.features.projectdetail

import androidx.annotation.DrawableRes
import com.muxy.app.R
import kotlinx.serialization.Serializable

@Serializable
enum class ProjectTool(
    val title: String,
    @DrawableRes val icon: Int,
) {
    FILES("Files", R.drawable.ic_folder),
    WORKTREES("Worktrees", R.drawable.ic_stacks),
    GIT("Git", R.drawable.ic_fork_right),
}
