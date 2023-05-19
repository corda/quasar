package org.testing.osgi;

import com.esotericsoftware.kryo.ClassResolver;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultClassResolver;
import com.esotericsoftware.kryo.util.IdentityObjectIntMap;
import com.esotericsoftware.kryo.util.IntMap;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * This {@link ClassResolver} is only for testing purposes because {@link Bundle#getBundleId()}
 * values are only constant for the lifetime of the current OSGi framework. We should not
 * expect these IDs to remain the same across different test runs.
 */
final class OsgiClassResolver extends DefaultClassResolver {
    private final Map<Long, Bundle> bundles;

    OsgiClassResolver(Bundle[] bundles) {
        this.bundles = new HashMap<>();
        for (Bundle bundle: bundles) {
            this.bundles.put(bundle.getBundleId(), bundle);
        }
    }

    @Override
    public void writeName(Output output, Class type, Registration registration) {
        output.writeVarInt(NAME + 2, true);
        if (classToNameId == null) {
            classToNameId = new IdentityObjectIntMap<>();
        }
        int nameId = classToNameId.get(type, -1);
        if (nameId != -1) {
            output.writeVarInt(nameId, true);
            return;
        }
        // Only write the class name the first time encountered in object graph.
        nameId = nextNameId++;
        classToNameId.put(type, nameId);
        output.writeVarInt(nameId, true);
        if (registration.isTypeNameAscii()) {
            output.writeAscii(type.getName());
        } else {
            output.writeString(type.getName());
        }

        final Bundle bundle = FrameworkUtil.getBundle(type);
        output.writeLong(bundle != null ? bundle.getBundleId() : -1, true);
    }

    @Override
    public Registration readName(Input input) {
        final int nameId = input.readVarInt(true);
        if (nameIdToClass == null) {
            nameIdToClass = new IntMap<>();
        }
        final Class<?> type;
        if (nameIdToClass.containsKey(nameId)) {
            type = nameIdToClass.get(nameId);
        } else {
            // Only read the class name the first time encountered in object graph.
            final String className = input.readString();
            final long bundleId = input.readLong(true);
            try {
                if (bundleId == -1) {
                    type = Class.forName(className, false, kryo.getClassLoader());
                } else {
                    type = bundles.get(bundleId).loadClass(className);
                }
            } catch (ClassNotFoundException e) {
                throw new KryoException("Unable to find class: " + className, e);
            }
            nameIdToClass.put(nameId, type);
        }
        return kryo.getRegistration(type);
    }
}
