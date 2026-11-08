package fr.husi.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
abstract class HusiKoinMainDispatcherTest : HusiKoinTest() {

    protected val dispatcher = StandardTestDispatcher()

    final override suspend fun preStartKoin() {
        Dispatchers.setMain(dispatcher)
        preStartKoinWithMainDispatcher()
    }

    final override suspend fun postStopKoin() {
        try {
            postStopKoinWithMainDispatcher()
        } finally {
            Dispatchers.resetMain()
        }
    }

    protected open suspend fun preStartKoinWithMainDispatcher() {}

    protected open suspend fun postStopKoinWithMainDispatcher() {}
}
