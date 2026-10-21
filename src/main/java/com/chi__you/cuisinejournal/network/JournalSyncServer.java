package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.culinary.CulinaryProfile;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.Supplier;

/** Registry-owned graph, with weak per-connection delta baselines. */
final class JournalSyncServer {
    private static CulinaryRegistry registry;
    private static JournalSyncPackets.Directory directory;
    private static long generation;
    private static final Map<ResourceLocation, JournalSyncPackets.TagDetails> TAG_DETAILS = new HashMap<>();
    private static final Map<ServerPlayer, Baseline> PLAYERS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> RESYNCS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> TAG_REQUESTS = new WeakHashMap<>();
    private JournalSyncServer() {}

    static JournalSyncPackets.Directory directory(CulinaryRegistry current,
            Supplier<Packets.SyncJournalExtras> build) {
        if (registry != current || directory == null) {
            var value = build.get();
            long nextGeneration = ++generation;
            TAG_DETAILS.clear();
            for (var ingredient : value.ingredients()) if (ingredient.tag())
                TAG_DETAILS.put(ingredient.item(), new JournalSyncPackets.TagDetails(nextGeneration, ingredient.item(),
                        ingredient.tagSources(), ingredient.tagUses()));
            directory = new JournalSyncPackets.Directory(nextGeneration,
                    value.collections().stream().map(v -> new JournalSyncPackets.CollectionProgress(v.id(), 0, 0).apply(v)).toList(),
                    value.ingredients().stream().map(v -> new JournalSyncPackets.IngredientProgress(v.item(), v.tag(),
                            false, 0, Long.MIN_VALUE, Long.MIN_VALUE, 0, List.of()).apply(v)).map(v ->
                            new JournalSyncPackets.TagDetails(nextGeneration, v.item(), List.of(), List.of()).apply(v)).toList(),
                    value.totalIngredients());
            registry = current; PLAYERS.clear();
        }
        return directory;
    }

    static void clear() {
        registry = null; directory = null; PLAYERS.clear(); RESYNCS.clear(); TAG_DETAILS.clear(); TAG_REQUESTS.clear();
    }
    static void forget(ServerPlayer player) { PLAYERS.remove(player); }
    static boolean needsDirectory(ServerPlayer player, CulinaryRegistry current) {
        return registry != current || directory == null || !PLAYERS.containsKey(player);
    }
    static void disconnect(ServerPlayer player) { PLAYERS.remove(player); RESYNCS.remove(player); TAG_REQUESTS.remove(player); }
    static JournalSyncPackets.TagDetails tagDetails(long requestedGeneration, ResourceLocation tag) {
        return directory == null || directory.generation() != requestedGeneration ? null : TAG_DETAILS.get(tag);
    }
    static void sendTag(ServerPlayer player, JournalSyncPackets.TagRequest request) {
        if (registry != CulinaryRegistries.current() || !PLAYERS.containsKey(player)) return;
        var details = tagDetails(request.generation(), request.tag());
        if (details == null) return;
        long now = System.nanoTime() / 1_000_000;
        Long previous = TAG_REQUESTS.get(player);
        if (previous != null && now - previous < 50) return;
        TAG_REQUESTS.put(player, now); Network.toPlayer(player, details);
    }
    static void resync(ServerPlayer player) {
        long now = System.nanoTime() / 1_000_000;
        Long previous = RESYNCS.get(player);
        if (previous != null && now - previous < 2000) return;
        RESYNCS.put(player, now); forget(player); ServerPacketHandler.sendExtras(player);
    }

    static void send(ServerPlayer player, CulinaryProfile profile, MasteryCurve curve,
                     JournalSyncPackets.Directory graph, List<Packets.ChallengeView> challenges,
                     List<Packets.MethodView> methods, List<Packets.ActivityView> activity, boolean force) {
        Baseline before = PLAYERS.get(player);
        boolean full = before == null || before.generation != graph.generation();
        // Extras can be sent by skill/login hooks before sendEverything runs.
        if (full) {
            ServerPacketHandler.sendCatalogue(player);
            Network.toPlayer(player, graph);
        }
        Map<JournalSyncPackets.IngredientKey, JournalSyncPackets.IngredientProgress> ingredients = new LinkedHashMap<>();
        Map<ResourceLocation, IngredientProgress> records = new HashMap<>();
        for (ResourceLocation item : registry.catalogueItems())
            records.put(item, IngredientProgress.collect(registry.ingredientSources(item), profile::peekIngredient));
        for (var template : graph.ingredients()) {
            var record = template.tag() ? TagIngredientProgress.collect(registry, template.item(), profile::peekMeal)
                    : records.get(template.item());
            boolean known = record != null && record.known();
            var value = new JournalSyncPackets.IngredientProgress(template.item(), template.tag(), known,
                    record == null ? 0 : record.timesUsed(),
                    record == null ? Long.MIN_VALUE : record.firstUsedDay(),
                    record == null ? Long.MIN_VALUE : record.lastUsedDay(),
                    (int)template.meals().stream().filter(profile::isDiscovered).count(),
                    record == null ? List.of() : record.methods());
            ingredients.put(value.key(), value);
        }
        Map<ResourceLocation, JournalSyncPackets.CollectionProgress> collections = new LinkedHashMap<>();
        Map<ResourceLocation, int[]> counts = new HashMap<>();
        for (var entry : registry.entries()) {
            ResourceLocation id = ServerPacketHandler.usesModFallback(entry)
                    ? ServerPacketHandler.modCategoryId(entry.sourceModId()) : entry.category().id();
            int[] count = counts.computeIfAbsent(id, key -> new int[2]);
            if (profile.isDiscovered(entry.target())) count[0]++;
            if (profile.rankOf(entry.target(), curve).isMastered()) count[1]++;
        }
        for (var template : graph.collections()) {
            int[] count = counts.getOrDefault(template.id(), new int[2]);
            collections.put(template.id(), new JournalSyncPackets.CollectionProgress(template.id(), count[0], count[1]));
        }
        List<JournalSyncPackets.CollectionProgress> changedCollections = collections.values().stream()
                .filter(v -> full || !v.equals(before.collections.get(v.id()))).toList();
        List<JournalSyncPackets.IngredientProgress> changedIngredients = ingredients.values().stream()
                .filter(v -> full || !v.equals(before.ingredients.get(v.key()))).toList();
        if (!force && !full && changedCollections.isEmpty() && changedIngredients.isEmpty()
                && challenges.equals(before.challenges) && methods.equals(before.methods) && activity.equals(before.activity)) return;
        long sequence = before == null ? 1 : before.sequence + 1;
        Network.toPlayer(player, new JournalSyncPackets.Progress(graph.generation(), sequence, full,
                changedCollections, changedIngredients, challenges, methods, activity));
        PLAYERS.put(player, new Baseline(graph.generation(), sequence, collections, ingredients, challenges, methods, activity));
    }

    private record Baseline(long generation, long sequence,
            Map<ResourceLocation, JournalSyncPackets.CollectionProgress> collections,
            Map<JournalSyncPackets.IngredientKey, JournalSyncPackets.IngredientProgress> ingredients,
            List<Packets.ChallengeView> challenges, List<Packets.MethodView> methods, List<Packets.ActivityView> activity) {}
}
