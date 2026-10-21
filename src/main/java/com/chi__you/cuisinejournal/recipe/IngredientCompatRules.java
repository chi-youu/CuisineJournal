package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.chi__you.cuisinejournal.CuisineJournalLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.Reader;
import java.util.*;
import java.util.function.Predicate;

/** Immutable effective rules; real Minecraft tags and player records are never rewritten. */
public final class IngredientCompatRules {
    private static final Map<String, String> CHINESE_TAG_NAMES = chineseTagNames();
    private static volatile IngredientCompatRules current = empty();
    public static Runnable rollbackPoint() { var previous=current; return () -> current=previous; }
    private final Map<ResourceLocation, String> tagActions;
    private final Map<ResourceLocation, Boolean> excluded;
    private final Map<ResourceLocation, ResourceLocation> aliases, tagItems;
    private final Map<ResourceLocation, ResourceLocation> portionOrigins;
    private final Map<ResourceLocation, List<ResourceLocation>> itemTypes, tagTypes;
    private final Map<ResourceLocation, IngredientCatalogueConfig.Flavor> itemFlavors, tagFlavors;
    private final Map<ResourceLocation, Merge> merges;
    private final Map<ResourceLocation, String> names;
    private final Map<ResourceLocation, Boolean> processing;
    private final Map<String, List<String>> sources;

    private IngredientCompatRules(Builder b) {
        tagActions = Map.copyOf(b.tagActions); excluded = Map.copyOf(b.excluded);
        aliases = Map.copyOf(b.aliases); tagItems = Map.copyOf(b.tagItems);
        portionOrigins = Map.copyOf(b.portionOrigins);
        itemTypes = Map.copyOf(b.itemTypes); tagTypes = Map.copyOf(b.tagTypes);
        itemFlavors = Map.copyOf(b.itemFlavors); tagFlavors = Map.copyOf(b.tagFlavors);
        merges = Map.copyOf(b.merges); names = Map.copyOf(b.names);
        processing = Map.copyOf(b.processing);
        Map<String, List<String>> trace = new TreeMap<>();
        b.sources.forEach((k, v) -> trace.put(k, List.copyOf(v)));
        sources = Collections.unmodifiableMap(trace);
        validateAliases();
    }

    public record Merge(ResourceLocation id, String displayName, List<ResourceLocation> tags,
                        List<ResourceLocation> types, IngredientCatalogueConfig.Flavor flavor) {
        public Merge { tags = List.copyOf(tags); types = List.copyOf(types); }
    }
    public record RuleFile(ResourceLocation id, String pack, JsonObject json) {
        int priority() { return json.has("priority") ? json.get("priority").getAsInt() : 0; }
    }
    public static IngredientCompatRules current() { return current; }
    public static IngredientCompatRules empty() { return new IngredientCompatRules(new Builder()); }
    public static void clear() { current = empty(); }

