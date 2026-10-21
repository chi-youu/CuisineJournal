package com.chi__you.cuisinejournal.platform;

import java.util.ServiceLoader;

public final class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to load service for " + type.getName()));
    }
}
