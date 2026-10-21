package com.chi__you.cuisinejournal.compat;

import com.chi__you.cuisinejournal.mixin.unification.UnificationPackRepositoryAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.*;
import net.minecraftforge.event.AddPackFindersEvent;
import java.util.*;
import java.util.concurrent.*;

/** Replaces only Compat's fixed finders; the repository itself stays attached to the server. */
public final class UnificationReload {
    private UnificationReload() {}
    private record Filtered(RepositorySource original) implements RepositorySource {
        public void loadPacks(java.util.function.Consumer<Pack> consumer) {
            original.loadPacks(pack -> { if (!UnificationPacks.managed(pack.getId())) consumer.accept(pack); });
        }
    }
    private record Current(List<RepositorySource> sources) implements RepositorySource {
        public void loadPacks(java.util.function.Consumer<Pack> consumer) {
            sources.forEach(source -> source.loadPacks(consumer));
        }
    }

    public static Transaction capture(MinecraftServer server) { return new Transaction(server); }

    public static final class Transaction {
        private final java.util.function.Function<Collection<String>, CompletableFuture<Void>> reload;
        private final PackRepository repository;
        private final Set<RepositorySource> sources;
        private final List<RepositorySource> before;
        private final List<String> selected;

        private Transaction(MinecraftServer server) {
            this(server.getPackRepository(), ((UnificationPackRepositoryAccessor) server.getPackRepository())
                    .cuisinejournal$sources(), server::reloadResources);
        }

        Transaction(PackRepository repository, Set<RepositorySource> sources,
                    java.util.function.Function<Collection<String>, CompletableFuture<Void>> reload) {
            this.repository = repository; this.sources = sources; this.reload = reload;
            before = List.copyOf(sources);
            selected = repository.getSelectedPacks().stream().map(Pack::getId).toList();
        }

        public CompletableFuture<Void> apply() {
            return apply(com.chi__you.cuisinejournal.config.ConfigManager.server().compatibility.unifiedMode);
        }

        public CompletableFuture<Void> apply(boolean enabled) {
            try {
                var nativeSources = new ArrayList<RepositorySource>();
                // Invoke only the audited host listener, not every mod's registration event again.
                if (KaleidoscopeUnificationCompat.installed()) Class.forName("com.bmt.kaleidoscope_compat.datapack.DatapackLoader")
                        .getMethod("onDatapackLoad", AddPackFindersEvent.class).invoke(null,
                                new AddPackFindersEvent(PackType.SERVER_DATA, nativeSources::add));
                nativeSources.add(consumer -> {
                    var own = UnificationRulePack.create(enabled);
                    if (own == null) throw new IllegalStateException("Cannot create Cuisine Journal unification pack");
                    consumer.accept(own);
                });
                return applySources(nativeSources);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        CompletableFuture<Void> applySources(List<RepositorySource> nativeSources) {
            try {
                var packs = new ArrayList<String>();
                var current = new Current(List.copyOf(nativeSources));
                current.loadPacks(pack -> packs.add(pack.getId()));
                if (packs.isEmpty()) throw new IllegalStateException("Compat registered no packs");
                sources.clear();
                for (RepositorySource source : before) {
                    if (source instanceof Current) continue;
                    sources.add(source instanceof Filtered ? source : new Filtered(source));
                }
                sources.add(current);
                repository.reload();
                for (String id : packs) if (!repository.isAvailable(id))
                    throw new IllegalStateException("Missing Compat pack " + id);
                var next = UnificationPacks.replace(selected, packs);
                repository.setSelected(next);
                return reload.apply(next);
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        public CompletableFuture<Void> rollback() {
            try {
                sources.clear(); sources.addAll(before);
                repository.reload(); repository.setSelected(selected);
                return reload.apply(selected);
            } catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        }
    }
}
