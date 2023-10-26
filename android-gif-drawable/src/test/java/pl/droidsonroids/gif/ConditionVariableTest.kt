package pl.droidsonroids.gif

import net.jodah.concurrentunit.Waiter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class ConditionVariableTest {

    @get:Rule
    val timeout = Timeout(TEST_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)

    private lateinit var conditionVariable: ConditionVariable
    private lateinit var waiter: Waiter

    @Before
    fun setUp() {
        conditionVariable = ConditionVariable()
        waiter = Waiter()
    }

    @Test
    fun testBlock() {
        blockAndWait()
    }

    @Test
    fun testOpen() {
        object : Thread() {
            override fun run() {
                conditionVariable.open()
                waiter.resume()
            }
        }.start()
        conditionVariable.block()
        waiter.await()
    }

    @Test
    fun testInitiallyOpened() {
        conditionVariable.set(true)
        conditionVariable.block()
    }

    @Test
    fun testInitiallyClosed() {
        conditionVariable.set(false)
        blockAndWait()
    }

    @Test
    fun testClose() {
        conditionVariable.close()
        blockAndWait()
    }

    private fun blockAndWait() {
        val thread: Thread = object : Thread() {
            override fun run() {
                try {
                    waiter.resume()
                    conditionVariable.block()
                } catch (e: InterruptedException) {
                    currentThread().interrupt()
                    waiter.rethrow(e)
                }
                waiter.fail("ConditionVariable not blocked")
            }
        }
        thread.start()
        thread.join(BLOCK_DURATION.toLong())
        waiter.await()
    }

    companion object {
        private const val TEST_TIMEOUT = 500
        private const val BLOCK_DURATION = 200
    }
}