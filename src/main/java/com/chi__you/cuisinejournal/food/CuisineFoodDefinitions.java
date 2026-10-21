package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.CuisineJournal;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Per-item final values loaded from data/<namespace>/cuisinejournal/foods/*.json. */
public final class CuisineFoodDefinitions {
    private static final String DIRECTORY = "cuisinejournal/foods";
    public static final ResourceLocation DEFAULT_DIMENSION =
            new ResourceLocation("minecraft", "overworld");
    private static volatile Map<ResourceLocation, Values> definitions = Map.of();
    public static Runnable rollbackPoint() { var previous=definitions; return () -> definitions=previous; }

    private CuisineFoodDefinitions() {
    }

    public static void reload(ResourceManager resources) {
        Map<ResourceLocation, Values> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> file :
                resources.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ResourceLocation item = ResourceLocation.tryParse(json.get("item").getAsString());
                if (item == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)
                        || net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item)
                        == net.minecraft.world.item.Items.AIR) {
                    throw new IllegalArgumentException("invalid item ID");
                }
                var registered = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item);
                if (registered != net.minecraft.world.item.Items.CAKE
                        && new net.minecraft.world.item.ItemStack(registered).getFoodProperties(null) == null) {
                    throw new IllegalArgumentException("item has no food properties");
                }
                Float health = number(json, "health");
                Float regen = number(json, "regen_per_second");
                Integer duration = wholeNumber(json, "duration_seconds");
                Integer slots = wholeNumber(json, "slot_cost");
                boolean repeatable = booleanValue(json, "repeatable", false);
                String slotType = json.has("slot_type") ? json.get("slot_type").getAsString() : null;
                ResourceLocation dimension = json.has("dimension")
                        ? ResourceLocation.tryParse(json.get("dimension").getAsString())
                        : DEFAULT_DIMENSION;
                if (dimension == null) {
                    throw new IllegalArgumentException("invalid dimension id");
                }
                List<Effect> effects = json.has("effects") ? readEffects(json.getAsJsonArray("effects")) : null;
                List<String> flavorSources = readFlavorSources(json);
                if (health != null && (!Float.isFinite(health) || health < 0 || health > 1000)
                        || regen != null && (!Float.isFinite(regen) || regen < 0 || regen > 100)
                        || duration != null && (duration < 1 || duration > 2_592_000)
                        || slots != null && (slots < 1 || slots > 3)
                        || slotType != null && !slotType.equals("food") && !slotType.equals("drink")) {
                    throw new IllegalArgumentException("food values out of range");
                }
                if (loaded.putIfAbsent(item, new Values(health, regen, duration, slots,
                        slotType, repeatable, effects, dimension, flavorSources)) != null) {
                    throw new IllegalArgumentException("duplicate definition for " + item);
                }
            } catch (Exception failure) {
                CuisineJournal.LOGGER.warn("Skipping food definition {}: {}", file.getKey(), failure.getMessage());
            }
        }
        definitions = Map.copyOf(loaded);
        CuisineJournal.LOGGER.info("Loaded {} food definitions from datapacks", loaded.size());
    }

    public static Values get(ResourceLocation item) {
        return definitions.get(item);
    }

    /** null means automatic; an explicitly empty array means no inherited flavors. */
    public static List<String> readFlavorSources(JsonObject json) {
        if (!json.has("flavor_sources")) return null;
        List<String> sources = new ArrayList<>();
        for (var raw : json.getAsJsonArray("flavor_sources")) {
            String reference = raw.getAsString();
            ResourceLocation.parse(reference.startsWith("#") ? reference.substring(1) : reference);
            if (!sources.contains(reference)) sources.add(reference);
        }
        if (sources.size() > 64) throw new IllegalArgumentException("Too many flavor sources");
        return List.copyOf(sources);
    }

    private static Float number(JsonObject json, String name) {
        return json.has(name) ? json.get(name).getAsFloat() : null;
    }

    private static boolean booleanValue(JsonObject json, String name, boolean fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        JsonElement value = json.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static Integer wholeNumber(JsonObject json, String name) {
        if (!json.has(name)) {
            return null;
        }
        JsonElement value = json.get(name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        String raw = value.getAsString();
        if (!raw.matches("-?[0-9]+")) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        return Integer.parseInt(raw);
    }

    private static List<Effect> readEffects(JsonArray values) {
        if (values.size() > 64) {
            throw new IllegalArgumentException("too many food effects");
        }
        List<Effect> effects = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject json = value.getAsJsonObject();
            ResourceLocation id = ResourceLocation.tryParse(json.get("id").getAsString());
            Integer level = wholeNumber(json, "level");
            Float fraction = number(json, "active_fraction");
            if (id == null || level == null || level < 1 || level > 256
                    || fraction == null || !Float.isFinite(fraction)
                    || fraction < 0 || fraction > 1) {
                throw new IllegalArgumentException("invalid food effect");
            }
            effects.add(new Effect(id, level, fraction));
        }
        return List.copyOf(effects);
    }

    public record Effect(ResourceLocation id, int level, float activeFraction, int durationTicks) {
        public Effect(ResourceLocation id, int level, float activeFraction) {
            this(id, level, activeFraction, 0);
        }
        public int activeTicks(int foodTicks) {
            int fractionTicks = Math.max(0, Math.round(activeFraction * foodTicks));
            return durationTicks > 0 ? Math.min(durationTicks, fractionTicks) : fractionTicks;
        }
    }

    public record Values(Float health, Float regenPerSecond, Integer durationSeconds,
                         Integer slotCost, String slotType, boolean repeatable, List<Effect> effects,
                         ResourceLocation dimension, List<String> flavorSources) {
        public Values {
            flavorSources = flavorSources == null ? null : List.copyOf(flavorSources);
            effects = effects == null ? null : List.copyOf(effects);
        }
        public Values(Float health, Float regenPerSecond, Integer durationSeconds, Integer slotCost,
                      String slotType, boolean repeatable, List<Effect> effects, ResourceLocation dimension) {
            this(health, regenPerSecond, durationSeconds, slotCost, slotType, repeatable, effects, dimension, null);
        }
        /** Compatibility constructor: old definitions are overworld food. */
        public Values(Float health, Float regenPerSecond, Integer durationSeconds,
                      Integer slotCost, String slotType, boolean repeatable, List<Effect> effects) {
            this(health, regenPerSecond, durationSeconds, slotCost, slotType, repeatable,
                    effects, DEFAULT_DIMENSION);
        }
    }
}
