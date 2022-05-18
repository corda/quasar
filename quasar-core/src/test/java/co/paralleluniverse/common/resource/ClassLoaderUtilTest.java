package co.paralleluniverse.common.resource;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassLoaderUtilTest {
    @Test
    public void testModuleInfoAsClass() {
        assertFalse(ClassLoaderUtil.isClassFile("module-info.class"));
        assertFalse(ClassLoaderUtil.isClassFile("/module-info.class"));
        assertFalse(ClassLoaderUtil.isClassFile("/path/module-info.class"));
    }

    @Test
    public void testAlmostModuleInfoAsClass() {
        assertTrue(ClassLoaderUtil.isClassFile("not-module-info.class"));
        assertTrue(ClassLoaderUtil.isClassFile("/not-module-info.class"));
        assertTrue(ClassLoaderUtil.isClassFile("/path/not-module-info.class"));
    }
}
