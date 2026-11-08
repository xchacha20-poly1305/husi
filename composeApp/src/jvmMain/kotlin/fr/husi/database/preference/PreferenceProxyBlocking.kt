package fr.husi.database.preference

import kotlinx.coroutines.runBlocking

fun <T> PreferenceProxy<T>.getBlocking(): T = runBlocking { get() }

fun <T> PreferenceProxy<T>.setBlocking(value: T) {
    runBlocking { set(value) }
}

fun <T> PreferenceProxy<T>.updateBlocking(transform: (T) -> T) {
    runBlocking { update(transform) }
}
