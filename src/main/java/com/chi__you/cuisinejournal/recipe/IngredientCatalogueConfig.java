package com.chi__you.cuisinejournal.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Server-side manual corrections and confirmed flavor-source presentation data.
 *
 * <p>{@code excluded} accepts three forms: an item id ({@code minecraft:bowl}), an item tag
 * ({@code #minecraft:leaves} — removes the tag's members and the tag entry itself) and a
 * wildcard id ({@code *:*_bowl}). {@code processingRecipeTypes} lists recipe types that only
 * process food (cutting, milling); their outputs are ingredients, not dishes.</p>
 */
public final class IngredientCatalogueConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .registerTypeAdapter(Flavor.class, (com.google.gson.JsonSerializer<Flavor>)
                    (flavor, type, context) -> flavor.toJson())
            .registerTypeAdapter(Flavor.class, (com.google.gson.JsonDeserializer<Flavor>)
                    (json, type, context) -> Flavor.fromJson(json.getAsJsonObject())).create();
    private static final String FILE_NAME = "cuisinejournal-ingredients.json";
    private static final List<String> DEFAULT_PROCESSING = List.of(
            "farmersdelight:cutting", "create:milling", "create:crushing", "create:cutting",
            "kaleidoscope_cookery:chopping_board","kaleidoscope_cookery:millstone","farm_and_charm:mincer");
    private static IngredientCatalogueConfig current = new IngredientCatalogueConfig();
    private static Path currentFile;
    private static boolean writable = true;
    public static Runnable rollbackPoint() {
        var previous=current;var file=currentFile;boolean canWrite=writable;
        return () -> {current=previous;currentFile=file;writable=canWrite;};
    }
    static { current.normalise(); }

    private Set<String> excluded = new LinkedHashSet<>();
    private List<String> processingRecipeTypes;
    private com.google.gson.JsonObject compatOverrides = new com.google.gson.JsonObject();
    private Map<String, List<String>> types = new LinkedHashMap<>();
    private Map<String, Flavor> flavor = new LinkedHashMap<>();
    /** Flavor of a tag entry, keyed by tag id ({@code c:eggs} or {@code forge:eggs}). */
    private Map<String, Flavor> tagFlavor = new LinkedHashMap<>();

    private transient Set<ResourceLocation> excludedItems = Set.of();
    private transient List<ResourceLocation> excludedTags = List.of();
    private transient List<Pattern> excludedPatterns = List.of();
    private transient Set<ResourceLocation> processing = Set.of();

    public static synchronized void load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        currentFile = file;
        writable = true;
        if (!Files.exists(file)) {
            current = new IngredientCatalogueConfig();
            current.normalise();
            current.save(file);
            return;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            IngredientCatalogueConfig loaded = GSON.fromJson(raw, IngredientCatalogueConfig.class);
            if (loaded == null) throw new IllegalArgumentException("Missing ingredient configuration object");
            loaded.normalise();
            current = loaded;
            current.normalise();
            // Surface newly added options in an existing file without touching its values.
            if (!raw.contains("\"processingRecipeTypes\"") || !raw.contains("\"tagFlavor\"")) current.save(file);
        } catch (IOException | RuntimeException failure) {
            com.chi__you.cuisinejournal.data.UnreadableFiles.preserve(file);
            writable = false;
        }
    }

    public static synchronized IngredientCatalogueConfig current() {
        return current;
    }

    public boolean excluded(ResourceLocation id) {
        if (containsId(compatOverrides(), "excluded_items", id)) return true;
        if (containsId(compatOverrides(), "included_items", id)) return false;
        if (excludedItems.contains(id)) return true;
        String text = id.toString();
        for (Pattern pattern : excludedPatterns) if (pattern.matcher(text).matches()) return true;
        if (excludedTags.isEmpty()) return IngredientCompatRules.current().excludedItem(id);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) return false;
        ItemStack stack = new ItemStack(item);
        for (ResourceLocation tag : excludedTags) {
            if (stack.is(TagKey.create(Registries.ITEM, tag))) return true;
        }
        return IngredientCompatRules.current().excludedItem(id);
    }

    /** Whether a tag entry is excluded (either the {@code c:} or the {@code forge:} spelling). */
    public boolean excludedTag(ResourceLocation tag) {
        ResourceLocation key = CulinaryRegistry.tagKey(tag);
        if (containsTag(compatOverrides(), "keep_tags", key)) return false;
        if (containsTag(compatOverrides(), "hidden_tags", key)) return true;
        for (ResourceLocation excluded : excludedTags) {
            if (excluded.equals(tag) || CulinaryRegistry.tagKey(excluded).equals(key)) return true;
        }
        return IngredientCompatRules.current().hiddenTag(key);
    }

    public boolean processing(ResourceLocation recipeType) {
        if (recipeType == null) return false;
        com.google.gson.JsonObject p = compatOverrides().has("processing_recipe_types")
                ? compatOverrides().getAsJsonObject("processing_recipe_types") : new com.google.gson.JsonObject();
        if (containsId(p, "remove", recipeType)) return false;
        if (containsId(p, "add", recipeType)) return true;
        if (processingRecipeTypes != null) return processing.contains(recipeType);
        Boolean override = IngredientCompatRules.current().processingOverride(recipeType);
        return override != null ? override : DEFAULT_PROCESSING.contains(recipeType.toString());
    }

    public com.google.gson.JsonObject compatOverrides() {
        return compatOverrides == null ? new com.google.gson.JsonObject() : compatOverrides;
    }

    public List<String> localSources(ResourceLocation id, boolean tag) {
        List<String> result = new ArrayList<>();
        String key = id.toString();
        if (tag) {
            for (ResourceLocation blocked : excludedTags)
                if (IngredientCompatRules.canonical(blocked).equals(IngredientCompatRules.canonical(id)))
                    result.add("config/cuisinejournal-ingredients.json:excluded #" + blocked);
            if (tagFlavor.containsKey(key)) result.add("config/cuisinejournal-ingredients.json:tagFlavor " + key);
        } else {
            if (excludedItems.contains(id)) result.add("config/cuisinejournal-ingredients.json:excluded " + key);
            for (Pattern pattern : excludedPatterns) if (pattern.matcher(key).matches())
                result.add("config/cuisinejournal-ingredients.json:excluded wildcard " + pattern);
            if (flavor.containsKey(key)) result.add("config/cuisinejournal-ingredients.json:flavor " + key);
        }
        if (types.containsKey(key)) result.add("config/cuisinejournal-ingredients.json:types " + key);
        return List.copyOf(result);
    }

    private static boolean containsId(com.google.gson.JsonObject json, String field, ResourceLocation id) {
        if (!json.has(field)) return false;
        for (var value : json.getAsJsonArray(field)) if (id.toString().equals(value.getAsString())) return true;
        return false;
    }

    private static boolean containsTag(com.google.gson.JsonObject json, String field, ResourceLocation id) {
        if (!json.has(field)) return false;
        for (var value : json.getAsJsonArray(field)) {
            ResourceLocation key = ResourceLocation.tryParse(value.getAsString());
            if (key != null && IngredientCompatRules.canonical(key).equals(id)) return true;
        }
        return false;
    }

    public List<ResourceLocation> typeOverride(ResourceLocation id) {
        List<ResourceLocation> explicit = explicitTypes("item_types", id);
        if (explicit != null) return explicit;
        List<String> values = types.get(id.toString());
        if (values != null) return values.stream().map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull).distinct().toList();
        return IngredientCompatRules.current().itemTypes(id);
    }

    public Flavor flavor(ResourceLocation id) {
        Flavor explicit = explicitFlavor("item_flavor", id);
        if (explicit != null) return explicit;
        return flavor.containsKey(id.toString()) ? flavor.get(id.toString())
                : IngredientCompatRules.current().itemFlavor(id);
    }

    /** Flavor configured for any of the tag ids merged into one entry. */
    public Flavor tagFlavor(List<ResourceLocation> ids) {
        for (ResourceLocation id : ids) {
            Flavor explicit = explicitFlavor("tag_flavor", id);
            if (explicit != null) return explicit;
        }
        for (ResourceLocation id : ids) {
            Flavor value = tagFlavor.get(id.toString());
            if (value != null) return value;
        }
        for (ResourceLocation id : ids) {
            Flavor value = IngredientCompatRules.current().tagFlavor(id);
            if (value != null) return value;
        }
        return null;
    }

    public List<ResourceLocation> tagTypeOverride(List<ResourceLocation> ids) {
        for (ResourceLocation id : ids) {
            List<ResourceLocation> explicit = explicitTypes("tag_types", id);
            if (explicit != null) return explicit;
        }
        for (ResourceLocation id : ids) {
            List<String> local = types.get(id.toString());
            if (local != null) return local.stream().map(ResourceLocation::parse).toList();
        }
        for (ResourceLocation id : ids) {
            List<ResourceLocation> override = IngredientCompatRules.current().tagTypes(id);
            if (!override.isEmpty()) return override;
        }
        return List.of();
    }

    private List<ResourceLocation> explicitTypes(String field, ResourceLocation id) {
            if (!compatOverrides().has(field)) return null;
            var map = compatOverrides().getAsJsonObject(field);
            var value = map.get(id.toString());
            if (value == null || value.isJsonNull()) return null;
            List<ResourceLocation> result = new ArrayList<>();
            for (var type : value.getAsJsonArray()) result.add(ResourceLocation.parse(type.getAsString()));
            return List.copyOf(result);
    }

    // ------------------------------------------------------------------ in-game flavor edits

    /**
     * The in-game binding layer: {@code compatOverrides.item_flavor / tag_flavor}. It sits above
     * data-pack compat rules and the older {@code flavor / tagFlavor} maps.
     */
    public Flavor localFlavor(boolean tag, ResourceLocation id) {
        return explicitFlavor(tag ? "tag_flavor" : "item_flavor", id);
    }

    /** Every local binding, keyed by id, for the editor's source marks and for export. */
    public Map<ResourceLocation, Flavor> localFlavors(boolean tag) {
        Map<ResourceLocation, Flavor> result = new LinkedHashMap<>();
        String field = tag ? "tag_flavor" : "item_flavor";
        if (!compatOverrides().has(field)) return result;
        for (var entry : compatOverrides().getAsJsonObject(field).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null || entry.getValue().isJsonNull()) continue;
            try { result.put(id, Flavor.fromJson(entry.getValue().getAsJsonObject())); }
            catch (RuntimeException ignored) { }
        }
        return result;
    }

    /** Sets ({@code value != null}) or clears the local binding and saves the file atomically. */
    public static synchronized boolean setLocalFlavor(boolean tag, ResourceLocation id, Flavor value) {
        IngredientCatalogueConfig next = GSON.fromJson(GSON.toJson(current), IngredientCatalogueConfig.class);
        next.normalise();
        com.google.gson.JsonObject overrides = next.compatOverrides().deepCopy();
        String field = tag ? "tag_flavor" : "item_flavor";
        com.google.gson.JsonObject map = overrides.has(field) ? overrides.getAsJsonObject(field) : new com.google.gson.JsonObject();
        if (value == null) map.remove(id.toString()); else map.add(id.toString(), value.toJson());
        if (map.size() == 0) overrides.remove(field); else overrides.add(field, map);
        next.compatOverrides = overrides;
        return commit(next);
    }

    /** Replaces every local flavor binding at once (loading a flavor scheme). */
    public static synchronized boolean replaceLocalFlavors(Map<ResourceLocation, Flavor> items, Map<ResourceLocation, Flavor> tags) {
        IngredientCatalogueConfig next = GSON.fromJson(GSON.toJson(current), IngredientCatalogueConfig.class);
        next.normalise();
        com.google.gson.JsonObject overrides = next.compatOverrides().deepCopy();
        for (boolean tag : new boolean[]{false, true}) {
            String field = tag ? "tag_flavor" : "item_flavor";
            Map<ResourceLocation, Flavor> values = tag ? tags : items;
            if (values.isEmpty()) { overrides.remove(field); continue; }
            com.google.gson.JsonObject map = new com.google.gson.JsonObject();
            values.forEach((id, value) -> map.add(id.toString(), value.toJson()));
            overrides.add(field, map);
        }
        next.compatOverrides = overrides;
        return commit(next);
    }

    /** Drops every local flavor binding (after an export moved them into a data pack). */
    public static synchronized boolean clearLocalFlavors() {
        IngredientCatalogueConfig next = GSON.fromJson(GSON.toJson(current), IngredientCatalogueConfig.class);
        next.normalise();
        com.google.gson.JsonObject overrides = next.compatOverrides().deepCopy();
        overrides.remove("item_flavor"); overrides.remove("tag_flavor");
        next.compatOverrides = overrides;
        return commit(next);
    }

    private static boolean commit(IngredientCatalogueConfig next) {
        if (currentFile == null || !writable) return false;
        if (!FlavorPresets.writeAtomically(currentFile, GSON.toJson(next))) return false;
        current = next;
        return true;
    }

    private Flavor explicitFlavor(String field, ResourceLocation id) {
        if (!compatOverrides().has(field)) return null;
        try {
            var value = compatOverrides().getAsJsonObject(field).get(id.toString());
            return value == null || value.isJsonNull() ? null : Flavor.fromJson(value.getAsJsonObject());
        } catch(RuntimeException invalid) { return null; }
    }

    private void normalise() {
        if (excluded == null) excluded = new LinkedHashSet<>();
        if (types == null) types = new LinkedHashMap<>();
        if (flavor == null) flavor = new LinkedHashMap<>();
        if (tagFlavor == null) tagFlavor = new LinkedHashMap<>();
        excluded = new LinkedHashSet<>(excluded);
        if (processingRecipeTypes != null) processingRecipeTypes = new ArrayList<>(processingRecipeTypes);
        types = new LinkedHashMap<>(types);
        flavor = new LinkedHashMap<>(flavor);
        tagFlavor = new LinkedHashMap<>(tagFlavor);

        Set<ResourceLocation> items = new LinkedHashSet<>();
        List<ResourceLocation> tags = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();
        for (String entry : excluded) {
            if (entry == null || entry.isBlank()) continue;
            String value = entry.strip();
            if (value.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(value.substring(1));
                if (tag != null) tags.add(tag);
            } else if (value.contains("*")) {
                StringBuilder regex = new StringBuilder();
                for (String part : value.split("\\*", -1)) {
                    if (!regex.isEmpty()) regex.append(".*");
                    regex.append(Pattern.quote(part));
                }
                patterns.add(Pattern.compile(regex.toString()));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(value);
                if (id != null) items.add(id);
            }
        }
        excludedItems = Set.copyOf(items);
        excludedTags = List.copyOf(tags);
        excludedPatterns = List.copyOf(patterns);
        processing = Set.copyOf((processingRecipeTypes == null ? DEFAULT_PROCESSING : processingRecipeTypes).stream().map(ResourceLocation::tryParse)
                .filter(java.util.Objects::nonNull).toList());
    }

    private void save(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // A missing optional correction file must never stop a server.
        }
    }

    public static final class Flavor {
        private boolean source;
        private String effect;
        private List<FlavorStage> stages = List.of();
        private List<ResourceLocation> flavors = List.of();

        public boolean source() { return source; }
        public ResourceLocation effect() { return effect == null ? null : ResourceLocation.tryParse(effect); }
        public List<FlavorStage> stages() { return stages == null ? List.of() : stages; }
        public List<ResourceLocation> flavors() { return flavors == null ? List.of() : flavors; }

        public com.google.gson.JsonObject toJson() {
            var json = new com.google.gson.JsonObject();
            json.addProperty("source", source);
            if (effect != null) json.addProperty("effect", effect);
            if(!flavors().isEmpty()) { var refs=new com.google.gson.JsonArray();flavors().forEach(id->refs.add(id.toString()));json.add("flavors",refs); }
            if (!stages().isEmpty()) {
                var values = new com.google.gson.JsonArray();
                for (var stage : stages()) values.add(stage.toJson());
                json.add("stages",values);
            }
            return json;
        }

        /** A binding written by the in-game editor; legacy inline stages and effect are kept. */
        public static Flavor of(boolean source, List<ResourceLocation> flavors, Flavor previous) {
            Flavor value = new Flavor();
            value.source = source;
            value.flavors = List.copyOf(new LinkedHashSet<>(flavors));
            if (previous != null) { value.effect = previous.effect; value.stages = previous.stages(); }
            return value;
        }

        public static Flavor fromJson(com.google.gson.JsonObject json) {
            Flavor value = new Flavor();
            if (json.has("source")) value.source = json.get("source").getAsBoolean();
            if (json.has("effect")) value.effect = json.get("effect").getAsString();
            if (value.effect != null && ResourceLocation.tryParse(value.effect) == null)
                throw new IllegalArgumentException("Invalid flavor effect: " + value.effect);
            if (json.has("stages")) value.stages = FlavorStage.read(json.getAsJsonArray("stages"));
            if(json.has("flavors")) {
                Set<ResourceLocation> refs=new LinkedHashSet<>();
                for(var raw:json.getAsJsonArray("flavors"))refs.add(ResourceLocation.parse(raw.getAsString()));
                if(refs.size()>64)throw new IllegalArgumentException("Too many bound flavors");
                value.flavors=List.copyOf(refs);
            }
            return value;
        }
    }
}
