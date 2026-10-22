package com.chi__you.cuisinejournal.compat;

import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import java.lang.reflect.Method;
import java.util.*;

/** One read-only Mixin 0.8 registry access, isolated from the conflict policy. */
final class SelectedTargetMixins {
    private static final Method SELECTED = selectedMethod();
    private SelectedTargetMixins() {}

    static List<IMixinInfo> of(String target) {
        // Mixins.getMixinsForClass exposes APPLIED mixins, which is too late for acceptTargets.
        ClassInfo type = ClassInfo.fromCache(target);
        if (type == null || SELECTED == null) throw new IllegalStateException("Selected Mixin registry unavailable");
        try {
            Object result = SELECTED.invoke(type);
            if (!(result instanceof Set<?> values)) throw new IllegalStateException("Selected Mixin registry changed");
            var selected = new ArrayList<IMixinInfo>();
            for (Object value : values) {
                if (!(value instanceof IMixinInfo info)) throw new IllegalStateException("Selected Mixin entry changed");
                selected.add(info);
            }
            selected.sort(Comparator.comparing(IMixinInfo::getClassName));
            return selected;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read selected Mixins", failure); }
    }
    private static Method selectedMethod() {
        try {
            Method method = ClassInfo.class.getDeclaredMethod("getMixins");
            return method.trySetAccessible() ? method : null;
        } catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
}
