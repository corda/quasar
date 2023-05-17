package co.paralleluniverse.io.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class JdkProxySerializerTest {
    private static final int BUFFER_SIZE = 8192;

    private final Kryo kryo = KryoUtil.newKryo(new DefaultClassResolver());

    @SuppressWarnings("unchecked")
    private Function<Object, String> createProxy() {
        return (Function<Object, String>) Proxy.newProxyInstance(
            ClassLoader.getSystemClassLoader(),
            new Class[] { Function.class },
            (InvocationHandler & Serializable) (obj, method, args) -> {
                final String methodName = method.getName();
                switch (methodName) {
                    case "toString":
                        return "MyProxy";
                    case "equals":
                        return obj == args[0];
                    case "hashCode":
                        return System.identityHashCode(obj);
                    case "apply":
                        return "Proxy[apply=" + args[0] + ']';
                    default:
                        return null;
                }
            }
        );
    }

    @Before
    public void setup() {
        kryo.register(InvocationHandler.class, new JdkProxySerializer());
    }

    @Test
    public void testReadAndWrite() {
        final Function<Object, String> proxy = createProxy();
        assertEquals("Proxy[apply=101]", proxy.apply(101));
        Function<Object, String> result = serializeAndDeserialize(proxy);
        assertEquals("Proxy[apply=-999]", result.apply(-999));
        assertEquals("MyProxy", result.toString());
        assertNotSame(proxy, result);
    }

    @Test
    public void testCopy() {
        final Function<Object, String> proxy = createProxy();
        final Function<Object, String> result = kryo.copy(proxy);
        assertEquals("Proxy[apply=Wibble!]", result.apply("Wibble!"));
        assertEquals("MyProxy", result.toString());
        assertNotSame(proxy, result);
    }

    @SuppressWarnings("unchecked")
    private <T> T serializeAndDeserialize(T source) {
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
