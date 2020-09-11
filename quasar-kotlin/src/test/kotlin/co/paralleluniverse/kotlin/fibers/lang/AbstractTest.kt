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
import kotlin.test.assertEquals

import co.paralleluniverse.kotlin.fibers.StaticPropertiesTest

abstract class AbstractClass  {

    // Complex template types.
    @Suspendable
    fun complex(m: MyMap, b: Boolean): MyMap {
        Fiber.yield()
        return if (b) {
            m
        } else {
            mapOf<Int, Int>(6 to 7)
        }
    }

    @Suspendable
    abstract fun complexabs(m: MyMap, b: Boolean): MyMap

    // Primitve types.
    @Suspendable
    fun primitive(a: Int, b: Int): Int {
        Fiber.yield()
        return a + b
    }

    @Suspendable
    abstract fun primitiveabs(a: Int, b: Int): Int
}

class ConcreteClass : AbstractClass() {
    @Suspendable
    override fun complexabs(m: MyMap, b: Boolean): MyMap {
        Fiber.yield()
        return if (b) {
            m
        } else {
            mapOf<Int, Int>(1 to 1)
        }
    }

    // Does this have to be marked suspendable when overriding ?
    @Suspendable
    override fun primitiveabs(a: Int, b: Int): Int {
        Fiber.yield()
        return a - b
    }
}

class AbstractTest {

    @Test fun `abstract primitive`() {

        StaticPropertiesTest.withVerifyInstrumentationOn {

            Assume.assumeTrue(SystemProperties.isEmptyOrTrue(StaticPropertiesTest.verifyInstrumentationKey))

            val fiber = object : Fiber<Int>() {
                val ac : AbstractClass = ConcreteClass()
                @Suspendable
                override fun run(): Int {
                    return ac.primitive(4,5)
                }
            }

            val actual = fiber.start().get()
            assertEquals(9, actual)
        }
    }

    @Test fun `abstract primitive abs`() {

        StaticPropertiesTest.withVerifyInstrumentationOn {

            Assume.assumeTrue(SystemProperties.isEmptyOrTrue(StaticPropertiesTest.verifyInstrumentationKey))

            val fiber = object : Fiber<Int>() {
                val ac : AbstractClass = ConcreteClass()
                @Suspendable
                override fun run(): Int {
                    return ac.primitiveabs(6,5)
                }
            }

            val actual = fiber.start().get()
            assertEquals(1, actual)
        }
    }

    @Test fun `abstract complex`() {

        StaticPropertiesTest.withVerifyInstrumentationOn {

            Assume.assumeTrue(SystemProperties.isEmptyOrTrue(StaticPropertiesTest.verifyInstrumentationKey))

            val fiber = object : Fiber<MyMap>() {
                val ac : AbstractClass = ConcreteClass()
                @Suspendable
                override fun run(): MyMap {
                    return ac.complex(mapOf(1 to 4, 5 to 5), true)
                }
            }

            val actual = fiber.start().get()
            assertEquals(mapOf(1 to 4, 5 to 5), actual)
        }
    }

    @Test fun `abstract complex abs`() {

        StaticPropertiesTest.withVerifyInstrumentationOn {

            Assume.assumeTrue(SystemProperties.isEmptyOrTrue(StaticPropertiesTest.verifyInstrumentationKey))

            val fiber = object : Fiber<MyMap>() {
                val ac : AbstractClass = ConcreteClass()
                @Suspendable
                override fun run(): MyMap {
                    return ac.complexabs(mapOf(1 to 11), false)
                }
            }

            val actual = fiber.start().get()
            assertEquals(mapOf(1 to 1), actual)
        }
    }

}
