package com.chi__you.cuisinejournal.network;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Reload failure must never consume the local edits that produced an export. */
public final class ExportCompletion {
    private ExportCompletion() {}
    public static CompletableFuture<Integer> afterReload(CompletableFuture<?> reload, Executor executor,
                                                         int written, BooleanSupplier clear) {
        return reload.thenApplyAsync(ignored -> {
            if (!clear.getAsBoolean()) throw new CompletionException(new IllegalStateException("Export cleanup failed"));
            return written;
        }, executor);
    }
}
