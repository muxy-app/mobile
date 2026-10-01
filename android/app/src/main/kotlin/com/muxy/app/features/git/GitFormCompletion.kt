package com.muxy.app.features.git

import java.util.UUID

data class GitFormCompletion(
    val route: GitRoute,
    val url: String? = null,
    val id: UUID = UUID.randomUUID(),
)
