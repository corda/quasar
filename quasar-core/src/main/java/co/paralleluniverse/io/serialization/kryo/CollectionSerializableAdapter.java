package co.paralleluniverse.io.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.CollectionSerializer;
import com.esotericsoftware.kryo.serializers.DefaultSerializers.ArraysAsListSerializer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.esotericsoftware.kryo.Kryo.NULL;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;
import static java.lang.invoke.MethodType.methodType;
import static java.security.AccessController.doPrivileged;

/**
 * Modify a {@link CollectionSerializer} such that we always record the actual type
 * for each of the collection's elements, since invoking any {@code writeReplace} method
 * may change this type.
 * @param <T> The {@link Collection} type whose {@link CollectionSerializer} is being adapted.
 */
final class CollectionSerializerAdapter<T extends Collection<? super Object>> extends CollectionSerializer<T> {
    private static final MethodHandle writeHeaderMethod;
    private static final MethodHandle createMethod;

    static {
        try {
            final MethodHandles.Lookup lookup = doPrivileged((PrivilegedExceptionAction<MethodHandles.Lookup>) () ->
                privateLookupIn(CollectionSerializer.class, lookup())
            );
            writeHeaderMethod = lookup.findVirtual(CollectionSerializer.class, "writeHeader", methodType(void.class, Kryo.class, Output.class, Collection.class));
            createMethod = lookup.findVirtual(CollectionSerializer.class, "create", methodType(Collection.class, Kryo.class, Input.class, Class.class, int.class));
        } catch (PrivilegedActionException ex) {
            final Exception e = ex.getException();
            throw new InternalError(e.getMessage(), e);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InternalError(e.getMessage(), e);
        }
    }

    private final CollectionSerializer<T> underlying;
    private final ReplaceableObjectKryo roKryo;

    CollectionSerializerAdapter(ReplaceableObjectKryo roKryo, CollectionSerializer<T> underlying) {
        this.underlying = underlying;
        this.roKryo = roKryo;
    }

    @Override
    public void setElementsCanBeNull(boolean elementsCanBeNull) {
        underlying.setElementsCanBeNull(elementsCanBeNull);
    }

    @Override
    public void setElementClass(Class elementClass) {
        underlying.setElementClass(elementClass);
    }

    @Override
    public Class<?> getElementClass() {
        return underlying.getElementClass();
    }

    @Override
    public void setElementClass(Class elementClass, Serializer serializer) {
        underlying.setElementClass(elementClass, serializer);
    }

    @Override
    public void setElementSerializer(Serializer elementSerializer) {
        underlying.setElementSerializer(elementSerializer);
    }

    @Override
    public Serializer<?> getElementSerializer() {
        return underlying.getElementSerializer();
    }

    @Override
    public T copy(Kryo kryo, T original) {
        return underlying.copy(kryo, original);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected T create(Kryo kryo, Input input, Class<? extends T> type, int size) {
        try {
            return (T) createMethod.invoke(underlying, kryo, input, type, size);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t.getMessage(), t);
        }
    }

    @Override
    protected void writeHeader(Kryo kryo, Output output, T collection) {
        try {
            writeHeaderMethod.invoke(underlying, kryo, output, collection);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t.getMessage(), t);
        }
    }

    @Override
    public void write(Kryo kryo, Output output, T collection) {
        if (collection == null) {
            output.writeByte(NULL);
            return;
        }

        final int length = collection.size();
        if (length == 0) {
            output.writeByte(1);
            writeHeader(kryo, output, collection);
            return;
        }

        try {
            output.writeVarInt(length + 1, true);
            writeHeader(kryo, output, collection);

            for (Object element : collection) {
                kryo.writeClassAndObject(output, element);
            }
        } finally {
            kryo.getGenerics().popGenericType();
        }
    }

    @Override
    public T read(Kryo kryo, Input input, Class<? extends T> type) {
        try {
            int length = input.readVarIntFlag(true);
            if (length == 0) {
                return null;
            }

            --length;
            final T collection = create(kryo, input, type, length);
            kryo.reference(collection);

            for (int i = 0; i < length; ++i) {
                collection.add(kryo.readClassAndObject(input));
            }
            return adapt(collection);
        } finally {
            kryo.getGenerics().popGenericType();
        }
    }

    @SuppressWarnings("unchecked")
    private T adapt(T collection) {
        final Class<?> underlyingClass = underlying.getClass();
        if (underlyingClass == ArraysAsListSerializer.class) {
            return (T) Arrays.asList(collection.toArray());
        } else if (underlyingClass == roKryo.getImmutableListSerializerClass()) {
            return (T) List.of(collection.toArray());
        } else if (underlyingClass == roKryo.getImmutableSetSerializerClass()) {
            return (T) Set.of(collection.toArray());
        } else {
            return collection;
        }
    }
}
