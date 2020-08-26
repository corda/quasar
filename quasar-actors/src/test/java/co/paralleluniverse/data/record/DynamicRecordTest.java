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
import static org.junit.Assume.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import co.paralleluniverse.data.record.Field.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runners.Parameterized;

/**
 *
 * @author pron
 */
@RunWith(Parameterized.class)
public class DynamicRecordTest {
    @Rule
    public TestName name = new TestName();
    @Rule
    public TestRule watchman = TestUtil.WATCHMAN;

    private static final Random rand = new Random();
    private final RecordType.Mode mode;

    public DynamicRecordTest(RecordType.Mode mode) {
        this.mode = mode;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {RecordType.Mode.METHOD_HANDLE},
            {RecordType.Mode.REFLECTION},
            {RecordType.Mode.UNSAFE},
            {RecordType.Mode.GENERATION},});
    }

    @Before
    public void setUp() {
    }

    public static class A {
        public boolean a;
        public byte b;
        public short c;
        public int d;
        public long e;
        public float f;
        public double g;
        public char h;
        public String str;
        public boolean[] aa;
        public byte[] ba;
        public final short[] ca;
        public int[] da;
        public long[] ea;
        public final float[] fa;
        public double[] ga;
        public char[] ha;
        public String[] stra;
        public final boolean af;
        public final byte bf;
        public final short cf;
        public final int df;
        public final long ef;
        public final float ff;
        public final double gf;
        public final char hf;
        public final String strf;
        public int x;

        public A() {
            a = rand.nextBoolean();
            b = (byte) rand.nextInt();
            c = (short) rand.nextInt();
            d = rand.nextInt();
            e = rand.nextLong();
            f = rand.nextFloat();
            g = rand.nextDouble();
            h = (char) rand.nextInt();
            str = "foo";

            aa = new boolean[1];
            ba = new byte[2];
            ca = new short[3];
            da = new int[4];
            ea = new long[5];
            fa = new float[6];
            ga = new double[7];
            ha = new char[8];
            stra = new String[]{"hello", "world"};

            for (int i = 0; i < aa.length; i++)
                aa[i] = rand.nextBoolean();
            for (int i = 0; i < ba.length; i++)
                ba[i] = (byte) rand.nextInt();
            for (int i = 0; i < ca.length; i++)
                ca[i] = (short) rand.nextInt();
            for (int i = 0; i < da.length; i++)
                da[i] = rand.nextInt();
            for (int i = 0; i < ea.length; i++)
                ea[i] = rand.nextLong();
            for (int i = 0; i < fa.length; i++)
                fa[i] = rand.nextFloat();
            for (int i = 0; i < aa.length; i++)
                ga[i] = rand.nextDouble();
            for (int i = 0; i < ha.length; i++)
                ha[i] = (char) rand.nextInt();

            af = rand.nextBoolean();
            bf = (byte) rand.nextInt();
            cf = (short) rand.nextInt();
            df = rand.nextInt();
            ef = rand.nextLong();
            ff = rand.nextFloat();
            gf = rand.nextDouble();
            hf = (char) rand.nextInt();
            strf = "foof";

            x = rand.nextInt();
        }
    }

    public static class B extends A {
        public boolean isA() {
            return a;
        }

        public void setA(boolean a) {
            this.a = a;
        }

        public byte getB() {
            return (byte) (b + 1);
        }

        public void setB(byte b) {
            this.b = (byte) (b + 1);
        }

        public short getC() {
            return (short) (c + 1);
        }

        public void setC(short c) {
            this.c = (short) (c + 1);
        }

        public int getD() {
            return d + 1;
        }

        public void setD(int d) {
            this.d = d + 1;
        }

        public long getE() {
            return e + 1;
        }

        public void setE(long e) {
            this.e = e + 1;
        }

        public float getF() {
            return f + 1;
        }

        public void setF(float f) {
            this.f = f + 1;
        }

        public double getG() {
            return g + 1;
        }

        public void setG(double g) {
            this.g = g + 1;
        }

        public char getH() {
            return (char) (h + 1);
        }

        public void setH(char h) {
            this.h = (char) (h + 1);
        }

        public String getStr() {
            return str + "!";
        }

        public void setStr(String str) {
            this.str = str + "!";
        }

        public boolean getAa(int index) {
            return aa[index];
        }

        public void setAa(int index, boolean a) {
            this.a = a;
        }

        public byte getBa(int index) {
            return (byte) (ba[index] + 1);
        }

        public void setBa(int index, byte b) {
            this.b = (byte) (b + 1);
        }

        public short getCa(int index) {
            return (short) (ca[index] + 1);
        }

        public void setCa(int index, short c) {
            this.c = (short) (c + 1);
        }

        public int getDa(int index) {
            return da[index] + 1;
        }

        public void setDa(int index, int d) {
            this.d = d + 1;
        }

        public long getEa(int index) {
            return ea[index] + 1;
        }

        public void setEa(int index, long e) {
            this.e = e + 1;
        }

        public float getFa(int index) {
            return fa[index] + 1;
        }

        public void setFa(int index, float f) {
            this.f = f + 1;
        }

        public double getGa(int index) {
            return ga[index] + 1;
        }

        public void setGa(int index, double g) {
            this.g = g + 1;
        }

        public char getHa(int index) {
            return (char) (ha[index] + 1);
        }

        public void setHa(int index, char h) {
            this.h = (char) (h + 1);
        }

        public String getStra(int index) {
            return stra[index] + "!";
        }

        public void setStra(int index, String str) {
            stra[index] = str + "!";
        }

        public int getX() {
            return x + 1;
        }
    }
    private final RecordType<A> rt = RecordType.newType(A.class);
    private final BooleanField<A> $a = rt.booleanField("a");
    private final ByteField<A> $b = rt.byteField("b");
    private final ShortField<A> $c = rt.shortField("c");
    private final IntField<A> $d = rt.intField("d");
    private final LongField<A> $e = rt.longField("e");
    private final FloatField<A> $f = rt.floatField("f");
    private final DoubleField<A> $g = rt.doubleField("g");
    private final CharField<A> $h = rt.charField("h");
    private final ObjectField<A, String> $str = rt.objectField("str", String.class);
    private final BooleanArrayField<A> $aa = rt.booleanArrayField("aa", 1);
    private final ByteArrayField<A> $ba = rt.byteArrayField("ba", 2);
    private final ShortArrayField<A> $ca = rt.shortArrayField("ca", 3);
    private final IntArrayField<A> $da = rt.intArrayField("da", 4);
    private final LongArrayField<A> $ea = rt.longArrayField("ea", 5);
    private final FloatArrayField<A> $fa = rt.floatArrayField("fa", 6);
    private final DoubleArrayField<A> $ga = rt.doubleArrayField("ga", 7);
    private final CharArrayField<A> $ha = rt.charArrayField("ha", 8);
    private final ObjectArrayField<A, String> $stra = rt.objectArrayField("stra", String.class, 2);
    private final BooleanField<A> $af = rt.booleanField("af");
    private final ByteField<A> $bf = rt.byteField("bf");
    private final ShortField<A> $cf = rt.shortField("cf");
    private final IntField<A> $df = rt.intField("df");
    private final LongField<A> $ef = rt.longField("ef");
    private final FloatField<A> $ff = rt.floatField("ff");
    private final DoubleField<A> $gf = rt.doubleField("gf");
    private final CharField<A> $hf = rt.charField("hf");
    private final ObjectField<A, String> $strf = rt.objectField("strf", String.class);
    private final IntField<A> $x = rt.intField("x");

    @After
    public void tearDown() {
    }

    //////////// General //////////////////
    @Test
    public void testSetDirectGetRecord() {
        A a = new A();
        Record<A> r = rt.wrap(a, mode);

        assertThat(r.get($a), equalTo(a.a));
        assertThat(r.get($b), equalTo(a.b));
        assertThat(r.get($c), equalTo(a.c));
        assertThat(r.get($d), equalTo(a.d));
        assertThat(r.get($e), equalTo(a.e));
        assertThat(r.get($f), equalTo(a.f));
        assertThat(r.get($g), equalTo(a.g));
        assertThat(r.get($h), equalTo(a.h));
        assertThat(r.get($str), equalTo(a.str));

        assertThat(r.get($af), equalTo(a.af));
        assertThat(r.get($bf), equalTo(a.bf));
        assertThat(r.get($cf), equalTo(a.cf));
        assertThat(r.get($df), equalTo(a.df));
        assertThat(r.get($ef), equalTo(a.ef));
        assertThat(r.get($ff), equalTo(a.ff));
        assertThat(r.get($gf), equalTo(a.gf));
        assertThat(r.get($hf), equalTo(a.hf));
        assertThat(r.get($strf), equalTo(a.strf));

        assertThat(r.get($x), equalTo(a.x));

        for (int i = 0; i < $aa.length; i++)
            assertThat(r.get($aa, i), equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(r.get($ba, i), equalTo(a.ba[i]));
        for (int i = 0; i < $ca.length; i++)
            assertThat(r.get($ca, i), equalTo(a.ca[i]));
        for (int i = 0; i < $da.length; i++)
            assertThat(r.get($da, i), equalTo(a.da[i]));
        for (int i = 0; i < $ea.length; i++)
            assertThat(r.get($ea, i), equalTo(a.ea[i]));
        for (int i = 0; i < $fa.length; i++)
            assertThat(r.get($fa, i), equalTo(a.fa[i]));
        for (int i = 0; i < $ga.length; i++)
            assertThat(r.get($ga, i), equalTo(a.ga[i]));
        for (int i = 0; i < $ha.length; i++)
            assertThat(r.get($ha, i), equalTo(a.ha[i]));
        for (int i = 0; i < $stra.length; i++)
            assertThat(r.get($stra, i), equalTo(a.stra[i]));

        boolean[] aa = new boolean[1];
        byte[] ba = new byte[2];
        short[] ca = new short[3];
        int[] da = new int[4];
        long[] ea = new long[5];
        float[] fa = new float[6];
        double[] ga = new double[7];
        char[] ha = new char[8];
        String[] stra = new String[2];

        r.get($aa, aa, 0);
        r.get($ba, ba, 0);
        r.get($ca, ca, 0);
        r.get($da, da, 0);
        r.get($ea, ea, 0);
        r.get($fa, fa, 0);
        r.get($ga, ga, 0);
        r.get($ha, ha, 0);
        r.get($stra, stra, 0);

        assertThat(aa, equalTo(a.aa));
        assertThat(ba, equalTo(a.ba));
        assertThat(ca, equalTo(a.ca));
        assertThat(da, equalTo(a.da));
        assertThat(ea, equalTo(a.ea));
        assertThat(fa, equalTo(a.fa));
        assertThat(ga, equalTo(a.ga));
        assertThat(ha, equalTo(a.ha));
        assertThat(stra, equalTo(a.stra));
    }

    @Test
    public void defaultModeForAIsUnsafe() {
        assumeThat(mode, is(RecordType.Mode.METHOD_HANDLE));

        A a = new A();

        Record<A> r = rt.wrap(a);
        assertThat(r, instanceOf(DynamicUnsafeRecord.class));

        rt.wrap(a, RecordType.Mode.UNSAFE);
    }

    @Test
    public void defaultModeForBIsGeneration() {
        assumeThat(mode, is(RecordType.Mode.METHOD_HANDLE));

        A a = new B();

        Record<A> r = rt.wrap(a);
        assertThat(r, instanceOf(DynamicGeneratedRecord.class));

        rt.wrap(a, RecordType.Mode.GENERATION);
    }

    @Test
    public void whenUnsafeAndBeanThenThrowException() {
        assumeThat(mode, is(RecordType.Mode.UNSAFE));

        try {
            A a = new B();
            Record<A> r = rt.wrap(a, mode);
            fail();
        } catch (Exception e) {
        }
    }

    @Test
    public void testSetDirectGetRecordBean() {
        assumeThat(mode, not(RecordType.Mode.UNSAFE));

        A a = new B();
        Record<A> r = rt.wrap(a, mode);

        assertThat(r.get($a), equalTo(a.a));
        assertThat(r.get($b), equalTo((byte) (a.b + 1)));
        assertThat(r.get($c), equalTo((short) (a.c + 1)));
        assertThat(r.get($d), equalTo(a.d + 1));
        assertThat(r.get($e), equalTo(a.e + 1));
        assertThat(r.get($f), equalTo(a.f + 1));
        assertThat(r.get($g), equalTo(a.g + 1));
        assertThat(r.get($h), equalTo((char) (a.h + 1)));
        assertThat(r.get($str), equalTo(a.str + "!"));

        for (int i = 0; i < $aa.length; i++)
            assertThat(r.get($aa, i), equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(r.get($ba, i), equalTo((byte) (a.ba[i] + 1)));
        for (int i = 0; i < $ca.length; i++)
            assertThat(r.get($ca, i), equalTo((short) (a.ca[i] + 1)));
        for (int i = 0; i < $da.length; i++)
            assertThat(r.get($da, i), equalTo(a.da[i] + 1));
        for (int i = 0; i < $ea.length; i++)
            assertThat(r.get($ea, i), equalTo(a.ea[i] + 1));
        for (int i = 0; i < $fa.length; i++)
            assertThat(r.get($fa, i), equalTo(a.fa[i] + 1));
        for (int i = 0; i < $ga.length; i++)
            assertThat(r.get($ga, i), equalTo(a.ga[i] + 1));
        for (int i = 0; i < $ha.length; i++)
            assertThat(r.get($ha, i), equalTo((char) (a.ha[i] + 1)));
        for (int i = 0; i < $stra.length; i++)
            assertThat(r.get($stra, i), equalTo(a.stra[i] + "!"));

        boolean[] aa = new boolean[1];
        byte[] ba = new byte[2];
        short[] ca = new short[3];
        int[] da = new int[4];
        long[] ea = new long[5];
        float[] fa = new float[6];
        double[] ga = new double[7];
        char[] ha = new char[8];
        String[] stra = new String[2];

        r.get($aa, aa, 0);
        r.get($ba, ba, 0);
        r.get($ca, ca, 0);
        r.get($da, da, 0);
        r.get($ea, ea, 0);
        r.get($fa, fa, 0);
        r.get($ga, ga, 0);
        r.get($ha, ha, 0);
        r.get($stra, stra, 0);

        for (int i = 0; i < $aa.length; i++)
            assertThat(aa[i], equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(ba[i], equalTo((byte) (a.ba[i] + 1)));
        for (int i = 0; i < $ca.length; i++)
            assertThat(ca[i], equalTo((short) (a.ca[i] + 1)));
        for (int i = 0; i < $da.length; i++)
            assertThat(da[i], equalTo(a.da[i] + 1));
        for (int i = 0; i < $ea.length; i++)
            assertThat(ea[i], equalTo(a.ea[i] + 1));
        for (int i = 0; i < $fa.length; i++)
            assertThat(fa[i], equalTo(a.fa[i] + 1));
        for (int i = 0; i < $ga.length; i++)
            assertThat(ga[i], equalTo(a.ga[i] + 1));
        for (int i = 0; i < $ha.length; i++)
            assertThat(ha[i], equalTo((char) (a.ha[i] + 1)));
        for (int i = 0; i < $stra.length; i++)
            assertThat(stra[i], equalTo(a.stra[i] + "!"));

        assertThat(r.get($x), equalTo(a.x + 1));
    }

    @Test
    public void testSetRecordGetDirect() {
        A a = new A();
        Record<A> r = rt.wrap(a, mode);

        r.set($a, rand.nextBoolean());
        r.set($b, (byte) rand.nextInt());
        r.set($c, (short) rand.nextInt());
        r.set($d, rand.nextInt());
        r.set($e, rand.nextLong());
        r.set($f, rand.nextFloat());
        r.set($g, rand.nextDouble());
        r.set($h, (char) rand.nextInt());
        r.set($str, "bar");

        try {
            r.set($af, rand.nextBoolean());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($bf, (byte) rand.nextInt());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($cf, (short) rand.nextInt());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($df, rand.nextInt());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($ef, rand.nextLong());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($ff, rand.nextFloat());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($gf, rand.nextDouble());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($hf, (char) rand.nextInt());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
        try {
            r.set($strf, "bar");
            fail();
        } catch (ReadOnlyFieldException e) {
        }

        r.set($x, rand.nextInt());

        for (int i = 0; i < $aa.length; i++)
            r.set($aa, i, rand.nextBoolean());
        for (int i = 0; i < $ba.length; i++)
            r.set($ba, i, (byte) rand.nextInt());
        for (int i = 0; i < $ca.length; i++)
            r.set($ca, i, (short) rand.nextInt());
        for (int i = 0; i < $da.length; i++)
            r.set($da, i, rand.nextInt());
        for (int i = 0; i < $ea.length; i++)
            r.set($ea, i, rand.nextLong());
        for (int i = 0; i < $fa.length; i++)
            r.set($fa, i, rand.nextFloat());
        for (int i = 0; i < $ga.length; i++)
            r.set($ga, i, rand.nextDouble());
        for (int i = 0; i < $ha.length; i++)
            r.set($ha, i, (char) rand.nextInt());

        r.set($stra, 0, "goodbye");

        assertThat(r.get($a), equalTo(a.a));
        assertThat(r.get($b), equalTo(a.b));
        assertThat(r.get($c), equalTo(a.c));
        assertThat(r.get($d), equalTo(a.d));
        assertThat(r.get($e), equalTo(a.e));
        assertThat(r.get($f), equalTo(a.f));
        assertThat(r.get($g), equalTo(a.g));
        assertThat(r.get($h), equalTo(a.h));
        assertThat(a.str, equalTo("bar"));

        for (int i = 0; i < $aa.length; i++)
            assertThat(r.get($aa, i), equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(r.get($ba, i), equalTo(a.ba[i]));
        for (int i = 0; i < $ca.length; i++)
            assertThat(r.get($ca, i), equalTo(a.ca[i]));
        for (int i = 0; i < $da.length; i++)
            assertThat(r.get($da, i), equalTo(a.da[i]));
        for (int i = 0; i < $ea.length; i++)
            assertThat(r.get($ea, i), equalTo(a.ea[i]));
        for (int i = 0; i < $fa.length; i++)
            assertThat(r.get($fa, i), equalTo(a.fa[i]));
        for (int i = 0; i < $ga.length; i++)
            assertThat(r.get($ga, i), equalTo(a.ga[i]));
        for (int i = 0; i < $ha.length; i++)
            assertThat(r.get($ha, i), equalTo(a.ha[i]));
        for (int i = 0; i < $stra.length; i++)
            assertThat(r.get($stra, i), equalTo(a.stra[i]));

        assertThat(r.get($stra, 0), equalTo("goodbye"));

        assertThat(r.get($x), equalTo(a.x));
    }

    @Test
    public void testSetRecordBeanGetDirect() {
        assumeThat(mode, not(RecordType.Mode.UNSAFE));

        A a = new B();
        Record<A> r = rt.wrap(a, mode);

        r.set($a, rand.nextBoolean());
        r.set($b, (byte) rand.nextInt());
        r.set($c, (short) rand.nextInt());
        r.set($d, rand.nextInt());
        r.set($e, rand.nextLong());
        r.set($f, rand.nextFloat());
        r.set($g, rand.nextDouble());
        r.set($h, (char) rand.nextInt());
        r.set($str, "bar");

        for (int i = 0; i < $aa.length; i++)
            r.set($aa, i, rand.nextBoolean());
        for (int i = 0; i < $ba.length; i++)
            r.set($ba, i, (byte) rand.nextInt());
        for (int i = 0; i < $ca.length; i++)
            r.set($ca, i, (short) rand.nextInt());
        for (int i = 0; i < $da.length; i++)
            r.set($da, i, rand.nextInt());
        for (int i = 0; i < $ea.length; i++)
            r.set($ea, i, rand.nextLong());
        for (int i = 0; i < $fa.length; i++)
            r.set($fa, i, rand.nextFloat());
        for (int i = 0; i < $ga.length; i++)
            r.set($ga, i, rand.nextDouble());
        for (int i = 0; i < $ha.length; i++)
            r.set($ha, i, (char) rand.nextInt());

        r.set($stra, 0, "goodbye");

        assertThat(r.get($a), equalTo(a.a));
        assertThat(r.get($b), equalTo((byte) (a.b + 1)));
        assertThat(r.get($c), equalTo((short) (a.c + 1)));
        assertThat(r.get($d), equalTo(a.d + 1));
        assertThat(r.get($e), equalTo(a.e + 1));
        assertThat(r.get($f), equalTo(a.f + 1));
        assertThat(r.get($g), equalTo(a.g + 1));
        assertThat(r.get($h), equalTo((char) (a.h + 1)));
        assertThat(a.str, equalTo("bar!"));

        for (int i = 0; i < $aa.length; i++)
            assertThat(r.get($aa, i), equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(r.get($ba, i), equalTo((byte) (a.ba[i] + 1)));
        for (int i = 0; i < $ca.length; i++)
            assertThat(r.get($ca, i), equalTo((short) (a.ca[i] + 1)));
        for (int i = 0; i < $da.length; i++)
            assertThat(r.get($da, i), equalTo(a.da[i] + 1));
        for (int i = 0; i < $ea.length; i++)
            assertThat(r.get($ea, i), equalTo(a.ea[i] + 1));
        for (int i = 0; i < $fa.length; i++)
            assertThat(r.get($fa, i), equalTo(a.fa[i] + 1));
        for (int i = 0; i < $ga.length; i++)
            assertThat(r.get($ga, i), equalTo(a.ga[i] + 1));
        for (int i = 0; i < $ha.length; i++)
            assertThat(r.get($ha, i), equalTo((char) (a.ha[i] + 1)));
        for (int i = 0; i < $stra.length; i++)
            assertThat(r.get($stra, i), equalTo(a.stra[i] + "!"));

        assertThat(r.get($stra, 0), equalTo("goodbye!!"));

        try {
            r.set($x, rand.nextInt());
            fail();
        } catch (ReadOnlyFieldException e) {
        }
    }

    @Test
    public void testSetRecordGetDirect2() {
        A a = new A();
        Record<A> r = rt.wrap(a, mode);

        boolean[] aa = new boolean[1];
        byte[] ba = new byte[2];
        short[] ca = new short[3];
        int[] da = new int[4];
        long[] ea = new long[5];
        float[] fa = new float[6];
        double[] ga = new double[7];
        char[] ha = new char[8];
        String[] stra = new String[]{"foo", "bar"};

        for (int i = 0; i < aa.length; i++)
            aa[i] = rand.nextBoolean();
        for (int i = 0; i < ba.length; i++)
            ba[i] = (byte) rand.nextInt();
        for (int i = 0; i < ca.length; i++)
            ca[i] = (short) rand.nextInt();
        for (int i = 0; i < da.length; i++)
            da[i] = rand.nextInt();
        for (int i = 0; i < ea.length; i++)
            ea[i] = rand.nextLong();
        for (int i = 0; i < fa.length; i++)
            fa[i] = rand.nextFloat();
        for (int i = 0; i < aa.length; i++)
            ga[i] = rand.nextDouble();
        for (int i = 0; i < ha.length; i++)
            ha[i] = (char) rand.nextInt();

        r.set($aa, aa, 0);
        r.set($ba, ba, 0);
        r.set($ca, ca, 0);
        r.set($da, da, 0);
        r.set($ea, ea, 0);
        r.set($fa, fa, 0);
        r.set($ga, ga, 0);
        r.set($ha, ha, 0);
        r.set($stra, stra, 0);

        for (int i = 0; i < $aa.length; i++)
            assertThat(r.get($aa, i), equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(r.get($ba, i), equalTo(a.ba[i]));
        for (int i = 0; i < $ca.length; i++)
            assertThat(r.get($ca, i), equalTo(a.ca[i]));
        for (int i = 0; i < $da.length; i++)
            assertThat(r.get($da, i), equalTo(a.da[i]));
        for (int i = 0; i < $ea.length; i++)
            assertThat(r.get($ea, i), equalTo(a.ea[i]));
        for (int i = 0; i < $fa.length; i++)
            assertThat(r.get($fa, i), equalTo(a.fa[i]));
        for (int i = 0; i < $ga.length; i++)
            assertThat(r.get($ga, i), equalTo(a.ga[i]));
        for (int i = 0; i < $ha.length; i++)
            assertThat(r.get($ha, i), equalTo(a.ha[i]));
        for (int i = 0; i < $stra.length; i++)
            assertThat(r.get($stra, i), equalTo(a.stra[i]));

        assertThat(r.get($stra, 0), equalTo("foo"));
        assertThat(r.get($stra, 1), equalTo("bar"));
    }

    @Test
    public void testSetRecordBeanGetDirect2() {
        assumeThat(mode, not(RecordType.Mode.UNSAFE));

        A a = new B();
        Record<A> r = rt.wrap(a, mode);

        boolean[] aa = new boolean[1];
        byte[] ba = new byte[2];
        short[] ca = new short[3];
        int[] da = new int[4];
        long[] ea = new long[5];
        float[] fa = new float[6];
        double[] ga = new double[7];
        char[] ha = new char[8];
        String[] stra = new String[]{"foo", "bar"};

        for (int i = 0; i < aa.length; i++)
            aa[i] = rand.nextBoolean();
        for (int i = 0; i < ba.length; i++)
            ba[i] = (byte) rand.nextInt();
        for (int i = 0; i < ca.length; i++)
            ca[i] = (short) rand.nextInt();
        for (int i = 0; i < da.length; i++)
            da[i] = rand.nextInt();
        for (int i = 0; i < ea.length; i++)
            ea[i] = rand.nextLong();
        for (int i = 0; i < fa.length; i++)
            fa[i] = rand.nextFloat();
        for (int i = 0; i < aa.length; i++)
            ga[i] = rand.nextDouble();
        for (int i = 0; i < ha.length; i++)
            ha[i] = (char) rand.nextInt();

        r.set($aa, aa, 0);
        r.set($ba, ba, 0);
        r.set($ca, ca, 0);
        r.set($da, da, 0);
        r.set($ea, ea, 0);
        r.set($fa, fa, 0);
        r.set($ga, ga, 0);
        r.set($ha, ha, 0);
        r.set($stra, stra, 0);

        for (int i = 0; i < $aa.length; i++)
            assertThat(r.get($aa, i), equalTo(a.aa[i]));
        for (int i = 0; i < $ba.length; i++)
            assertThat(r.get($ba, i), equalTo((byte) (a.ba[i] + 1)));
        for (int i = 0; i < $ca.length; i++)
            assertThat(r.get($ca, i), equalTo((short) (a.ca[i] + 1)));
        for (int i = 0; i < $da.length; i++)
            assertThat(r.get($da, i), equalTo(a.da[i] + 1));
        for (int i = 0; i < $ea.length; i++)
            assertThat(r.get($ea, i), equalTo(a.ea[i] + 1));
        for (int i = 0; i < $fa.length; i++)
            assertThat(r.get($fa, i), equalTo(a.fa[i] + 1));
        for (int i = 0; i < $ga.length; i++)
            assertThat(r.get($ga, i), equalTo(a.ga[i] + 1));
        for (int i = 0; i < $ha.length; i++)
            assertThat(r.get($ha, i), equalTo((char) (a.ha[i] + 1)));
        for (int i = 0; i < $stra.length; i++)
            assertThat(r.get($stra, i), equalTo(a.stra[i] + "!"));

        assertThat(r.get($stra, 0), equalTo("foo!!"));
        assertThat(r.get($stra, 1), equalTo("bar!!"));
    }

    @Test
    public void testArrayOutOfBounds() {
        A a = new A();
        Record<A> r = rt.wrap(a, mode);

        try {
            r.get($aa, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($aa, 1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($aa, -1, false);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($aa, 1, false);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ba, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ba, 2);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ba, -1, (byte) 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ba, 2, (byte) 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ca, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ca, 3);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ca, -1, (short) 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ca, 3, (short) 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($da, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($da, 4);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($da, -1, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($da, 4, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ea, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ea, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ea, -1, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ea, 5, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($fa, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($fa, 6);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($fa, -1, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($fa, 6, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ga, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ga, 7);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ga, -1, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ga, 7, 0);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ha, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($ha, 8);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ha, -1, 'x');
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($ha, 8, 'x');
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($stra, -1);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.get($stra, 2);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($stra, -1, "x");
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        try {
            r.set($stra, 2, "x");
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
        }
    }
}
