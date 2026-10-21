package com.chi__you.cuisinejournal.recipe;

import com.chi__you.cuisinejournal.CuisineJournalLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Named snapshots of in-game edits, in two independent kinds:
 *
 * <ul>
 *   <li>{@link Kind#FLAVOR}: in-game flavor presets plus ingredient / tag flavor bindings
 *       (kept together so a binding never points at a missing flavor)</li>
 *   <li>{@link Kind#CATEGORY}: the dish category edits</li>
 * </ul>
 *
 * Built-in schemes come from data packs ({@code data/<ns>/cuisinejournal/schemes/<kind>/*.json},
 * read-only, plus a generated empty "百味纪默认"); the player's own schemes live
 * in {@code config/cuisinejournal/schemes/<kind>/*.json}. Loading replaces that kind's local layer
 * wholesale; saving writes the current layer. The last loaded / saved scheme per kind is
 * remembered so the editor can say whether there are unsaved changes.
 */
public final class SchemeStore {
    public enum Kind {
        FLAVOR("flavor"), CATEGORY("category");
        public final String folder;
        Kind(String folder) { this.folder = folder; }
    }

    /** 0 = generated default, 1 = from a data pack, 2 = the player's own. */
    public record Scheme(String id, Kind kind, byte source, String name, String description, String saved,
                         JsonObject content) {
        public boolean editable() { return source == 2; }
    }

    public static final String DEFAULT_ID = "default";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter SAVED = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
    private static final int MAX_NAME = 48, MAX_DESCRIPTION = 256;

    private static volatile Path configDirectory;
    private static volatile Map<Kind, Map<String, Scheme>> builtIn = Map.of();
    public static Runnable rollbackPoint() { var previous=builtIn; return () -> builtIn=previous; }

    private SchemeStore() {
    }

    private static Path folder(Kind kind) {
        return configDirectory.resolve("cuisinejournal").resolve("schemes").resolve(kind.folder);
    }

    private static Path stateFile() {
        return configDirectory.resolve("cuisinejournal").resolve("schemes").resolve("current.json");
    }

    // ------------------------------------------------------------------ loading

    /** Reads built-in schemes from data packs; called on every data reload. */
    public static synchronized void reload(ResourceManager resources, Path configDirectory) {
        SchemeStore.configDirectory = configDirectory;
        Map<Kind, Map<String, Scheme>> next = new LinkedHashMap<>();
        for (Kind kind : Kind.values()) {
            Map<String, Scheme> found = new TreeMap<>();
            JsonObject emptyContent=new JsonObject();
            if(kind==Kind.FLAVOR){emptyContent.add("flavors",new JsonObject());emptyContent.add("bindings",new JsonObject());}
            else emptyContent.add("categories",new JsonObject());
            found.put(DEFAULT_ID, new Scheme(DEFAULT_ID, kind, (byte) 0, "", "", "", emptyContent));
            String directory = "cuisinejournal/schemes/" + kind.folder;
            resources.listResources(directory, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
                String file = path.getPath().substring(directory.length() + 1, path.getPath().length() - 5);
                String id = path.getNamespace() + ":" + file;
                try (Reader reader = resource.openAsReader()) {
                    found.put(id, parse(id, kind, (byte) 1, JsonParser.parseReader(reader).getAsJsonObject()));
                } catch (Exception failure) {
                    CuisineJournalLog.LOGGER.warn("Skipping scheme {}: {}", path, failure.getMessage());
                }
            });
            next.put(kind, Map.copyOf(found));
        }
        builtIn = Map.copyOf(next);
    }

    public static void clear() {
        builtIn = Map.of();
    }

    private static Scheme parse(String id, Kind kind, byte source, JsonObject json) {
        validateContent(kind,json);
        String name = text(json, "name", id, MAX_NAME);
        String description = text(json, "description", "", MAX_DESCRIPTION);
        String saved = text(json, "saved", "", 32);
        JsonObject content = new JsonObject();
        if (kind == Kind.FLAVOR) {
            content.add("flavors", object(json, "flavors"));
            content.add("bindings", object(json, "bindings"));
        } else content.add("categories", object(json, "categories"));
        return new Scheme(id, kind, source, name, description, saved, content);
    }

    private static String text(JsonObject json, String key, String fallback, int max) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        String value = json.get(key).getAsString();
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key).deepCopy() : new JsonObject();
    }

    /** Built-in schemes first (default, then data packs by id), then the player's own by name. */
    public static synchronized List<Scheme> list(Kind kind) {
        List<Scheme> result = new ArrayList<>(builtIn.getOrDefault(kind, Map.of()).values());
        result.sort(Comparator.comparingInt((Scheme s) -> s.source()).thenComparing(Scheme::id));
        List<Scheme> own = new ArrayList<>(own(kind).values());
        own.sort(Comparator.comparing(Scheme::name, String.CASE_INSENSITIVE_ORDER));
        result.addAll(own);
        return result;
    }

    public static synchronized Scheme get(Kind kind, String id) {
        if (id == null) return null;
        Scheme value = builtIn.getOrDefault(kind, Map.of()).get(id);
        return value != null ? value : own(kind).get(id);
    }

    /** Export the active layer, including unsaved edits; unrelated selected schemes keep their snapshot. */
    public static synchronized Scheme forExport(Kind kind, String id) {
        Scheme selected = get(kind, id);
        if (selected == null) return null;
        String active = currentId(kind);
        if (id.equals(active) || get(kind, active) == null && DEFAULT_ID.equals(id)) {
            return new Scheme(selected.id(), kind, selected.source(), selected.name(), selected.description(),
                    selected.saved(), currentContent(kind));
        }
        return selected;
    }

    private static Map<String, Scheme> own(Kind kind) {
        Map<String, Scheme> result = new LinkedHashMap<>();
        if (configDirectory == null) return result;
        Path folder = folder(kind);
        if (!Files.isDirectory(folder)) return result;
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                String id = "own:" + name.substring(0, name.length() - 5);
                try {
                    result.put(id, parse(id, kind, (byte) 2,
                            JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()));
                } catch (Exception failure) {
                    CuisineJournalLog.LOGGER.warn("Skipping scheme file {}: {}", file, failure.getMessage());
                }
            }
        } catch (IOException failure) {
            CuisineJournalLog.LOGGER.warn("Cannot list schemes in {}: {}", folder, failure.getMessage());
        }
        return result;
    }

    // ------------------------------------------------------------------ current layer

    /** The current local layer of one kind, in scheme content form. */
    public static JsonObject currentContent(Kind kind) {
        JsonObject content = new JsonObject();
        if (kind == Kind.FLAVOR) {
            JsonObject flavors = new JsonObject();
            FlavorPresets.overrides().forEach((id, preset) -> flavors.add(id.toString(), preset.toJson()));
            JsonObject bindings = new JsonObject();
            for (boolean tag : new boolean[]{false, true}) {
                var values = IngredientCatalogueConfig.current().localFlavors(tag);
                if (values.isEmpty()) continue;
                JsonObject map = new JsonObject();
                values.forEach((id, value) -> map.add(id.toString(), value.toJson()));
                bindings.add(tag ? "tag_flavor" : "item_flavor", map);
            }
            content.add("flavors", flavors);
            content.add("bindings", bindings);
        } else content.add("categories", GlobalCategoryStore.localJson());
        return content;
    }

    /** Compare parsed meaning, not optional fields omitted by an older writer. */
    private static JsonElement canonical(Kind kind, JsonObject content) {
        JsonObject value = content.deepCopy();
        if (kind == Kind.CATEGORY) value.add("categories", GlobalCategoryStore.normalisedJson(object(value, "categories")));
        else {
            JsonObject flavors = new JsonObject();
            object(value, "flavors").entrySet().forEach(entry -> flavors.add(entry.getKey(),
                    FlavorPresets.read(ResourceLocation.parse(entry.getKey()), entry.getValue().getAsJsonObject()).toJson()));
            JsonObject bindings = new JsonObject();
            JsonObject rawBindings = object(value, "bindings");
            for (String field : List.of("item_flavor", "tag_flavor")) {
                JsonObject entries = new JsonObject();
                object(rawBindings, field).entrySet().forEach(entry -> entries.add(entry.getKey(),
                        IngredientCatalogueConfig.Flavor.fromJson(entry.getValue().getAsJsonObject()).toJson()));
                if (entries.size() > 0) bindings.add(field, entries);
            }
            value.add("flavors", flavors);
            value.add("bindings", bindings);
        }
        return sorted(value);
    }

    private static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            new TreeMap<>(element.getAsJsonObject().asMap()).forEach((k, v) -> result.add(k, sorted(v)));
            return result;
        }
        if (element.isJsonArray()) {
            var result = new com.google.gson.JsonArray();
            element.getAsJsonArray().forEach(v -> result.add(sorted(v)));
            return result;
        }
        return element;
    }

    /** Whether the current layer differs from the scheme it was last loaded from or saved to. */
    public static synchronized boolean dirty(Kind kind) {
        Scheme current = get(kind, currentId(kind));
        JsonObject base = current == null ? emptyContent(kind) : current.content();
        return !canonical(kind, base).equals(canonical(kind, currentContent(kind)));
    }

    private static JsonObject emptyContent(Kind kind) {
        JsonObject content = new JsonObject();
        if (kind == Kind.FLAVOR) { content.add("flavors", new JsonObject()); content.add("bindings", new JsonObject()); }
        else content.add("categories", new JsonObject());
        return content;
    }

    /** Whether this kind has no local edits at all. */
    public static boolean empty(Kind kind) {
        return canonical(kind, emptyContent(kind)).equals(canonical(kind, currentContent(kind)));
    }

    /** Counts shown in the editor: flavors / item+tag bindings, or category sections. */
    public static int[] counts(Kind kind, JsonObject content) {
        if (kind == Kind.FLAVOR) {
            JsonObject bindings = object(content, "bindings");
            int count = object(bindings, "item_flavor").size() + object(bindings, "tag_flavor").size();
            return new int[]{object(content, "flavors").size(), count};
        }
        JsonObject c = GlobalCategoryStore.normalisedJson(object(content, "categories"));
        int roots = c.getAsJsonArray("roots").size(), subs = c.getAsJsonArray("subcategories").size();
        int assigned = c.getAsJsonObject("assignments").size();
        int hidden = c.getAsJsonArray("blockedRoots").size() + c.getAsJsonArray("blockedSubcategories").size();
        int changed = c.getAsJsonObject("rootOverrides").size() + c.getAsJsonObject("subcategoryOverrides").size()
                + c.getAsJsonObject("parentOverrides").size();
        return new int[]{roots, subs, assigned, hidden, changed};
    }

    // ------------------------------------------------------------------ current scheme memory

    public static synchronized String currentId(Kind kind) {
        JsonObject state = readState();
        return state.has(kind.folder) ? state.get(kind.folder).getAsString() : null;
    }

    private static synchronized boolean setCurrent(Kind kind, String id) {
        JsonObject state = readState();
        if (id == null) state.remove(kind.folder); else state.addProperty(kind.folder, id);
        return FlavorPresets.writeAtomically(stateFile(), GSON.toJson(state));
    }

    private static JsonObject readState() {
        if (configDirectory == null) return new JsonObject();
        Path file = stateFile();
        try {
            if (Files.isRegularFile(file)) return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) { }
        return new JsonObject();
    }

    // ------------------------------------------------------------------ actions

    /** Replaces this kind's local layer with the scheme. */
    public static synchronized boolean load(Kind kind, String id) {
        Scheme scheme = get(kind, id);
        if (scheme == null) return false;
        try { validateContent(kind,scheme.content()); }
        catch (RuntimeException invalid) { CuisineJournalLog.LOGGER.warn("Invalid scheme {}: {}",id,invalid.getMessage()); return false; }
        boolean ok;
        if (kind == Kind.FLAVOR) {
            Map<ResourceLocation, FlavorPresets.Preset> presets = new TreeMap<>();
            for (var entry : object(scheme.content(), "flavors").entrySet()) {
                ResourceLocation key = ResourceLocation.tryParse(entry.getKey());
                if (key == null || !entry.getValue().isJsonObject()) continue;
                try { presets.put(key, FlavorPresets.read(key, entry.getValue().getAsJsonObject())); }
                catch (RuntimeException invalid) { CuisineJournalLog.LOGGER.warn("Scheme {} flavor {}: {}", id, key, invalid.getMessage()); }
            }
            JsonObject bindings = object(scheme.content(), "bindings");
            var items = bindings(bindings, "item_flavor");
            var tags = bindings(bindings, "tag_flavor");
            var previousPresets = new TreeMap<>(FlavorPresets.overrides());
            var previousItems = IngredientCatalogueConfig.current().localFlavors(false);
            var previousTags = IngredientCatalogueConfig.current().localFlavors(true);
            if (!FlavorPresets.replaceLocal(presets)) return false;
            boolean bindingsWritten = false;
            boolean committed = false;
            try {
                bindingsWritten = IngredientCatalogueConfig.replaceLocalFlavors(items, tags);
                committed = bindingsWritten && setCurrent(kind, id);
                return committed;
            } finally {
                if (!committed) {
                    // The stores publish only after persistence succeeds; undo in reverse order.
                    if (bindingsWritten && !IngredientCatalogueConfig.replaceLocalFlavors(previousItems, previousTags))
                        CuisineJournalLog.LOGGER.error("Could not roll back bindings after loading scheme {}", id);
                    if (!FlavorPresets.replaceLocal(previousPresets))
                        CuisineJournalLog.LOGGER.error("Could not roll back presets after loading scheme {}", id);
                }
            }
        } else {
            JsonObject previousCategories = GlobalCategoryStore.localJson();
            String previousId = currentId(kind);
            if (!GlobalCategoryStore.replaceLocal(object(scheme.content(), "categories"))) return false;
            if (setCurrent(kind, id)) return true;
            if (!GlobalCategoryStore.replaceLocal(previousCategories))
                CuisineJournalLog.LOGGER.error("Could not roll back categories after loading scheme {}", id);
            if (!setCurrent(kind, previousId))
                CuisineJournalLog.LOGGER.error("Could not roll back current scheme metadata after loading scheme {}", id);
            return false;
        }
    }

    private static Map<ResourceLocation, IngredientCatalogueConfig.Flavor> bindings(JsonObject json, String field) {
        Map<ResourceLocation, IngredientCatalogueConfig.Flavor> result = new LinkedHashMap<>();
        for (var entry : object(json, field).entrySet()) {
            ResourceLocation key = ResourceLocation.tryParse(entry.getKey());
            if (key == null || !entry.getValue().isJsonObject()) continue;
            try { result.put(key, IngredientCatalogueConfig.Flavor.fromJson(entry.getValue().getAsJsonObject())); }
            catch (RuntimeException ignored) { }
        }
        return result;
    }

    private static void validateContent(Kind kind, JsonObject content) {
        if (kind == Kind.CATEGORY) {
            if (!content.has("categories") || !content.get("categories").isJsonObject())
                throw new IllegalArgumentException("Missing category content");
            GlobalCategoryStore.normalisedJson(content.getAsJsonObject("categories"));
            return;
        }
        if (!content.has("flavors") || !content.get("flavors").isJsonObject()
                || !content.has("bindings") || !content.get("bindings").isJsonObject())
            throw new IllegalArgumentException("Missing flavor content");
        for (var entry : content.getAsJsonObject("flavors").entrySet())
            FlavorPresets.read(ResourceLocation.parse(entry.getKey()),entry.getValue().getAsJsonObject());
        var bindings = content.getAsJsonObject("bindings");
        for (String field : List.of("item_flavor","tag_flavor")) if (bindings.has(field))
            for (var entry : bindings.getAsJsonObject(field).entrySet()) {
                ResourceLocation.parse(entry.getKey());
                IngredientCatalogueConfig.Flavor.fromJson(entry.getValue().getAsJsonObject());
            }
    }

    /**
     * Saves the current layer as the player's scheme {@code id} (null: a new one named
     * {@code name}; an existing own scheme with the same name is overwritten).
     *
     * @return the saved scheme's id, or null on failure
     */
    public static synchronized String save(Kind kind, String id, String name, String description) {
        if (configDirectory == null) return null;
        Scheme existing = id == null ? null : get(kind, id);
        if (id != null && (existing == null || !existing.editable())) return null;
        String cleanName = clean(name == null ? existing == null ? "" : existing.name() : name, MAX_NAME);
        if (cleanName.isEmpty()) return null;
        String cleanDescription = clean(description == null ? existing == null ? "" : existing.description() : description, MAX_DESCRIPTION);
        if (id == null) {
            for (Scheme own : own(kind).values()) if (own.name().equals(cleanName)) { id = own.id(); break; }
        }
        if(id==null && list(kind).size()>=512)return null;
        String file = id != null ? id.substring(4) : fileName(kind, cleanName);
        JsonObject json = new JsonObject();
        json.addProperty("name", cleanName);
        json.addProperty("description", cleanDescription);
        json.addProperty("saved", LocalDateTime.now().format(SAVED));
        currentContent(kind).entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
        if (!FlavorPresets.writeAtomically(folder(kind).resolve(file + ".json"), GSON.toJson(json))) return null;
        String saved = "own:" + file;
        return setCurrent(kind, saved) ? saved : null;
    }

    /** Saves a copy of any scheme (built-in included) as the player's own, under {@code name}. */
    public static synchronized String copy(Kind kind, String id, String name) {
        Scheme source = get(kind, id);
        String cleanName = clean(name, MAX_NAME);
        if (source == null || cleanName.isEmpty() || configDirectory == null) return null;
        String file = fileName(kind, cleanName);
        JsonObject json = new JsonObject();
        json.addProperty("name", cleanName);
        json.addProperty("description", source.description());
        json.addProperty("saved", LocalDateTime.now().format(SAVED));
        source.content().entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue().deepCopy()));
        return FlavorPresets.writeAtomically(folder(kind).resolve(file + ".json"), GSON.toJson(json)) ? "own:" + file : null;
    }

    public static synchronized boolean rename(Kind kind, String id, String name) {
        Scheme existing = get(kind, id);
        if (existing == null || !existing.editable()) return false;
        String cleanName = clean(name, MAX_NAME);
        if (cleanName.isEmpty()) return false;
        JsonObject json = new JsonObject();
        json.addProperty("name", cleanName);
        json.addProperty("description", existing.description());
        json.addProperty("saved", existing.saved());
        existing.content().entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
        return FlavorPresets.writeAtomically(folder(kind).resolve(id.substring(4) + ".json"), GSON.toJson(json));
    }

    public static synchronized boolean delete(Kind kind, String id) {
        Scheme existing = get(kind, id);
        if (existing == null || !existing.editable()) return false;
        try {
            Files.deleteIfExists(folder(kind).resolve(id.substring(4) + ".json"));
        } catch (IOException failure) {
            CuisineJournalLog.LOGGER.error("Could not delete scheme {}", id, failure);
            return false;
        }
        if (id.equals(currentId(kind))) setCurrent(kind, null);
        return true;
    }

    /**
     * First start: when a kind has no local edits and no remembered scheme, load the configured
     * default scheme (if any). Returns whether anything was loaded.
     */
    public static synchronized boolean applyDefault(Kind kind, String id) {
        if (id == null || id.isBlank() || currentId(kind) != null || !empty(kind)) return false;
        String key = get(kind, id) != null ? id : get(kind, "own:" + id) != null ? "own:" + id : null;
        if (key == null) {
            CuisineJournalLog.LOGGER.warn("Default {} scheme '{}' not found", kind.folder, id);
            return false;
        }
        return load(kind, key);
    }

    private static String clean(String value, int max) {
        String result = value == null ? "" : value.strip().replaceAll("[\\r\\n\\t]", " ");
        return result.length() > max ? result.substring(0, max) : result;
    }

    /** A file name from the scheme name: letters, digits and CJK kept; a counter avoids clashes. */
    private static String fileName(Kind kind, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_-]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "scheme";
        if (base.length() > 40) base = base.substring(0, 40);
        String candidate = base;
        int counter = 2;
        while (Files.exists(folder(kind).resolve(candidate + ".json"))) candidate = base + "_" + counter++;
        return candidate;
    }
}
