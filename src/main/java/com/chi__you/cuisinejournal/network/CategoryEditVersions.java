package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.GlobalCategoryStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Semantic category tokens; membership tokens are only dependencies of destructive edits. */
public final class CategoryEditVersions {
    private CategoryEditVersions() {}

    public static Map<String, String> values() {
        synchronized (GlobalCategoryStore.class) {
            Map<String, String> result = new TreeMap<>();
            JsonObject state = GlobalCategoryStore.exportOverlay(null);
            Map<String, JsonElement> roots = indexed(state.getAsJsonArray("roots"));
            Map<String, JsonElement> subs = indexed(state.getAsJsonArray("subcategories"));
            Map<String, Set<String>> children = new TreeMap<>();
            Map<String, Map<String, String>> members = new TreeMap<>();
            for (var entry : GlobalCategoryStore.metadata()) {
                boolean root = entry.parent() == null;
                String id = entry.id().toString();
                JsonObject value = new JsonObject();
                value.addProperty("parent", text(entry.parent()));
                value.addProperty("title", entry.titleKey());
                value.addProperty("literal", entry.literalTitle());
                value.addProperty("icon", text(entry.icon()));
                value.addProperty("source", entry.source());
                value.addProperty("protected", entry.protectedEntry());
                value.addProperty("hidden", entry.hidden());
                value.addProperty("originalTitle", entry.originalTitleKey());
                value.addProperty("originalLiteral", entry.originalLiteralTitle());
                value.addProperty("originalIcon", text(entry.originalIcon()));
                // Stored values change before the derived culinary registry is rebuilt.
                value.add("stored", (root ? roots : subs).get(id));
                value.add("override", state.getAsJsonObject(root ? "rootOverrides" : "subcategoryOverrides").get(id));
                if (!root) value.add("parentOverride", state.getAsJsonObject("parentOverrides").get(id));
                result.put(key(root ? "root" : "sub", id), value.toString());
                if (root) children.computeIfAbsent(id, unused -> new TreeSet<>());
                else children.computeIfAbsent(entry.parent().toString(), unused -> new TreeSet<>()).add(id);
                members.computeIfAbsent(id, unused -> new TreeMap<>());
            }
            Map<String, String> assignments = new TreeMap<>();
            state.getAsJsonObject("assignments").entrySet().forEach(e -> assignments.put(e.getKey(), e.getValue().getAsString()));
            Set<String> foods = new TreeSet<>(assignments.keySet());
            CulinaryRegistries.current().targets().forEach(id -> foods.add(id.toString()));
            for (String food : foods) {
                var dish = CulinaryRegistries.current().entry(ResourceLocation.tryParse(food));
                JsonObject value = new JsonObject();
                String assigned = assignments.get(food);
                value.addProperty("assignment", assigned);
                if (dish != null) {
                    value.addProperty("root", dish.category().id().toString());
                    value.addProperty("sub", dish.masteryGroup().toString());
                    members.computeIfAbsent(dish.masteryGroup().toString(), unused -> new TreeMap<>()).put(food, value.toString());
                }
                if (assigned != null) members.computeIfAbsent(assigned, unused -> new TreeMap<>()).put(food, value.toString());
                result.put(key("food", food), value.toString());
            }
            children.forEach((id, entries) -> {
                JsonArray value = new JsonArray();
                entries.forEach(value::add);
                result.put(key("children", id), value.toString());
            });
            members.forEach((id, entries) -> {
                JsonObject value = new JsonObject();
                entries.forEach(value::addProperty);
                result.put(key("members", id), value.toString());
            });
            tombstones(state, "deletedRoots", "root", result);
            tombstones(state, "deletedSubcategories", "sub", result);
            return Map.copyOf(result);
        }
    }

    private static void tombstones(JsonObject state, String field, String kind, Map<String, String> result) {
        JsonArray values = state.getAsJsonArray(field);
        if (values == null) return;
        values.forEach(value -> result.putIfAbsent(key(kind, value.getAsString()), "<tombstone>"));
    }

    public static Collection<String> keys(Packets.CategoryEditRequest request) {
        Set<String> result = new LinkedHashSet<>();
        var entries = GlobalCategoryStore.metadata();
        ResourceLocation id = request.targetId();
        Map<String, String> values = switch (request.action()) {
            case DELETE_ROOT, BLOCK_ROOT, DELETE_SUB, BLOCK_SUB -> values();
            default -> Map.of();
        };
        switch (request.action()) {
            case CREATE_ROOT, EDIT_ROOT, RESET_ROOT, RESTORE_ROOT -> add(result, "root", id);
            case CREATE_SUB, EDIT_SUB, MOVE_SUB, RESET_SUB, RESTORE_SUB -> {
                add(result, "sub", id);
                add(result, "root", request.parent());
                entries.stream().filter(e -> e.id().equals(id) && e.parent() != null)
                        .forEach(e -> add(result, "root", e.parent()));
            }
            case DELETE_ROOT, BLOCK_ROOT -> {
                add(result, "root", id);
                add(result, "children", id);
                affectedMembers(result, id, values);
                entries.stream().filter(e -> Objects.equals(e.parent(), id) && e.parent() != null).forEach(e -> {
                    add(result, "sub", e.id());
                    affectedMembers(result, e.id(), values);
                });
            }
            case DELETE_SUB, BLOCK_SUB -> {
                add(result, "sub", id);
                affectedMembers(result, id, values);
            }
            case ASSIGN_FOOD, ASSIGN_FOODS -> {
                // Assignment edits are independent at the food-entry level. A category
                // rename or a different food assignment must not reject this request.
                request.items().forEach(food -> add(result, "food", food));
            }
            case UNASSIGN_FOOD -> request.items().forEach(food -> add(result, "food", food));
        }
        return List.copyOf(result);
    }

    private static void affectedMembers(Set<String> keys, ResourceLocation id, Map<String, String> values) {
        if (id == null) return;
        add(keys, "members", id);
        // The aggregate catches removed/reassigned foods as well as newly added members.
        JsonObject members = com.google.gson.JsonParser.parseString(values.getOrDefault(key("members", id.toString()), "{}")).getAsJsonObject();
        members.keySet().forEach(food -> keys.add(key("food", food)));
    }

    private static Map<String, JsonElement> indexed(JsonArray entries) {
        Map<String, JsonElement> result = new TreeMap<>();
        entries.forEach(value -> result.put(value.getAsJsonObject().get("id").getAsString(), value));
        return result;
    }

    private static String text(ResourceLocation value) { return value == null ? null : value.toString(); }
    private static String key(String kind, String id) { return "category:" + kind + ":" + id; }
    private static void add(Set<String> keys, String kind, ResourceLocation id) {
        if (id != null) keys.add(key(kind, id.toString()));
    }
}
