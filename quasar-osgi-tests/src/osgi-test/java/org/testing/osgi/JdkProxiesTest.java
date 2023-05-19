package org.testing.osgi;

import co.paralleluniverse.io.serialization.kryo.KryoUtil;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.test.common.annotation.InjectBundleContext;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.testing.osgi.proxy.ProxyFactory;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@ExtendWith(BundleContextExtension.class)
@TestInstance(PER_CLASS)
class JdkProxiesTest {
    private static final int BUFFER_SIZE = 8192;
    private static final String TAG = "XXX";

    private static Kryo createKryo(Bundle[] bundles) {
        return KryoUtil.newKryo(new OsgiClassResolver(bundles));
    }

    public interface Api extends Function<Object, String> {
    }

    @Test
    void testReadAndWrite(@InjectBundleContext BundleContext bundleContext) {
        final Api proxy = ProxyFactory.createProxy(Api.class);
        final Kryo kryo = createKryo(bundleContext.getBundles());
        assertThat(proxy).isInstanceOf(AutoCloseable.class);

        final Api result = serializeAndDeserialize(kryo, proxy);
        assertNotSame(proxy, result);
        assertNotEquals(proxy, result);
        assertThat(result.toString())
            .isEqualTo(proxy.toString())
            .isEqualTo("TestProxy[]");
        assertNotEquals(proxy.hashCode(), result.hashCode());
        assertThat(result.apply(TAG))
            .isEqualTo(proxy.apply(TAG))
            .isEqualTo("TestProxy[apply=[" + TAG + "]]");
        assertThat(result).isInstanceOf(AutoCloseable.class);
    }

    @Test
    public void testCopy(@InjectBundleContext BundleContext bundleContext) {
        final Api proxy = ProxyFactory.createProxy(Api.class);
        final Kryo kryo = createKryo(bundleContext.getBundles());

        final Api result = kryo.copy(proxy);
        assertNotSame(proxy, result);
        assertNotEquals(proxy, result);
        assertThat(result.toString())
            .isEqualTo(proxy.toString())
            .isEqualTo("TestProxy[]");
        assertNotEquals(proxy.hashCode(), result.hashCode());
        assertThat(result.apply(TAG))
            .isEqualTo(proxy.apply(TAG))
            .isEqualTo("TestProxy[apply=[" + TAG + "]]");
        assertThat(result).isInstanceOf(AutoCloseable.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T serializeAndDeserialize(Kryo kryo, T source) {
        final byte[] buffer;

        try (Output output = new Output(BUFFER_SIZE)) {
            kryo.writeClassAndObject(output, source);
            buffer = output.getBuffer();
        }

        try (Input input = new Input(buffer)) {
            return (T) kryo.readClassAndObject(input);
        }
    }
}
