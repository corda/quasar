package org.testing.osgi.proxy;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Function;

public final class ProxyFactory {
    private ProxyFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <T, X extends Function<T, String>> X createProxy(Class<X> apiClass) {
        return (X) Proxy.newProxyInstance(
            apiClass.getClassLoader(),
            new Class[] { apiClass, AutoCloseable.class },
            (InvocationHandler & Serializable)(obj, method, args) -> {
                final String methodName = method.getName();
                switch (methodName) {
                    // java.lang.Object
                    case "equals":
                        return obj == args[0];
                    case "hashCode":
                        return System.identityHashCode(obj);
                    case "toString":
                        return "TestProxy[]";

                    // Function
                    case "apply":
                        return "TestProxy[apply=" + Arrays.toString(args) + ']';

                    // AutoCloseable
                    case "close":
                        System.out.println("Closing");
                        break;

                    default:
                        System.out.println("Unknown method: " + method);
                        break;
                }
                return null;
            }
        );
    }
}
