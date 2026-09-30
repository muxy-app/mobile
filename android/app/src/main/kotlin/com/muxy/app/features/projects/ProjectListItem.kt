package com.muxy.app.features.projects

class ProjectLogo(
    val png: ByteArray,
)

data class ProjectListItem(
    val id: String,
    val name: String,
    val path: String,
    val symbol: String?,
    val iconColor: String?,
    val logo: ProjectLogo?,
)

sealed interface ProjectListStatus {
    data object Loading : ProjectListStatus

    data object LoadFailed : ProjectListStatus

    data object Empty : ProjectListStatus

    data class Disconnected(
        val message: String?,
    ) : ProjectListStatus
}
