package org.testing.osgi.kotlin

import org.testing.osgi.annotation.DoNotInstrument
import org.testing.osgi.annotation.Suspendable
import java.lang.invoke.MethodHandle
import java.util.function.Function

@Suppress("unused")
class ForbidLambdaInstrumentation : Function<MethodHandle, Throwable> {
    @Suspendable
    override fun apply(message: MethodHandle): Throwable {
        val runnable = @DoNotInstrument {
            synchronized(this) {
                try {
                    // Invoking a MethodHandle is always suspendable.
                    throw Exception(message.invoke() as String)
                } catch (e: Error) {
                    throw e
                } catch (t: Throwable) {
                    t
                }
            }
        }
        return runnable.invoke()
    }
}
