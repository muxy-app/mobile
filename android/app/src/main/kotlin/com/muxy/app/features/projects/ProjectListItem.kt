package com.muxy.app.features.projects

class ProjectLogo(
    val png: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ProjectLogo && png.contentEquals(other.png)

    override fun hashCode(): Int = png.contentHashCode()
}

sealed interface ProjectIcon {
    data class Symbol(
        val name: String?,
    ) : ProjectIcon

    data class Emoji(
        val text: String,
    ) : ProjectIcon
}

data class ProjectListItem(
    val id: String,
    val name: String,
    val path: String,
    val icon: ProjectIcon,
    val iconColor: String?,
    val logo: ProjectLogo?,
    val isNested: Boolean = false,
)

sealed interface ProjectListStatus {
    data object Loading : ProjectListStatus

    data object LoadFailed : ProjectListStatus

    data object Empty : ProjectListStatus

    data object NeedsPairing : ProjectListStatus

    data class Disconnected(
        val message: String?,
    ) : ProjectListStatus
}
