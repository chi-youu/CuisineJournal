package com.chi__you.cuisinejournal.client.journal;

/** Identity-based return route; a failed or abandoned open never captures a later screen. */
public final class ScreenReturnSession<T> {
    private T parent;
    private boolean entered;
    private long expiresAt;
    public void request(T parent, long now) { this.parent = parent; entered = false; expiresAt = now + 10_000; }
    public boolean enter(T current, boolean destination, long now) {
        if (parent == null) return false;
        if (!entered && (now > expiresAt || current != parent)) { clear(); return false; }
        if (destination) { entered = true; return true; }
        clear(); return false;
    }
    public T closing(boolean destination, boolean toGame) {
        T result = entered && destination && toGame ? parent : null;
        clear(); return result;
    }
    public void expire(T current, long now) {
        if (!entered && parent != null && (current != parent || now > expiresAt)) clear();
    }
    public void clear() { parent = null; entered = false; expiresAt = 0; }
}
