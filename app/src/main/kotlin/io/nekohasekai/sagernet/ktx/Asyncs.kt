@file:OptIn(DelicateCoroutinesApi::class)

package io.nekohasekai.sagernet.ktx

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun runOnDefaultDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Default, block = block)

suspend fun <T> onDefaultDispatcher(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.Default, block = block)

fun runOnIoDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.IO, block = block)

suspend fun <T> onIoDispatcher(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.IO, block = block)

fun runOnMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Main.immediate, block = block)

suspend fun <T> onMainDispatcher(block: suspend CoroutineScope.() -> T) =
    withContext(Dispatchers.Main.immediate, block = block)
