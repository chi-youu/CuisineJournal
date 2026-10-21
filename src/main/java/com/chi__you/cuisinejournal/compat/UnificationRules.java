package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Predicate;

/** Explicit equivalence groups and opt-in resource overrides, not a generic recipe rewriter. */
public final class UnificationRules {
    public static final ResourceLocation HIDDEN = ResourceLocation.parse("cuisinejournal:unified_hidden");
    public record Result(Map<ResourceLocation, JsonElement> resources, Map<ResourceLocation, ResourceLocation> replacements,
                         List<String> problems) {}
    private UnificationRules() {}

    public static Result compile(JsonObject document, boolean enabled, Predicate<ResourceLocation> exists,
                                 Predicate<String> loaded) {
        var resources = new LinkedHashMap<ResourceLocation, JsonElement>();
        var replacements = new LinkedHashMap<ResourceLocation, ResourceLocation>();
        var problems = new ArrayList<String>();
        var claimed = new HashSet<ResourceLocation>();
        if (enabled) for (var entry : document.entrySet()) {
            try {
                ResourceLocation group = ResourceLocation.parse(entry.getKey());
                JsonObject rule = entry.getValue().getAsJsonObject();
                if (rule.has("enabled") && !rule.get("enabled").getAsBoolean()) continue;
                if (rule.has("required_mods") && strings(rule.getAsJsonArray("required_mods")).stream().anyMatch(mod -> !loaded.test(mod)))
                    continue;
                var items = ids(rule.getAsJsonArray("items"));
                var priority = priority(rule, loaded);
                if (items.size() < 2 || priority.isEmpty() || !items.containsAll(priority))
                    throw new IllegalArgumentException("Need at least two items and a nonempty priority drawn from items");
                if (rule.has("keep_recipes") && (!rule.get("keep_recipes").isJsonPrimitive()
                        || !rule.getAsJsonPrimitive("keep_recipes").isBoolean()))
                    throw new IllegalArgumentException("keep_recipes must be a boolean");
                if (rule.has("keep_recipe_items") && !items.containsAll(ids(rule.getAsJsonArray("keep_recipe_items"))))
                    throw new IllegalArgumentException("keep_recipe_items must be drawn from items");
                if (items.contains(ResourceLocation.parse("minecraft:air")))
                    throw new IllegalArgumentException("Air cannot be an equivalence member");
                var present = items.stream().filter(exists).toList();
                ResourceLocation winner = priority.stream().filter(exists).findFirst().orElse(null);
                if (winner == null || present.size() < 2) continue;
                if (present.stream().anyMatch(claimed::contains)) throw new IllegalArgumentException("Overlapping item groups");
                String tag = "cuisinejournal:unification/" + group.getNamespace() + "/" + group.getPath();
                var patches = new LinkedHashMap<ResourceLocation, JsonElement>();
                JsonObject tagFile = new JsonObject(); tagFile.addProperty("replace", false);
                var members = new JsonArray(); present.forEach(item -> members.add(item.toString())); tagFile.add("values", members);
                patches.put(resource(tag, "tags/items/"), tagFile);
                if (rule.has("resources")) for (var patch : rule.getAsJsonObject("resources").entrySet()) {
                    var id = ResourceLocation.parse(patch.getKey());
                    if (!(id.getPath().startsWith("recipes/") || id.getPath().startsWith("tags/items/")
                            || id.getPath().startsWith("loot_tables/")) || !id.getPath().endsWith(".json"))
                        throw new IllegalArgumentException("Unsupported resource path: " + id);
                    if (id.equals(resource(HIDDEN.toString(), "tags/items/"))
                            || id.equals(resource("kaleidoscope_cookery:united", "tags/items/")))
                        throw new IllegalArgumentException("Reserved hidden tag");
                    if (patches.putIfAbsent(id, expand(patch.getValue(), winner.toString(), tag)) != null)
                        throw new IllegalArgumentException("Duplicate generated resource: " + id);
                }
                if (patches.keySet().stream().anyMatch(resources::containsKey))
                    throw new IllegalArgumentException("Resource already owned by another group");
                resources.putAll(patches); claimed.addAll(present);
                for (var item : present) if (!item.equals(winner)) replacements.put(item, winner);
            } catch (RuntimeException failure) { problems.add(entry.getKey() + ": " + failure.getMessage()); }
        }
        var hidden = new JsonObject(); hidden.addProperty("replace", false);
        var values = new JsonArray(); replacements.keySet().forEach(item -> values.add(item.toString())); hidden.add("values", values);
        resources.put(resource(HIDDEN.toString(), "tags/items/"), hidden);
        return new Result(Map.copyOf(resources), Map.copyOf(replacements), List.copyOf(problems));
    }

    static List<ResourceLocation> priority(JsonObject rule, Predicate<String> loaded) {
        var items = ids(rule.getAsJsonArray("items"));
        var selected = rule.has("priority") ? ids(rule.getAsJsonArray("priority")) : items;
        if (rule.has("priority_when_loaded")) {
            boolean chosen = false;
            for (var entry : rule.getAsJsonObject("priority_when_loaded").entrySet()) {
                var order = ids(entry.getValue().getAsJsonArray());
                if (order.isEmpty() || !items.containsAll(order))
                    throw new IllegalArgumentException("Conditional priority must be drawn from items");
                if (!chosen && loaded.test(entry.getKey())) { selected = order; chosen = true; }
            }
        }
        return selected;
    }

    private static ResourceLocation resource(String tag, String prefix) {
        var id = ResourceLocation.parse(tag);
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + ".json");
    }
    private static List<String> strings(JsonArray array) {
        var result = new ArrayList<String>(); array.forEach(value -> result.add(value.getAsString())); return result;
    }
    private static List<ResourceLocation> ids(JsonArray array) {
        return strings(array).stream().map(ResourceLocation::parse).distinct().toList();
    }
    private static JsonElement expand(JsonElement value, String winner, String tag) {
        if (value.isJsonObject()) {
            var result = new JsonObject(); value.getAsJsonObject().entrySet().forEach(e -> result.add(e.getKey(), expand(e.getValue(), winner, tag))); return result;
        }
        if (value.isJsonArray()) { var result = new JsonArray(); value.getAsJsonArray().forEach(v -> result.add(expand(v, winner, tag))); return result; }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            if (value.getAsString().equals("$representative")) return new JsonPrimitive(winner);
            if (value.getAsString().equals("$tag")) return new JsonPrimitive(tag);
        }
        return value.deepCopy();
    }
}
