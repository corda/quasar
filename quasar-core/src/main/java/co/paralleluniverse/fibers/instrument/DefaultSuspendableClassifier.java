/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.fibers.instrument;

import co.paralleluniverse.fibers.instrument.MethodDatabase.SuspendableType;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static co.paralleluniverse.fibers.instrument.Classes.KOTLIN_LAMBDA_SUFFIX;
import static co.paralleluniverse.fibers.instrument.Classes.LAMBDA_METHOD_PREFIX;
import static co.paralleluniverse.fibers.instrument.Classes.SUSPEND_EXECUTION_NAME;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 *
 * @author pron
 */
public class DefaultSuspendableClassifier implements SuspendableClassifier {
    private final List<SuspendableClassifier> classifiers;
    private final SuspendableClassifier simpleClassifier;

    public DefaultSuspendableClassifier(ClassLoader classLoader) {
        this.classifiers = StreamSupport.stream(ServiceLoader.load(SuspendableClassifier.class, classLoader).spliterator(), false).collect(toUnmodifiableList());
        this.simpleClassifier = new SimpleSuspendableClassifier(classLoader);
    }

    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public SuspendableType isSuspendable(MethodDatabase db, String sourceName, String sourceDebugInfo, boolean isInterface, String className, String superClassName, String[] interfaces, String methodName, String methodDesc, String methodSignature, String[] methodExceptions) {
        SuspendableType st;

        try {
            // classifier service
            for (SuspendableClassifier sc : classifiers) {
                st = sc.isSuspendable(db, sourceName, sourceDebugInfo, isInterface, className, superClassName, interfaces, methodName, methodDesc, methodSignature, methodExceptions);
                if (st != null)
                    return st;
            }

            // simple classifier (files in META-INF)
            st = simpleClassifier.isSuspendable(db, sourceName, sourceDebugInfo, isInterface, className, superClassName, interfaces, methodName, methodDesc, methodSignature, methodExceptions);
            if (st != null)
                return st;

            // throws SuspendExecution
            if (checkExceptions(methodExceptions))
                return SuspendableType.SUSPENDABLE;

            // lambda$ or $lambda-x
            if (methodName.startsWith(LAMBDA_METHOD_PREFIX) || methodName.contains(KOTLIN_LAMBDA_SUFFIX)) {
                return SuspendableType.SUSPENDABLE;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        return null;
    }

    private static boolean checkExceptions(String[] exceptions) {
        if (exceptions != null) {
            for (String ex : exceptions) {
                if (ex.equals(SUSPEND_EXECUTION_NAME))
                    return true;
            }
        }
        return false;
    }
}
