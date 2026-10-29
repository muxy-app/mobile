package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun <T> List<T>.opening(
    route: T,
    identity: (T) -> Any? = { it },
): List<T> {
    val index = indexOfLast { identity(it) == identity(route) }
    if (index < 0) return this + route
    return take(index) + route
}

fun <T : NavKey> NavBackStack<T>.open(
    route: T,
    identity: (T) -> Any? = { it },
) {
    replaceWith(opening(route, identity))
}

fun <T : NavKey> NavBackStack<T>.replaceWith(routes: List<T>) {
    while (size > routes.size) removeAt(lastIndex)
    routes.forEachIndexed { index, route ->
        when {
            index == size -> add(route)
            this[index] != route -> set(index, route)
        }
    }
}

fun <T : NavKey> NavBackStack<T>.close(route: T) {
    if (lastOrNull() != route) return
    removeAt(lastIndex)
}
