package co.paralleluniverse.io.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.DefaultArraySerializers.ObjectArraySerializer;
import java.lang.reflect.Array;

import static com.esotericsoftware.kryo.Kryo.NULL;

/**
 * A modified {@link ObjectArraySerializer} that records the actual type for each
 * of the collection's elements, since invoking any {@code writeReplace} method
 * may change this type.
 */
public final class ReplaceableObjectArraySerializer extends ObjectArraySerializer {
    public ReplaceableObjectArraySerializer(Kryo kryo, Class<?> type) {
        super(kryo, type);
    }

    @Override
    public void write(Kryo kryo, Output output, Object[] object) {
        if (object == null) {
            output.writeByte(NULL);
            return;
        }

        output.writeVarInt(object.length + 1, true);
        for (Object o : object) {
            kryo.writeClassAndObject(output, o);
        }
    }

    @Override
    public Object[] read(Kryo kryo, Input input, Class type) {
        int length = input.readVarInt(true);
        if (length == NULL) {
            return null;
        }
        --length;

        final Object[] object = (Object[]) Array.newInstance(type.getComponentType(), length);
        kryo.reference(object);
        for (int i = 0; i < length; ++i) {
            object[i] = kryo.readClassAndObject(input);
        }
        return object;
    }
}
