package com.chi__you.cuisinejournal.platform;

import java.nio.file.Path;

/**
 * Loader-specific services discovered through {@link java.util.ServiceLoader}.
 *
 * <p>Provides platform metadata and directories without spreading loader
 * lookups across callers. The current distribution supplies a Forge provider.</p>
 */
public interface IPlatformHelper {
    String loaderName();

    String loaderVersion();

    String minecraftVersion();

    Path gameDirectory();

    Path configDirectory();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /**
     * Human-readable display name for a mod id, used by the journal's "source
     * mod" labels. Falls back to a prettified id when the loader has no
     * metadata for it.
     */
    String modDisplayName(String modId);
}