    public static void load(ResourceManager resources) {
        List<RuleFile> files = new ArrayList<>();
        resources.listResources("cuisinejournal/compat", p -> p.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (Reader reader = resource.openAsReader()) {
                        files.add(new RuleFile(path, resource.sourcePackId(),
                                JsonParser.parseReader(reader).getAsJsonObject()));
                    } catch (Exception failure) {
                        CuisineJournalLog.LOGGER.warn("Skipping compatibility file {}: {}", path, failure.getMessage());
                    }
                });
        current = resolve(files, id -> ModList.get().isLoaded(id), IngredientCatalogueConfig.current().compatOverrides());
    }

    /** Same-resource files already obey vanilla pack precedence; distinct files use priority then id. */
    public static IngredientCompatRules resolve(List<RuleFile> files, Predicate<String> loaded, JsonObject local) {
        List<RuleFile> ordered = new ArrayList<>(files);
        ordered.removeIf(file -> {
            try { file.priority(); return false; }
            catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.warn("Skipping invalid priority in {}", file.id()); return true;
            }
        });
        ordered.sort(Comparator.comparingInt(RuleFile::priority).thenComparing(f -> f.id().toString()));
        Builder b = new Builder();
        for (RuleFile file : ordered) {
            try {
                if (strings(file.json(), "required_mods").stream().anyMatch(id -> !loaded.test(id))) continue;
                // Validate the complete file before it can mutate the accumulated rules.
                Builder validation = new Builder(); validation.apply(file.json(), "validation");
                b.apply(file.json(), file.pack() + " :: " + file.id());
            } catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.warn("Skipping compatibility file {}: {}", file.id(), failure.getMessage());
            }
        }
        if (local != null) {
            try {
                Builder validation = new Builder(); validation.apply(local, "validation");
                b.apply(local, "config/cuisinejournal-ingredients.json:compatOverrides");
            } catch (RuntimeException failure) {
                CuisineJournalLog.LOGGER.warn("Skipping local compatibility overrides: {}", failure.getMessage());
            }
        }
        return new IngredientCompatRules(b);
    }

    public boolean hiddenTag(ResourceLocation tag) { return "hidden".equals(tagActions.get(canonical(tag))); }
    public boolean dissolvedTag(ResourceLocation tag) { return "dissolved".equals(tagActions.get(canonical(tag))); }
    public boolean keptTag(ResourceLocation tag) { return "kept".equals(tagActions.get(canonical(tag))); }
    public Boolean itemExclusion(ResourceLocation item) { return excluded.get(item); }
    public boolean excludedItem(ResourceLocation item) { return Boolean.TRUE.equals(excluded.get(item)); }
    public ResourceLocation tagItem(ResourceLocation tag) { return tagItems.get(canonical(tag)); }
    public ResourceLocation alias(ResourceLocation item) { return alias(item, aliases); }
    /** Portion to preparation source; separate from ingredient-entry aliases. */
    public Map<ResourceLocation, ResourceLocation> portionOrigins() { return portionOrigins; }
    public List<ResourceLocation> itemSources(ResourceLocation target) {
        Set<ResourceLocation> result = new TreeSet<>(); result.add(target);
        aliases.keySet().stream().filter(item -> alias(item).equals(target)).forEach(result::add);
        return List.copyOf(result);
    }
    private static ResourceLocation alias(ResourceLocation item, Map<ResourceLocation, ResourceLocation> map) {
        Set<ResourceLocation> seen = new HashSet<>(); ResourceLocation next = item;
        while (map.containsKey(next)) {
            if (!seen.add(next)) return item;
            next = map.get(next);
        }
        return next;
    }
    private void validateAliases() {
        for (ResourceLocation start : aliases.keySet()) {
            if (aliases.containsKey(start) && alias(start).equals(start)) {
                CuisineJournalLog.LOGGER.warn("Ingredient alias cycle starting at {}; keeping original identities", start);
            }
        }
    }
    public List<ResourceLocation> itemTypes(ResourceLocation item) { return itemTypes.getOrDefault(item, List.of()); }
    public IngredientCatalogueConfig.Flavor itemFlavor(ResourceLocation item) { return itemFlavors.get(item); }
    public List<ResourceLocation> tagTypes(ResourceLocation tag) {
        ResourceLocation key = canonical(tag); Merge m = mergeFor(key);
        return tagTypes.containsKey(key) ? tagTypes.get(key) : m == null ? List.of() : m.types();
    }
    public IngredientCatalogueConfig.Flavor tagFlavor(ResourceLocation tag) {
        ResourceLocation key = canonical(tag); Merge m = mergeFor(key);
        return tagFlavors.containsKey(key) ? tagFlavors.get(key) : m == null ? null : m.flavor();
    }
    public Merge mergeFor(ResourceLocation tag) { return merges.get(canonical(tag)); }
    public String displayName(ResourceLocation key) {
        String name = names.get(canonical(key)); Merge m = mergeFor(key);
        return name != null ? name : m == null ? null : m.displayName();
    }
    public String chineseName(ResourceLocation key) {
        String name = displayName(key);
        if (name == null) {
            ResourceLocation id = canonical(key);
            name = CHINESE_TAG_NAMES.get("tag.item." + id.getNamespace() + "." + id.getPath().replace('/', '.'));
        }
        return name == null || name.isBlank() || name.codePoints().noneMatch(
                cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) ? null : name.strip();
    }
    private static Map<String, String> chineseTagNames() {
        Map<String, String> result = new HashMap<>();
        try (var stream = IngredientCompatRules.class.getResourceAsStream(
                "/assets/cuisinejournal/lang/zh_cn.json")) {
            if (stream != null) try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("tag.item."))
                        .forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
            }
        } catch (java.io.IOException | RuntimeException failure) {
            CuisineJournalLog.LOGGER.warn("Unable to read built-in Chinese tag names: {}", failure.getMessage());
        }
        return Map.copyOf(result);
    }
    public Boolean processingOverride(ResourceLocation type) { return processing.get(type); }
    public static ResourceLocation canonical(ResourceLocation id) {
        return "c".equals(id.getNamespace()) || "forge".equals(id.getNamespace())
                ? ResourceLocation.fromNamespaceAndPath("forge", id.getPath()) : id;
    }
    public List<String> sources(ResourceLocation id, boolean tag) {
        return sources.getOrDefault((tag ? "#" : "") + (tag ? canonical(id) : id), List.of());
    }
    public Set<String> affected() { return sources.keySet(); }

    private static ResourceLocation id(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid resource id: " + raw);
        return id;
    }
    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonElement v : json.getAsJsonArray(key)) result.add(v.getAsString());
        return result;
    }
    private static List<ResourceLocation> ids(JsonObject json, String key) {
        return strings(json, key).stream().map(IngredientCompatRules::id).distinct().toList();
    }
    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static final class Builder {
        final Map<ResourceLocation, String> tagActions = new LinkedHashMap<>();
        final Map<ResourceLocation, Boolean> excluded = new LinkedHashMap<>(), processing = new LinkedHashMap<>();
        final Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>(), tagItems = new LinkedHashMap<>();
        final Map<ResourceLocation, ResourceLocation> portionOrigins = new LinkedHashMap<>();
        final Map<ResourceLocation, List<ResourceLocation>> itemTypes = new LinkedHashMap<>(), tagTypes = new LinkedHashMap<>();
        final Map<ResourceLocation, IngredientCatalogueConfig.Flavor> itemFlavors = new LinkedHashMap<>(), tagFlavors = new LinkedHashMap<>();
        final Map<ResourceLocation, Merge> merges = new LinkedHashMap<>();
        final Map<ResourceLocation, String> names = new LinkedHashMap<>();
        final Map<String, List<String>> sources = new TreeMap<>();
        void trace(ResourceLocation key, boolean tag, String source, String action) {
            sources.computeIfAbsent((tag ? "#" : "") + key, k -> new ArrayList<>()).add(source + " -> " + action);
        }
        void action(ResourceLocation key, String action, String source) {
            key = canonical(key); tagActions.put(key, action);
            merges.remove(key); tagItems.remove(key); trace(key, true, source, action);
        }
        void apply(JsonObject json, String source) {
            if (json.has("enabled") && !json.get("enabled").getAsBoolean()) return;
            object(json, "portion_origins").entrySet().forEach(e -> {
                ResourceLocation portion = id(e.getKey());
                if (e.getValue().isJsonNull()) portionOrigins.remove(portion);
                else {
                    ResourceLocation parent = id(e.getValue().getAsString());
                    if (portion.equals(parent)) throw new IllegalArgumentException("Self-linked portion: " + portion);
                    portionOrigins.put(portion, parent);
                }
                trace(portion, false, source, "portion origin");
            });
            for (ResourceLocation key : ids(json, "hidden_tags")) action(key, "hidden", source);
            for (ResourceLocation key : ids(json, "dissolve_tags")) action(key, "dissolved", source);
            for (ResourceLocation key : ids(json, "keep_tags")) action(key, "kept", source);
            for (ResourceLocation key : ids(json, "remove_merge_tags")) {
                ResourceLocation group = canonical(key);
                List<ResourceLocation> removed = merges.entrySet().stream()
                        .filter(e -> canonical(e.getValue().id()).equals(group)).map(Map.Entry::getKey).toList();
                for (ResourceLocation member : removed) action(member, "kept", source);
            }
            for (ResourceLocation key : ids(json, "excluded_items")) { excluded.put(key, true); trace(key, false, source, "hidden"); }
            for (ResourceLocation key : ids(json, "included_items")) { excluded.put(key, false); trace(key, false, source, "kept"); }
            for (ResourceLocation key : ids(json, "remove_item_aliases")) { aliases.remove(key); trace(key, false, source, "alias removed"); }
            for (JsonElement value : json.has("item_aliases") ? json.getAsJsonArray("item_aliases") : List.<JsonElement>of()) {
                JsonObject o = value.getAsJsonObject(); ResourceLocation key = id(o.get("item").getAsString());
                ResourceLocation target = id(o.get("into").getAsString()); aliases.put(key, target); trace(key, false, source, "merged into " + target);
            }
            for (JsonElement value : json.has("tag_items") ? json.getAsJsonArray("tag_items") : List.<JsonElement>of()) {
                JsonObject o = value.getAsJsonObject(); ResourceLocation key = canonical(id(o.get("tag").getAsString()));
                ResourceLocation target = id(o.get("into").getAsString()); action(key, "item", source);
                tagItems.put(key, target); trace(key, true, source, "merged into item " + target);
            }
            object(json, "tag_names").entrySet().forEach(e -> {
                ResourceLocation key = canonical(id(e.getKey()));
                if (e.getValue().isJsonNull()) { names.remove(key); trace(key, true, source, "name reset"); return; }
                String name = e.getValue().getAsString();
                if (name.length() > 256) throw new IllegalArgumentException("Name too long: " + key);
                names.put(key, name); trace(key, true, source, "name");
            });
            types(json, "item_types", itemTypes, false, source); types(json, "tag_types", tagTypes, true, source);
            flavors(json, "item_flavor", itemFlavors, false, source); flavors(json, "tag_flavor", tagFlavors, true, source);
            for (JsonElement value : json.has("merge_tags") ? json.getAsJsonArray("merge_tags") : List.<JsonElement>of()) {
                JsonObject o = value.getAsJsonObject(); ResourceLocation target = id(o.get("id").getAsString());
                List<ResourceLocation> tags = ids(o, "tags");
                if (tags.isEmpty()) throw new IllegalArgumentException("Empty merge group: " + target);
                String name = o.has("name") ? o.get("name").getAsString() : null;
                if (name != null && name.length() > 256) throw new IllegalArgumentException("Name too long: " + target);
                Merge m = new Merge(target, name, tags, ids(o, "types"), o.has("flavor")
                        ? IngredientCatalogueConfig.Flavor.fromJson(o.getAsJsonObject("flavor")) : null);
                List<ResourceLocation> old = merges.entrySet().stream()
                        .filter(e -> e.getValue().id().equals(target)).map(Map.Entry::getKey).toList();
                for (ResourceLocation tag : old) action(tag, "kept", source);
                merges.put(canonical(target), m);
                for (ResourceLocation tag : tags) { action(tag, "merged", source); merges.put(canonical(tag), m); }
            }
            JsonObject p = object(json, "processing_recipe_types");
            for (JsonElement value : json.has("merge_tag_additions") ? json.getAsJsonArray("merge_tag_additions") : List.<JsonElement>of()) {
                JsonObject row = value.getAsJsonObject();
                ResourceLocation target = id(row.get("id").getAsString());
                List<ResourceLocation> added = ids(row, "tags");
                if (added.isEmpty()) throw new IllegalArgumentException("Empty tag addition: " + target);
                Merge base = merges.get(canonical(target));
                if (base == null || !canonical(base.id()).equals(canonical(target))) {
                    if (!source.equals("validation")) CuisineJournalLog.LOGGER.warn("Skipping tag addition to undefined group {} in {}", target, source);
                    continue;
                }
                Set<ResourceLocation> combined = new LinkedHashSet<>();
                for (ResourceLocation tag : base.tags()) if (merges.get(canonical(tag)) == base) combined.add(tag);
                combined.addAll(added);
                Merge updated = new Merge(base.id(), base.displayName(), List.copyOf(combined), base.types(), base.flavor());
                merges.replaceAll((key, old) -> old == base ? updated : old);
                for (ResourceLocation tag : added) {
                    action(tag, "merged", source);
                    merges.put(canonical(tag), updated);
                    trace(tag, true, source, "added to " + base.id());
                }
                merges.put(canonical(base.id()), updated);
            }
            for (ResourceLocation type : ids(p, "add")) { processing.put(type, true); trace(type, false, source, "processing add"); }
            for (ResourceLocation type : ids(p, "remove")) { processing.put(type, false); trace(type, false, source, "processing remove"); }
            for (ResourceLocation type : ids(p, "reset")) { processing.remove(type); trace(type, false, source, "processing reset"); }
        }
        void types(JsonObject json, String field, Map<ResourceLocation, List<ResourceLocation>> map, boolean tag, String source) {
            object(json, field).entrySet().forEach(e -> {
                ResourceLocation key = tag ? canonical(id(e.getKey())) : id(e.getKey());
                if (e.getValue().isJsonNull()) map.remove(key);
                else { JsonObject wrapper = new JsonObject(); wrapper.add("values", e.getValue()); map.put(key, ids(wrapper, "values")); }
                trace(key, tag, source, field);
            });
        }
        void flavors(JsonObject json, String field, Map<ResourceLocation, IngredientCatalogueConfig.Flavor> map, boolean tag, String source) {
            object(json, field).entrySet().forEach(e -> {
                ResourceLocation key = tag ? canonical(id(e.getKey())) : id(e.getKey());
                if (e.getValue().isJsonNull()) map.remove(key);
                else map.put(key, IngredientCatalogueConfig.Flavor.fromJson(e.getValue().getAsJsonObject()));
                trace(key, tag, source, field);
            });
        }
    }
}
