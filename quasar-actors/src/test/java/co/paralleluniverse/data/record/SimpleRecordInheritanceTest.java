/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
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
package co.paralleluniverse.data.record;

import co.paralleluniverse.common.test.TestUtil;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import co.paralleluniverse.data.record.Field.*;
import java.util.Random;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;

/**
 *
 * @author pron
 */
//@RunWith(org.mockito.runners.MockitoJUnitRunner.class) //VerboseMockitoJUnitRunner.class)
public class SimpleRecordInheritanceTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;
    
    private static final Random rand = new Random();

    public SimpleRecordInheritanceTest() {
    }

    @Before
    public void setUp() {
    }

    private static class A {
        static final RecordType<A> rt = RecordType.newType(A.class);
        static final BooleanField<A> $a = rt.booleanField("a");
        static final IntField<A> $d = rt.intField("d");
        static final ObjectField<A, String> $str = rt.objectField("str", String.class);
        static final DoubleArrayField<A> $ga = rt.doubleArrayField("ga", 2);
        static final ObjectArrayField<A, String> $stra = rt.objectArrayField("stra", String.class, 2);
    }

    private static class B extends A {
        static final RecordType<B> rt = RecordType.newType(B.class, A.rt);
        static final IntField<B> $d1 = rt.intField("d1");
        static final ObjectField<B, String> $str1 = rt.objectField("str1", String.class);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void test1() {
        Record<B> b = B.rt.newInstance();
        b.set(B.$a, true);
        b.set(A.$d, 15);
        b.set(B.$str, "hi");
        b.set(A.$ga, 0, 0.1);
        b.set(B.$ga, 1, 0.2);
        b.set(A.$stra, 0, "a1");
        b.set(B.$stra, 1, "a2");
        b.set(B.$d1, 42);
        b.set(B.$str1, "bye");

        assertThat(b.get(A.$a), equalTo(true));
        assertThat(b.get(B.$d), equalTo(15));
        assertThat(b.get(A.$str), equalTo("hi"));
        assertThat(b.get(B.$ga, 0), equalTo(0.1));
        assertThat(b.get(A.$ga, 1), equalTo(0.2));
        assertThat(b.get(B.$stra, 0), equalTo("a1"));
        assertThat(b.get(A.$stra, 1), equalTo("a2"));
        assertThat(b.get(B.$d1), equalTo(42));
        assertThat(b.get(B.$str1), equalTo("bye"));
    }

    @Test
    public void testIsInstance() {
        Record<B> b = B.rt.newInstance();
        Record<A> a = A.rt.newInstance();

        assertThat(B.rt.isInstance(b), is(true));
        assertThat(A.rt.isInstance(b), is(true));
        assertThat(B.rt.isInstance(a), is(false));
        assertThat(A.rt.isInstance(a), is(true));
    }
}
