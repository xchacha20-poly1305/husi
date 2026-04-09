package fr.husi.test

import fr.husi.di.initHusiKoin
import fr.husi.repository.FakeRepository
import fr.husi.repository.Repository
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class HusiKoinTest {

    @BeforeTest
    fun setUpKoin() = runBlocking {
        preStartKoin()
        initHusiKoin(testRepository())
        postStartKoin()
    }

    @AfterTest
    fun tearDownKoin() = runBlocking {
        preStopKoin()
        stopKoin()
        postStopKoin()
    }

    protected open fun testRepository(): Repository = FakeRepository()

    protected open suspend fun preStartKoin() {}

    protected open suspend fun postStartKoin() {}

    protected open suspend fun preStopKoin() {}

    protected open suspend fun postStopKoin() {}
}
