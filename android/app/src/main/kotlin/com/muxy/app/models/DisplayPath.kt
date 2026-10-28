package com.muxy.app.models

object DisplayPath {
    private val homeRoots = setOf("Users", "home")

    fun abbreviated(path: String): String {
        val components = path.split('/').filter(String::isNotEmpty)
        if (components.size < 2 || components[0] !in homeRoots) return path
        val rest = components.drop(2).joinToString("/")
        return if (rest.isEmpty()) "~" else "~/$rest"
    }

    fun folderName(path: String): String = path.trimEnd('/').substringAfterLast('/')
}
