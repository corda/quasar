package org.testing.multi.uninstrumented;

import java.util.function.Supplier;

public final class UninstrumentableType implements Supplier<String> {
    @Override
    public String get() {
        return "UNINSTRUMENTED";
    }
}
