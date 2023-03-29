package co.paralleluniverse.io.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.ImmutableCollectionsSerializers;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ObjectArraySerializerTest {
    private static final int BUFFER_SIZE = 8192;

    private static List<String> createRandomAccessListOf(String value) {
        final List<String> randomAccessList = new ArrayList<>();
        randomAccessList.add(value);
        return unmodifiableList(randomAccessList);
    }

    private static final Object[][] TEST_ARRAYS = {
         new String[] { "One", "Two", "Three" },
         new Long[] { 0L, 10L, 1000L },
         new Object[] { "Zero", 1, 100L },
         new Object[] {
             createRandomAccessListOf("Tom"),
             createRandomAccessListOf("Dick"),
             createRandomAccessListOf("Harry")
         }
    };

    private static Kryo createKryo() {
        final Kryo kryo = new ReplaceableObjectKryo(new DefaultClassResolver());
        kryo.register(Long[].class);
        kryo.register(String[].class);
        kryo.register(Object[].class);
        kryo.register(ArrayList.class);
        ImmutableCollectionsSerializers.registerSerializers(kryo);
        UnmodifiableCollectionsSerializer.registerSerializers(kryo);
        return kryo;
    }

    private final Kryo kryo = createKryo();
    private final Object[] data;

    @Parameters(name = "{index}: array = {0}")
    public static Collection<Object[]> data() {
        return stream(TEST_ARRAYS).map(array -> new Object[] { array }).collect(toUnmodifiableList());
    }

    public ObjectArraySerializerTest(Object[] data) {
        this.data = data;
    }

    @Test
    public void testSerializationOfArrays() {
        final byte[] buffer;

        try (Output output = new Output(BUFFER_SIZE)) {
            kryo.writeClassAndObject(output, data);
            buffer = output.getBuffer();
        }

        final Object result;
        try (Input input = new Input(buffer)) {
            result = kryo.readClassAndObject(input);
        }

        assertEquals(data.getClass(), result.getClass());

        assertTrue(result instanceof Object[]);
        final Object[] resultArray = (Object[]) result;
        assertEquals(data.length, resultArray.length);
        assertEquals(data.getClass().getComponentType(), resultArray.getClass().getComponentType());

        for (int i = 0; i < data.length; ++i) {
            final Object expectedElement = data[i];
            final Object actualElement = resultArray[i];
            assertEquals(expectedElement.getClass(), actualElement.getClass());

            if (expectedElement instanceof Collection<?>) {
                assertArrayEquals(((Collection<?>)expectedElement).toArray(), ((Collection<?>)actualElement).toArray());
            } else if (expectedElement instanceof Object[]) {
                assertArrayEquals((Object[])expectedElement, (Object[])actualElement);
            } else {
                assertEquals(expectedElement, actualElement);
            }
        }
    }
}
