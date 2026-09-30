package com.muxy.app.features.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun <T> List<T>.opening(route: T): List<T> {
    val index = lastIndexOf(route)
    if (index < 0) return this + route
    return take(index + 1)
}

fun <T : NavKey> NavBackStack<T>.open(route: T) {
    val target = opening(route)
    if (target.size > size) {
        add(route)
        return
    }
    repeat(size - target.size) { removeAt(lastIndex) }
}

fun <T : NavKey> NavBackStack<T>.close(route: T) {
    if (lastOrNull() != route) return
    removeAt(lastIndex)
}
