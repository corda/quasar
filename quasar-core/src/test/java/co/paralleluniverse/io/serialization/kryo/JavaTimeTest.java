package co.paralleluniverse.io.serialization.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import org.junit.Test;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * Check that we can serialize and deserialize these {@link java.time} classes,
 * which implement {@code writeReplace} and {@code readResolve}.
 */
public class JavaTimeTest {
    private static final int BUFFER_SIZE = 8192;

    private final Kryo kryo = KryoUtil.newKryo(new DefaultClassResolver());

    @Test
    public void testInstant() {
        final Instant source = Instant.now();
        final Instant result = serializeAndDeserialize(source);

        assertEquals(source.getClass(), result.getClass());
        assertNotSame(source, result);
        assertEquals(source, result);
    }

    @Test
    public void testDuration() {
        final Duration source = Duration.of(100, ChronoUnit.HOURS);
        final Duration result = serializeAndDeserialize(source);

        assertEquals(source.getClass(), result.getClass());
        assertNotSame(source, result);
        assertEquals(source, result);
    }

    @Test
    public void testPeriod() {
        final Period source = Period.of(100, 2, 20);
        final Period result = serializeAndDeserialize(source);

        assertEquals(source.getClass(), result.getClass());
        assertNotSame(source, result);
        assertEquals(source, result);
    }

    @SuppressWarnings("unchecked")
    private <T extends Serializable> T serializeAndDeserialize(T source) {
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
