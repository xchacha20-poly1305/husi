package fr.husi.ui.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteUrlTestRunnerTest {
    @Test
    fun `requests run concurrently and wait for their own subscribed results`() = runTest {
        val results = mutableMapOf("a" to RemoteUrlTestResult(1, 50))
        val runner = RemoteUrlTestRunner(results::get)
        val started = mutableListOf<String>()
        val first = async {
            runner.test("a") {
                started += "a"
            }
        }
        val second = async {
            runner.test("b") {
                started += "b"
            }
        }
        runCurrent()

        assertEquals(listOf("a", "b"), started)
        assertFalse(first.isCompleted)
        results["a"] = RemoteUrlTestResult(2, 50)
        advanceTimeBy(100.milliseconds)
        runCurrent()

        assertTrue(first.isCompleted)
        assertEquals(listOf("a", "b"), started)
        assertFalse(second.isCompleted)
        results["b"] = RemoteUrlTestResult(2, 80)
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertTrue(second.isCompleted)
    }

    @Test
    fun `test with no observable result eventually releases the queue`() = runTest {
        val runner = RemoteUrlTestRunner { null }
        val started = mutableListOf<String>()
        val first = async {
            runner.test("a") {
                started += "a"
            }
        }
        val second = async {
            runner.test("b") {
                started += "b"
            }
        }
        runCurrent()
        advanceTimeBy(9_999.milliseconds)
        runCurrent()
        assertFalse(first.isCompleted)
        assertEquals(listOf("a", "b"), started)
        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertTrue(first.isCompleted)
        assertEquals(listOf("a", "b"), started)
        assertTrue(second.isCompleted)
    }

    @Test
    fun `cancellation releases the queue without waiting for a result`() = runTest {
        val runner = RemoteUrlTestRunner { null }
        val first = async {
            runner.test("a") {}
        }
        runCurrent()
        first.cancelAndJoin()
        var started = false
        val next = async {
            runner.test("c") {
                started = true
            }
        }
        runCurrent()
        assertTrue(started)
        next.cancelAndJoin()
    }

    @Test
    fun `request failure releases the queue`() = runTest {
        val runner = RemoteUrlTestRunner { null }
        val failed = async {
            runCatching {
                runner.test("a") { error("request failed") }
            }
        }
        var started = false
        val next = async {
            runner.test("b") {
                started = true
            }
        }
        runCurrent()
        assertTrue(failed.await().isFailure)
        assertTrue(started)
        next.cancelAndJoin()
    }

    @Test
    fun `at most ten requests wait for results concurrently`() = runTest {
        val results = mutableMapOf<String, RemoteUrlTestResult>()
        val runner = RemoteUrlTestRunner(results::get)
        val started = mutableListOf<Int>()
        val requests = List(11) { index ->
            async {
                runner.test(index.toString()) {
                    started += index
                }
            }
        }
        runCurrent()
        assertEquals((0 until 10).toList(), started)
        results["0"] = RemoteUrlTestResult(1, 50)
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertTrue(requests[0].isCompleted)
        assertEquals((0 until 11).toList(), started)
        requests.forEach { it.cancelAndJoin() }
    }

    @Test
    fun `removing existing history completes a failed test`() = runTest {
        var result: RemoteUrlTestResult? = RemoteUrlTestResult(1, 50)
        val runner = RemoteUrlTestRunner { result }
        val test = async {
            runner.test("a") {}
        }
        runCurrent()
        result = null
        advanceTimeBy(100.milliseconds)
        runCurrent()
        assertTrue(test.isCompleted)
    }
}
