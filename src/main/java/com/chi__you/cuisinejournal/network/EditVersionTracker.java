package com.chi__you.cuisinejournal.network;

import java.util.*;

/** Monotonic tokens include tombstones, so changing back never revives an old draft. */
public final class EditVersionTracker {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Long> versions = new HashMap<>();
    private long sequence;

    public synchronized void update(Map<String, String> next) {
        Set<String> keys = new HashSet<>(values.keySet()); keys.addAll(next.keySet());
        for (String key : keys) if (!Objects.equals(values.get(key), next.get(key))) versions.put(key, ++sequence);
        values.clear(); values.putAll(next);
    }

    public synchronized long observe(String key, String value) {
        if (!Objects.equals(values.get(key), value)) { values.put(key, value); versions.put(key, ++sequence); }
        return versions.getOrDefault(key, 0L);
    }

    public synchronized Map<String, Long> snapshot() { return Map.copyOf(versions); }

    public static boolean matches(Map<String, Long> expected, Map<String, Long> current, Collection<String> keys) {
        return keys.stream().allMatch(key -> Objects.equals(expected.getOrDefault(key, 0L), current.getOrDefault(key, 0L)));
    }
}
