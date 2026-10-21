package com.chi__you.cuisinejournal.tracking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Registered operation-local state. Tick/world cleanup cannot omit a newly added producer. */
public final class ProductionFrames<T> {
    private static final List<ProductionFrames<?>> REGISTERED = new CopyOnWriteArrayList<>();
    private final ThreadLocal<T> value = new ThreadLocal<>();
    private final Supplier<T> initial;

    private ProductionFrames(Supplier<T> initial) {
        this.initial = initial;
        REGISTERED.add(this);
    }

    public static <T> ProductionFrames<T> value() { return new ProductionFrames<>(() -> null); }
    public static <T> ProductionFrames<Deque<T>> stack() { return new ProductionFrames<>(ArrayDeque::new); }

    public T get() {
        T current = value.get();
        if (current == null) {
            current = initial.get();
            if (current != null) value.set(current);
        }
        return current;
    }

    public void set(T frame) { if (frame == null) remove(); else value.set(frame); }
    public void remove() { value.remove(); }

    /** Discard abandoned inner operations only if the requested owner actually exists in this stack. */
    public static <T> T take(Deque<T> frames, Predicate<T> matches) {
        return FrameStacks.finish(frames, matches);
    }

    public static void expireAll() { REGISTERED.forEach(ProductionFrames::remove); }

    public <R> R run(T frame, Supplier<R> action) {
        T previous = value.get();
        set(frame);
        try { return action.get(); }
        finally { if (previous == null) remove(); else set(previous); }
    }
}
