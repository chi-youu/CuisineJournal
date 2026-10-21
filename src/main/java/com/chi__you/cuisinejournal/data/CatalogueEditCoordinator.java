package com.chi__you.cuisinejournal.data;

import java.nio.file.Path;

/** One ownership token for edits and asynchronous reloads. Stale completions cannot release a new operation. */
public final class CatalogueEditCoordinator {
    public enum Kind { EDIT, EXPORT, UNIFICATION }
    private static Lease active;
    private static Runnable pendingRebuild;
    private static long session;
    private CatalogueEditCoordinator() {}

    public static synchronized Lease begin(Kind kind) {
        if (active != null) return null;
        active = new Lease(kind, session);
        return active;
    }
    public static synchronized boolean busy() { return active != null; }
    public static synchronized boolean busy(Kind kind) { return active != null && active.kind == kind; }
    public static synchronized void stop() { session++; active = null; pendingRebuild = null; }

    /** Tag updates coalesce behind an edit; the latest rebuild reads the final resource state. */
    public static void requestRebuild(Runnable rebuild) {
        java.util.Objects.requireNonNull(rebuild);
        Lease lease;
        synchronized (CatalogueEditCoordinator.class) {
            if (active != null) { pendingRebuild = rebuild; return; }
            active = lease = new Lease(Kind.EDIT, session);
        }
        runRebuild(lease, rebuild);
    }

    private static void runRebuild(Lease lease, Runnable rebuild) {
        try { if (lease.current()) rebuild.run(); }
        finally { lease.close(); }
    }

    public static final class Lease implements AutoCloseable {
        private Kind kind;
        private final long generation;
        private Lease(Kind kind, long generation) { this.kind = kind; this.generation = generation; }
        public boolean current() { synchronized (CatalogueEditCoordinator.class) { return active == this && generation == session; } }
        public void phase(Kind next) {
            synchronized (CatalogueEditCoordinator.class) {
                if (!current()) throw new IllegalStateException("Expired catalogue edit");
                kind = next;
            }
        }
        @Override public void close() {
            Lease continuation = null;
            Runnable rebuild = null;
            synchronized (CatalogueEditCoordinator.class) {
                if (!current()) return;
                active = null;
                if (pendingRebuild != null) {
                    rebuild = pendingRebuild;
                    pendingRebuild = null;
                    active = continuation = new Lease(Kind.EDIT, session);
                }
            }
            if (continuation != null) runRebuild(continuation, rebuild);
        }
    }

    /** Shared checkpoint and rollback policy; callers supply changes and the authoritative rebuild/sync action. */
    public static void apply(Path directory, Runnable mutation, Runnable rebuild, Runnable synchronize) {
        var snapshot = checkpoint(directory);
        try { mutation.run(); rebuild.run(); }
        catch (RuntimeException | LinkageError failure) {
            rollback(snapshot, failure);
            throw failure;
        }
        synchronize.run();
    }

    public static LocalEditSnapshot checkpoint(Path directory) { return new LocalEditSnapshot(directory); }
    public static void rollback(LocalEditSnapshot snapshot, Throwable failure) {
        try { snapshot.restore(); }
        catch (RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
    }
}
