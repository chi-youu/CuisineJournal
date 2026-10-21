package com.chi__you.cuisinejournal.tracking;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Workstation metadata is composable; declaring a capability does not install a production hook. */
public final class WorkstationDefinitions {
    public enum Capability {
        ITEM_INPUT, FLUID_INPUT, TIMED_PROCESSING, FUEL_PROCESSING, DIRECT_INTERACTION,
        ITEM_OUTPUT, FLUID_OUTPUT, CONTAINER_FILL, MENU_TAKE, RIGHT_CLICK_TAKE,
        DIRECT_DROP, AUTOMATION_EXTRACT;

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** These names select an existing implementation, not a presumed universal machine API. */
    public enum Adapter {
        AUTO, CRAFTING, FURNACE, COOKING_POT, SKILLET, STOVE, CUTTING_BOARD, CAMPFIRE, ITEM_RECIPE_CONTAINER;
        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public record Definition(ResourceLocation block, boolean enabled, Adapter adapter,
                             ResourceLocation method, Set<Capability> capabilities,
                             Set<ResourceLocation> recipeTypes, List<String> sources) {
        public Definition {
            capabilities = Set.copyOf(capabilities);
            recipeTypes = Set.copyOf(recipeTypes);
            sources = List.copyOf(sources);
        }
        public boolean acceptsRecipe(ResourceLocation type) {
            return recipeTypes.isEmpty() || recipeTypes.contains(type);
        }
    }

    public record RuleFile(ResourceLocation id, String pack, JsonObject json) {}
    private static volatile Map<ResourceLocation, Definition> current = Map.of();
    public static Runnable rollbackPoint() { var previous=current; return () -> current=previous; }
    private WorkstationDefinitions() {}
    public static Definition get(ResourceLocation block) { return current.get(block); }
    public static Map<ResourceLocation, Definition> all() { return current; }
    public static void clear() { current = Map.of(); }

    public static Set<Capability> supported(Adapter adapter) {
        return switch (adapter) {
            case CRAFTING -> Set.of(Capability.ITEM_INPUT, Capability.ITEM_OUTPUT, Capability.DIRECT_INTERACTION, Capability.MENU_TAKE);
            case FURNACE -> Set.of(Capability.ITEM_INPUT, Capability.ITEM_OUTPUT, Capability.TIMED_PROCESSING,
                    Capability.FUEL_PROCESSING, Capability.MENU_TAKE);
            case ITEM_RECIPE_CONTAINER -> Set.of(Capability.ITEM_INPUT, Capability.ITEM_OUTPUT,
                    Capability.TIMED_PROCESSING, Capability.MENU_TAKE, Capability.CONTAINER_FILL);
            case COOKING_POT -> Set.of(Capability.ITEM_INPUT, Capability.ITEM_OUTPUT, Capability.TIMED_PROCESSING,
                    Capability.MENU_TAKE, Capability.CONTAINER_FILL, Capability.RIGHT_CLICK_TAKE);
            case SKILLET, STOVE, CAMPFIRE -> Set.of(Capability.ITEM_INPUT, Capability.ITEM_OUTPUT,
                    Capability.TIMED_PROCESSING, Capability.DIRECT_DROP, Capability.DIRECT_INTERACTION);
            case CUTTING_BOARD -> Set.of(Capability.ITEM_INPUT, Capability.ITEM_OUTPUT,
                    Capability.DIRECT_INTERACTION, Capability.DIRECT_DROP);
            case AUTO -> Set.of();
        };
    }
    public static Set<Capability> pending(Definition definition, Adapter detected) {
        Set<Capability> pending = new LinkedHashSet<>(definition.capabilities());
        // A declared adapter is not proof the target block's implementation matches it.
        Adapter effective = definition.adapter()==Adapter.AUTO || definition.adapter()==detected ? detected : Adapter.AUTO;
        pending.removeAll(supported(effective));
        return Set.copyOf(pending);
    }

    public static void load(ResourceManager resources) {
        List<RuleFile> files = new ArrayList<>();
        resources.listResources("cuisinejournal/compat", p -> p.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (Reader reader = resource.openAsReader()) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("workstations")) files.add(new RuleFile(path, resource.sourcePackId(), json));
                    } catch (Exception failure) {
                        CuisineJournalLog.LOGGER.warn("Skipping workstation file {}: {}", path, failure.getMessage());
                    }
                });
        current = resolve(files, mod -> ModList.get().isLoaded(mod));
    }

    public static Map<ResourceLocation, Definition> resolve(List<RuleFile> files, Predicate<String> loaded) {
        List<RuleFile> ordered = new ArrayList<>();
        for (RuleFile file : files) {
            try { priority(file); ordered.add(file); }
            catch (RuntimeException failure) { warn(file, failure); }
        }
        ordered.sort(Comparator.comparingInt(WorkstationDefinitions::priority)
                .thenComparing(file -> file.id().toString()));
        Map<ResourceLocation, Definition> result = new LinkedHashMap<>();
        for (RuleFile file : ordered) {
            try {
                if (!enabled(file.json()) || strings(file.json(), "required_mods").stream().anyMatch(mod -> !loaded.test(mod))) continue;
                Map<ResourceLocation, Definition> next = new LinkedHashMap<>(result);
                for (JsonElement entry : file.json().getAsJsonArray("workstations")) {
                    JsonObject json = entry.getAsJsonObject();
                    ResourceLocation block = id(json.get("block").getAsString());
                    Definition old = next.get(block);
                    Adapter adapter = json.has("adapter") ? Adapter.valueOf(upper(json.get("adapter").getAsString()))
                            : old == null ? Adapter.AUTO : old.adapter();
                    ResourceLocation method = json.has("method") ? id(json.get("method").getAsString())
                            : old == null ? null : old.method();
                    Set<Capability> capabilities = new LinkedHashSet<>(old == null ? Set.of() : old.capabilities());
                    if (json.has("capabilities")) {
                        capabilities.clear();
                        for (String raw : strings(json, "capabilities")) capabilities.add(Capability.valueOf(upper(raw)));
                    }
                    for (String raw : strings(json, "add_capabilities")) capabilities.add(Capability.valueOf(upper(raw)));
                    for (String raw : strings(json, "remove_capabilities")) capabilities.remove(Capability.valueOf(upper(raw)));
                    Set<ResourceLocation> recipes = new LinkedHashSet<>(old == null ? Set.of() : old.recipeTypes());
                    if (json.has("recipe_types")) {
                        recipes.clear(); strings(json, "recipe_types").forEach(raw -> recipes.add(id(raw)));
                    }
                    List<String> sources = new ArrayList<>(old == null ? List.of() : old.sources());
                    sources.add(file.pack() + " :: " + file.id());
                    next.put(block, new Definition(block, json.has("enabled") ? enabled(json) : old == null || old.enabled(),
                            adapter, method, capabilities, recipes, sources));
                }
                result = next;
            } catch (RuntimeException failure) { warn(file, failure); }
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static int priority(RuleFile file) { return file.json().has("priority") ? file.json().get("priority").getAsInt() : 0; }
    private static boolean enabled(JsonObject json) { return !json.has("enabled") || json.get("enabled").getAsBoolean(); }
    private static String upper(String value) { return value.toUpperCase(java.util.Locale.ROOT); }
    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("Invalid resource id: " + value);
        return id;
    }
    private static List<String> strings(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray(field)) result.add(entry.getAsString());
        return result;
    }
    private static void warn(RuleFile file, RuntimeException failure) {
        CuisineJournalLog.LOGGER.warn("Skipping workstation compatibility file {}: {}", file.id(), failure.getMessage());
    }
}
