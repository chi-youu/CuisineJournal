package com.chi__you.cuisinejournal.tracking;

import java.util.Deque;
import java.util.function.Predicate;

/** Unwinds abandoned inner calls without ever consuming an unrelated outer frame. */
public final class FrameStacks {
    private FrameStacks() {}
    public static <T> T finish(Deque<T> frames, Predicate<T> matches) {
        if (frames.stream().noneMatch(matches)) return null;
        while (!frames.isEmpty()) {
            T frame=frames.pop();
            if (matches.test(frame)) return frame;
        }
        return null;
    }
}
