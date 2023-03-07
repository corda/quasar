package co.paralleluniverse.fibers;

import java.io.Serializable;

/**
 * A callback used by {@link Fiber#parkAndCustomSerialize(CustomFiberWriter)}.
 *
 * @author Christian Sailer (christian.sailer@r3.com)
 */
@FunctionalInterface
public interface CustomFiberWriter extends Serializable {
    void write(Fiber<?> fiber);
}
