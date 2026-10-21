package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.recipe.FlavorPresets;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client copy of the flavor presets, edit permission, pending results and previews. */
public final class FlavorClientState {
    public record Entry(FlavorPresets.Preset preset, byte origin, int usedBy) { }

    private static volatile Map<ResourceLocation, Entry> presets = Map.of();
    private static volatile boolean canEdit;
    private static volatile int localPresets, localBindings;
    private static volatile boolean localCategories;
    private static final Map<Integer,Packets.FlavorEditResult> RESULTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Packets.FlavorPreview preview;
    private static volatile long revision;
    private static int nextRequest = 1;

    private FlavorClientState() {
    }

    public static void accept(Packets.SyncFlavors packet) {
        EditClientState.accept(packet.versions());
        Map<ResourceLocation, Entry> next = new LinkedHashMap<>();
        for (Packets.FlavorPresetView view : packet.presets()) {
            try {
                var preset = FlavorPresets.read(view.id(), JsonParser.parseString(view.json()).getAsJsonObject());
                next.put(view.id(), new Entry(preset, view.origin(), view.usedBy()));
            } catch (RuntimeException ignored) {
                // A preset the client cannot parse is simply not offered.
            }
        }
        presets = Map.copyOf(next);
        canEdit = packet.canEdit();
        localPresets = packet.localPresets(); localBindings = packet.localBindings(); localCategories = packet.localCategories();
        // Bindings or presets changed: dish flavors are asked for again when next shown.
        DISH_FLAVORS.clear(); REQUESTED.clear();
        revision++;
    }

    public static void accept(Packets.FlavorEditResult result) {
        if (RESULTS.size()>=128) RESULTS.clear();
        RESULTS.put(result.requestId(),result); revision++;
    }
    public static void accept(Packets.FlavorPreview value) {
        preview = value;
        DISH_FLAVORS.put(value.dish(), value.flavors());
        REQUESTED.remove(value.dish());
        revision++;
    }

    /** Flavors a dish picks up from its ingredients, as last reported by the server. */
    private static final Map<ResourceLocation, List<ResourceLocation>> DISH_FLAVORS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ResourceLocation,Long> REQUESTED = new java.util.concurrent.ConcurrentHashMap<>();

    /** The dish's flavors, or null while unknown (the first call asks the server once). */
    public static List<ResourceLocation> dishFlavors(ResourceLocation dish) {
        if (!com.chi__you.cuisinejournal.food.CuisineGameplayRules.flavorsEnabled()) return List.of();
        if (dish == null) return List.of();
        List<ResourceLocation> known = DISH_FLAVORS.get(dish);
        long now = System.nanoTime()/1_000_000;
        Long sent = REQUESTED.get(dish);
        if (known == null && (sent == null || now-sent>=2000)) {
            REQUESTED.put(dish,now);
            if(REQUESTED.size()>4096)REQUESTED.entrySet().removeIf(e->now-e.getValue()>10_000);
            com.chi__you.cuisinejournal.network.Network.toServer(new Packets.FlavorPreviewRequest(dish, 0));
        }
        return known;
    }

    public static Entry get(ResourceLocation id) { return id == null ? null : presets.get(id); }
    /** Presets sorted for lists: enabled first, then by display name. */
    public static List<Entry> sorted() {
        List<Entry> result = new ArrayList<>(presets.values());
        result.sort(java.util.Comparator.comparing((Entry e) -> !e.preset().enabled())
                .thenComparing(e -> e.preset().name(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    public static boolean canEdit() { return canEdit; }
    public static int localPresets() { return localPresets; }
    public static int localBindings() { return localBindings; }
    public static boolean localCategories() { return localCategories; }
    public static long revision() { return revision; }
    public static Packets.FlavorPreview preview() { return preview; }

    /** Takes the result of the request with this id, if it has arrived. */
    public static Packets.FlavorEditResult take(int requestId) {
        return RESULTS.remove(requestId);
    }

    public static synchronized int nextRequestId() { return nextRequest++; }

    public static void clear() {
        EditClientState.clear();
        DishSearchState.clear();
        presets = Map.of(); canEdit = false; RESULTS.clear(); preview = null;
        localPresets = 0; localBindings = 0; localCategories = false;
        DISH_FLAVORS.clear(); REQUESTED.clear(); revision++;
    }
}
