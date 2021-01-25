package co.paralleluniverse.fibers.suspend;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public final class Stacks {
    private static volatile Supplier<? extends StackOps> GET_STACK;

    public static StackOps getStack() {
        final Supplier<? extends StackOps> getter = GET_STACK;
        return getter != null ? getter.get() : null;
    }
}
