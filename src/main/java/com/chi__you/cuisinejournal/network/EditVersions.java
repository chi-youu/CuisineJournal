package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.recipe.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Optimistic edit tokens. Deleted entries retain a token to reject stale recreation. */
public final class EditVersions {
    private static final EditVersionTracker tracker = new EditVersionTracker();
    private EditVersions() {}

    public static String preset(ResourceLocation id) { return "flavor:preset:" + id; }
    public static String binding(boolean tag, ResourceLocation id) { return "flavor:" + (tag ? "tag:" : "item:") + id; }
    public static String collection(boolean category) { return category ? "collection:category" : "collection:flavor"; }

    public static synchronized Map<String, Long> snapshot() {
        Map<String, String> next = new HashMap<>(CategoryEditVersions.values());
        FlavorPresets.all().forEach((id, p) -> next.put(preset(id), FlavorPresets.origin(id) + ":" + canonical(p.toJson())));
        var config = IngredientCatalogueConfig.current();
        var registry = CulinaryRegistries.current();
        Set<ResourceLocation> items = new HashSet<>(registry.catalogueItems());
        items.addAll(config.localFlavors(false).keySet());
        for (var id : items) putBinding(next, false, id, IngredientCatalogue.flavor(id));
        Set<ResourceLocation> tags = new HashSet<>(registry.tags().keySet());
        tags.addAll(config.localFlavors(true).keySet());
        for (var id : tags) {
            var tag = registry.tags().get(id);
            List<ResourceLocation> ids = new ArrayList<>(); ids.add(id);
            if (tag != null) ids.addAll(tag.ids());
            putBinding(next, true, id, IngredientCatalogue.tagFlavor(ids));
        }
        for (var kind : SchemeStore.Kind.values()) {
            JsonObject content = new JsonObject();
            content.add("local", SchemeStore.currentContent(kind));
            content.addProperty("current", SchemeStore.currentId(kind));
            JsonObject schemes = new JsonObject();
            for (var scheme : SchemeStore.list(kind)) {
                JsonObject value = new JsonObject();
                value.addProperty("name", scheme.name()); value.addProperty("description", scheme.description());
                value.addProperty("source", scheme.source()); value.add("content", scheme.content());
                schemes.add(scheme.id(), value);
            }
            content.add("schemes", schemes);
            JsonObject effective = new JsonObject();
            String prefix = kind == SchemeStore.Kind.CATEGORY ? "category:" : "flavor:";
            next.forEach((key, value) -> { if (key.startsWith(prefix)) effective.addProperty(key, value); });
            content.add("effective", effective);
            next.put(collection(kind == SchemeStore.Kind.CATEGORY), canonical(content));
        }
        tracker.update(next);
        return tracker.snapshot();
    }

    private static void putBinding(Map<String, String> next, boolean tag, ResourceLocation id, IngredientCatalogueConfig.Flavor flavor) {
        var local = IngredientCatalogueConfig.current().localFlavor(tag, id);
        next.put(binding(tag, id), (local != null) + ":" + (flavor == null ? "null" : canonical(flavor.toJson())));
    }

    public static synchronized long bindingVersion(boolean tag, ResourceLocation id, IngredientCatalogueConfig.Flavor flavor) {
        Map<String, String> next = new HashMap<>(); putBinding(next, tag, id, flavor);
        String key = binding(tag, id), value = next.get(key);
        return tracker.observe(key, value);
    }

    public static synchronized boolean matches(Map<String, Long> expected, Collection<String> keys) {
        Map<String, Long> current = snapshot();
        return EditVersionTracker.matches(expected, current, keys);
    }

    public static boolean busy() { return com.chi__you.cuisinejournal.data.CatalogueEditCoordinator.busy(); }

    private static String canonical(JsonElement element) { return sorted(element).toString(); }
    private static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            new TreeMap<>(element.getAsJsonObject().asMap()).forEach((k, v) -> result.add(k, sorted(v)));
            return result;
        }
        if (element.isJsonArray()) { JsonArray result = new JsonArray(); element.getAsJsonArray().forEach(v -> result.add(sorted(v))); return result; }
        return element;
    }
}
