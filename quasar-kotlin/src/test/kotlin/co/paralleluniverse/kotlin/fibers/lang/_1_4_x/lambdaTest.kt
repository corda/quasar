package co.paralleluniverse.kotlin.fibers.lang._1_4_x

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class lambdaTest {

    private fun fib(a:Int) : Int {
        return {num: Int -> when(num) {
            0 -> {num}
            1 -> {num}
            else -> {fib(num-2) + fib(num-1)}
        }}(a)
    }

    @Test fun `local lambda fib`(){
        // This lambda will be instrumented !!
        assertEquals(34, {num:Int -> fib(num)}(9))
    }

    @Test fun `local lambda positive`(){
        // This lambda will be instrumented !!
        assertTrue { { num: Int -> num >= 0}(5) }
    }

    @Test fun `local lambda even`(){
        // This lambda will be instrumented !!
        assertTrue { { num: Int -> (num % 2) == 0}(6) }
    }

    @Test fun `dinner1`() {
        assertTrue(true)
    }
}
