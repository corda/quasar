package co.paralleluniverse.io.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.security.AccessController.doPrivileged;

public final class JdkProxySerializer extends Serializer<Object> {
    @Override
    public void write(Kryo kryo, Output output, Object obj) {
        final InvocationHandler handler = Proxy.getInvocationHandler(obj);
        final Class<?>[] interfaces = obj.getClass().getInterfaces();
        kryo.writeObject(output, interfaces);
        kryo.writeClassAndObject(output, handler);
    }

    @SuppressWarnings("removal")
    @Override
    public Object read(Kryo kryo, Input input, Class<?> type) {
        final Class<?>[] interfaces = kryo.readObject(input, Class[].class);
        final InvocationHandler handler = (InvocationHandler) kryo.readClassAndObject(input);
        return doPrivileged((PrivilegedAction<Object>)() -> createProxy(interfaces, handler));
    }

    @SuppressWarnings("removal")
    @Override
    public Object copy(Kryo kryo, Object original) {
        return doPrivileged((PrivilegedAction<Object>)() ->
            createProxy(original.getClass().getInterfaces(), Proxy.getInvocationHandler(original))
        );
    }

    private Object createProxy(Class<?>[] interfaces, InvocationHandler handler) {
        final Set<ClassLoader> classLoaders = new LinkedHashSet<>();
        for (Class<?> clazz : interfaces) {
            final ClassLoader classLoader = clazz.getClassLoader();
            if (classLoader != null) {
                classLoaders.add(classLoader);
            }
        }
        classLoaders.add(handler.getClass().getClassLoader());

        final List<Exception> exceptions = new ArrayList<>();
        for (ClassLoader classLoader : classLoaders) {
            try {
                // Use the first classloader that successfully creates the proxy.
                return Proxy.newProxyInstance(classLoader, interfaces, handler);
            } catch (RuntimeException e) {
                exceptions.add(e);
            }
        }
        final KryoException ex = new KryoException("Failed to create proxy using classloader=" + classLoaders);
        for (Exception e: exceptions) {
            ex.addSuppressed(e);
        }
        throw ex;
    }
}
