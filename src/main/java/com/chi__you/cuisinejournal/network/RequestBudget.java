package com.chi__you.cuisinejournal.network;

import java.util.*;

/** Shared bounded token bucket plus per-operation cooldowns; time is monotonic. */
public final class RequestBudget<K> {
    private record Use(double tokens, long updated, Map<String, Long> operations) {}
    private final Map<K, Use> uses = new HashMap<>();
    public synchronized boolean allow(K key, String operation, long now, long cooldown) {
        Use old = uses.get(key);
        double tokens = old == null ? 24 : Math.min(24, old.tokens + Math.max(0, now-old.updated)*.012);
        Map<String,Long> operations = old == null ? new HashMap<>() : old.operations;
        boolean allowed = tokens >= 1 && (!operations.containsKey(operation) || now-operations.get(operation)>=cooldown);
        if (allowed) { tokens--; operations.put(operation,now); }
        uses.put(key,new Use(tokens,now,operations)); return allowed;
    }
    public synchronized void remove(K key) { uses.remove(key); }
    public synchronized void clear() { uses.clear(); }
}
