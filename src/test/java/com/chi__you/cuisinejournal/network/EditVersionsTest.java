package com.chi__you.cuisinejournal.network;

import java.util.*;

public final class EditVersionsTest {
    private static int checks;
    public static void main(String[] args) {
        var tracker = new EditVersionTracker();
        tracker.update(Map.of("a", "first", "b", "other"));
        var opened = tracker.snapshot();
        tracker.update(Map.of("a", "second", "b", "other"));
        check(!matches(opened, tracker, "a"), "same entry stale save rejected");
        check(matches(opened, tracker, "b"), "different entry remains editable");
        tracker.update(Map.of("a", "first", "b", "other"));
        check(!matches(opened, tracker, "a"), "change back does not revive stale draft");
        var beforeDelete = tracker.snapshot();
        tracker.update(Map.of("b", "other"));
        check(!matches(beforeDelete, tracker, "a"), "deletion conflicts");
        var deleted = tracker.snapshot();
        check(matches(deleted, tracker, "a"), "fresh tombstone can be used");
        tracker.update(Map.of("a", "first", "b", "other"));
        check(!matches(deleted, tracker, "a"), "recreation conflicts");
        check(!matches(Map.of(), tracker, "a"), "missing token cannot overwrite existing entry");
        check(matches(Map.of(), tracker, "new"), "new entry can be created");
        check(!EditVersionTracker.matches(opened, tracker.snapshot(), List.of("a", "b")), "batch rejects if any member conflicts");
        var stable = tracker.snapshot(); tracker.update(Map.of("b", "other", "a", "first"));
        check(stable.equals(tracker.snapshot()), "same values preserve tokens regardless of map order");
        try { opened.put("a", 0L); throw new AssertionError("mutable snapshot"); } catch (UnsupportedOperationException expected) { checks++; }
        tracker.observe("binding", "source");
        var binding = tracker.snapshot(); tracker.observe("binding", "source");
        check(binding.equals(tracker.snapshot()), "repeated per-row sync preserves token");
        tracker.observe("binding", "changed");
        check(!matches(binding, tracker, "binding"), "binding update rejects old picker");
        System.out.println("EditVersionsTest: " + checks + " checks passed");
    }
    private static boolean matches(Map<String, Long> expected, EditVersionTracker tracker, String key) {
        return EditVersionTracker.matches(expected, tracker.snapshot(), List.of(key));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
