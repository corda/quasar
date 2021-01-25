package co.paralleluniverse.fibers.suspend;

@SuppressWarnings("unused")
public interface StackOps {
    int nextMethodEntry();
    boolean isFirstInStackOrPushed();
    void pushMethod(int entry, int numSlots);
    void popMethod(int slots);
    void postRestore() throws SuspendExecution, InterruptedException;

    int getInt(int idx);
    float getFloat(int idx);
    long getLong(int idx);
    double getDouble(int idx);
    Object getObject(int idx);

    void push(int value, int idx);
    void push(float value, int idx);
    void push(long value, int idx);
    void push(double value, int idx);
    void push(Object value, int idx);

    static void push(int value, StackOps s, int idx) {
        s.push(value, idx);
    }

    static void push(float value, StackOps s, int idx) {
        s.push(value, idx);
    }

    static void push(long value, StackOps s, int idx) {
        s.push(value, idx);
    }

    static void push(double value, StackOps s, int idx) {
        s.push(value, idx);
    }

    static void push(Object value, StackOps s, int idx) {
        s.push(value, idx);
    }
}
