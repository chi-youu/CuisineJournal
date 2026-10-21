package com.chi__you.cuisinejournal.compat;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

/** Optional access to Compat's persisted mode. Runtime pack replacement is handled separately. */
public final class KaleidoscopeUnificationCompat {
    private KaleidoscopeUnificationCompat() {}

    public static String datapackMode(boolean enabled) {
        return enabled ? "UNITE" : "COMPAT";
    }

    public static boolean installed() {
        return ModList.get().isLoaded("kaleidoscope_compat");
    }

    public static boolean enabled(boolean fallback) {
        if (!installed()) return fallback;
        try {
            return "UNITE".equals(value().get());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return fallback;
        }
    }

    public static void apply(boolean enabled) {
        applyMode(datapackMode(enabled));
    }

    public static String currentMode() {
        try { return value().get(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static void applyMode(String mode) {
        if (!installed()) return;
        try {
            var setting = value();
            String previous = setting.get();
            try {
                setting.set(mode);
                setting.save();
            } catch (RuntimeException failure) {
                setting.set(previous);
                throw failure;
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported Kaleidoscope Compat configuration", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static ForgeConfigSpec.ConfigValue<String> value() throws ReflectiveOperationException {
        return (ForgeConfigSpec.ConfigValue<String>) Class.forName(
                "com.bmt.kaleidoscope_compat.config.ForgeConfig").getField("DATAPACK_MODE").get(null);
    }
}
