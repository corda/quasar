package co.paralleluniverse.kotlin.fibers.lang._1_4_x

import co.paralleluniverse.common.util.SystemProperties
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.kotlin.fibers.StaticPropertiesTest.fiberWithVerifyInstrumentationOn
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SAMTest {
    // TODO Check heavy Suspendable so lightly thrown.
    @Suspendable
    private fun interface IntPredicate {
        @Suspendable
        fun accept(i:Int): Boolean
    }
    
    @Suspendable
    private fun doYield() {
        Fiber.yield()
    }

    @Suspendable
    private fun localLambda(a:Int) : Boolean {
        // val isPositive = IntPredicate { doYield(); it > 0}
        val l: (Int) -> Boolean = {doYield(); it > 0}
        return l(a)
    }

    @Suspendable
    private fun localSAM(a:Int) : Boolean {
        val isPositive = IntPredicate { doYield(); it > 0}
        return isPositive.accept(a)
    }

    @Test fun `local lambdas in suspendables`() {
        assertTrue(fiberWithVerifyInstrumentationOn {
            localLambda(4)
        })
        assertFalse(fiberWithVerifyInstrumentationOn {
            localLambda(-4)
        })
    }

    @Ignore("This test break quasar")
    @Test fun `local SAM in suspendables`() {
        // TODO This does not work, need to check what is going on in instrumentation.
        // TODO The above case of local lambda does work, so clearly instrumentation has some lambda awareness.
        assertTrue(fiberWithVerifyInstrumentationOn {
            localSAM(4)
        })
        assertFalse(fiberWithVerifyInstrumentationOn {
            localSAM(-4)
        })
    }
}