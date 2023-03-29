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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.reverseOrder;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class CollectionSerializerTest {
    private static final int BUFFER_SIZE = 8192;

    private static List<String> createRandomAccessListOf(String value) {
        final List<String> randomAccessList = new ArrayList<>();
        randomAccessList.add(value);
        return unmodifiableList(randomAccessList);
    }

    private static Set<String> treeSetOf(String... values) {
        Set<String> set = new TreeSet<>(reverseOrder());
        Collections.addAll(set, values);
        return set;
    }

    private static Queue<String> priorityQueueOf(String... values) {
        Queue<String> queue = new PriorityQueue<>(reverseOrder());
        Collections.addAll(queue, values);
        return queue;
    }

    private static final Collection<Collection<String>> IMMUTABLE_SINGLE_TYPE_DATA = List.of(
        createRandomAccessListOf("One"),
        createRandomAccessListOf("Two"),
        createRandomAccessListOf("Three")
    );

    private static final Collection<Collection<String>> UNMODIFIABLE_MULTIPLE_TYPE_DATA;
    static {
        List<Collection<String>> multiple = new LinkedList<>(IMMUTABLE_SINGLE_TYPE_DATA);
        multiple.add(singletonList("Four"));
        multiple.add(singleton("Five"));
        multiple.add(emptyList());
        multiple.add(emptySet());
        UNMODIFIABLE_MULTIPLE_TYPE_DATA = unmodifiableList(multiple);
    }

    @SuppressWarnings("unchecked")
    private static final Collection<Collection<String>> IMMUTABLE_MULTIPLE_TYPE_DATA = List.of(
        (Collection<String>[]) (UNMODIFIABLE_MULTIPLE_TYPE_DATA.toArray(new Collection<?>[0]))
    );

    private static final List<Collection<String>> MUTABLE_SINGLE_TYPE_DATA = new ArrayList<>(IMMUTABLE_SINGLE_TYPE_DATA);
    private static final List<Collection<String>> MUTABLE_MULTIPLE_TYPE_DATA = new ArrayList<>(IMMUTABLE_MULTIPLE_TYPE_DATA);
    private static final Collection<Collection<String>> UNMODIFIABLE_SINGLE_TYPE_DATA = unmodifiableList(MUTABLE_SINGLE_TYPE_DATA);

    private static final Collection<?>[] TEST_COLLECTIONS = {
        MUTABLE_MULTIPLE_TYPE_DATA,
        MUTABLE_SINGLE_TYPE_DATA,
        IMMUTABLE_SINGLE_TYPE_DATA,
        IMMUTABLE_MULTIPLE_TYPE_DATA,
        UNMODIFIABLE_SINGLE_TYPE_DATA,
        UNMODIFIABLE_MULTIPLE_TYPE_DATA,
        singletonList(singleton("Singleton List<Set<String>>")),
        singleton(singletonList("Singleton Set<List<String>>")),
        emptyList(),
        emptySet(),
        List.of(),
        Set.of(),
        asList(priorityQueueOf("Tom", "Dick", "Harry"), priorityQueueOf("Bill", "Ben")),
        singletonList(treeSetOf("Gamma", "Alpha", "Beta")),
        singleton(asList("Good", "Bad", "Ugly"))
    };

    private static Kryo createKryo() {
        final Kryo kryo = new ReplaceableObjectKryo(new DefaultClassResolver());
        kryo.register(ArrayList.class);
        kryo.register(LinkedList.class);
        kryo.register(PriorityQueue.class);
        kryo.register(TreeSet.class);
        kryo.register(asList("", "").getClass());
        kryo.register(emptySet().getClass());
        kryo.register(emptyList().getClass());
        kryo.register(singleton(null).getClass());
        kryo.register(singletonList(null).getClass());
        kryo.register(reverseOrder().getClass());
        ImmutableCollectionsSerializers.registerSerializers(kryo);
        UnmodifiableCollectionsSerializer.registerSerializers(kryo);
        return kryo;
    }

    private final Kryo kryo = createKryo();
    private final Collection<?> data;

    @Parameters(name = "{index}: collection = {0}")
    public static Collection<Object[]> data() {
        return stream(TEST_COLLECTIONS).map(collection -> new Object[] { collection }).collect(toUnmodifiableList());
    }

    public CollectionSerializerTest(Collection<?> data) {
        this.data = data;
    }

    @Test
    public void testSerializationOfCollectionTypes() {
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

        assertTrue(result instanceof Collection<?>);
        final Collection<?> resultList = (Collection<?>) result;
        assertEquals(data.size(), resultList.size());

        final Iterator<?> actual = resultList.iterator();
        final Iterator<?> expected = data.iterator();
        while (actual.hasNext() && expected.hasNext()) {
            final Object expectedElement = expected.next();
            final Object actualElement = actual.next();
            assertEquals(expectedElement.getClass(), actualElement.getClass());

            if (expectedElement instanceof Collection<?>) {
                assertArrayEquals(((Collection<?>)expectedElement).toArray(), ((Collection<?>)actualElement).toArray());
            } else if (expectedElement instanceof Object[]) {
                assertArrayEquals((Object[])expectedElement, (Object[])actualElement);
            } else {
                assertEquals(expectedElement, actualElement);
            }
        }

        assertFalse("Residual expected elements found?!", expected.hasNext());
        assertFalse("Residual actual elements found?!", actual.hasNext());
    }
}
