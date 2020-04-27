/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2015-2017, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.kotlin.fibers.lang

import co.paralleluniverse.common.util.SystemProperties
import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertNotNull

class MethodOverloadTest {

    @Suspendable
    fun function() {
        function(arrayOf(0))
    }

    @Suspendable
    fun function(m: Any) {
        Fiber.yield()
    }

    private val verifyInstrumentation = "co.paralleluniverse.fibers.verifyInstrumentation"

    @Test fun methodOverloadTest() {

        withVerifyInstrumentationOn {
            Assume.assumeTrue(SystemProperties.isEmptyOrTrue(verifyInstrumentation))

            val fiber = object : Fiber<Any>() {
                @Suspendable
                override fun run(): Any {
                    return function(object {})
                }
            }

            val actual = fiber.start().get()
            assertNotNull(actual)
        }
    }

    private fun <T> withVerifyInstrumentationOn(statement: () -> T): T{
        val originalValue = System.getProperty(verifyInstrumentation)
        System.setProperty(verifyInstrumentation, "true")
        try {
            return statement()
        } finally {
            if (originalValue == null) {
                System.clearProperty(verifyInstrumentation)
            } else {
                System.setProperty(verifyInstrumentation, originalValue)
            }
        }
    }

}
