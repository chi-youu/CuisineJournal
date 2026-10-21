package com.chi__you.cuisinejournal.client;

import java.util.Map;

/** Latest server tokens; editors copy these when opening a draft, never on submission. */
public final class EditClientState {
    private static volatile Map<String, Long> versions = Map.of();
    private EditClientState() {}
    public static Map<String, Long> snapshot() { return versions; }
    public static void accept(Map<String, Long> value) { versions = Map.copyOf(value); }
    public static void clear() { versions = Map.of(); }
}
