package com.chi__you.cuisinejournal.compat;

/** Optional host matching must not load classes for mods that are absent. */
public final class HostClasses {
    private HostClasses() {}

    public static boolean matches(Object value, String... names) {
        if (value == null) return false;
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            for (String name : names) if (type.getName().equals(name)) return true;
        return false;
    }
}

