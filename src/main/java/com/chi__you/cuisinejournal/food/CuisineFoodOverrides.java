package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.CuisineJournal;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Reads the foodConfigs section used by SOL without depending on SOL at runtime. */
public final class CuisineFoodOverrides {
    private static final String FILE_NAME = "cuisinejournal-food.json5";
    private static volatile Map<ResourceLocation, FoodValues> values = Map.of();

    public static Runnable rollbackPoint() { var previous=values;return ()->values=previous; }

    private CuisineFoodOverrides() {
    }

    public static void reload() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if (!Files.isRegularFile(path)) {
            values = Map.of();
            return;
        }
        Map<ResourceLocation, FoodValues> loaded = new HashMap<>();
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
            reader.setLenient(true);
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject foods = root.has("foodConfigs") && root.get("foodConfigs").isJsonObject()
                    ? root.getAsJsonObject("foodConfigs") : root;
            for (Map.Entry<String, JsonElement> entry : foods.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject food = entry.getValue().getAsJsonObject();
                Integer nutrition = integer(food, "nutrition");
                Float saturation = decimal(food, "saturationModifier");
                Float regen = decimal(food, "healthRegenModifier");
                Float drinkBonus = food.has("drinkBonus") ? decimal(food, "drinkBonus") : -1F;
                if (nutrition == null || saturation == null || regen == null
                        || drinkBonus == null
                        || nutrition < 0 || nutrition > 1000 || saturation < 0 || saturation > 100
                        || regen < 0 || regen > 100 || drinkBonus < -1 || drinkBonus > 100
                        || !Float.isFinite(saturation) || !Float.isFinite(regen)
                        || !Float.isFinite(drinkBonus)) {
                    CuisineJournal.LOGGER.warn("Skipping invalid food definition {} in {}", id, path);
                    continue;
                }
                List<EffectValues> effects = new ArrayList<>();
                if (food.has("extraEffects") && food.get("extraEffects").isJsonArray()) {
                    for (JsonElement effectElement : food.getAsJsonArray("extraEffects")) {
                        if (!effectElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject effect = effectElement.getAsJsonObject();
                        ResourceLocation effectId = effectId(effect);
                        Float activeFraction = decimal(effect, "duration");
                        Integer level = integer(effect, "amplifier");
                        if (effectId == null || activeFraction == null || level == null
                                || !Float.isFinite(activeFraction) || activeFraction < 0
                                || activeFraction > 1 || level < 1 || level > 256) {
                            CuisineJournal.LOGGER.warn("Skipping invalid effect for {} in {}", id, path);
                            continue;
                        }
                        effects.add(new EffectValues(effectId, activeFraction, level));
                    }
                }
                loaded.put(id, new FoodValues(nutrition, saturation, regen, drinkBonus, List.copyOf(effects)));
            }
            values = Collections.unmodifiableMap(loaded);
            CuisineJournal.LOGGER.info("Loaded {} food definitions from {}", loaded.size(), path);
        } catch (IOException | RuntimeException failure) {
            CuisineJournal.LOGGER.error("Could not read {}; keeping previous food definitions", path, failure);
        }
    }

    public static FoodValues get(ResourceLocation id) {
        return values.get(id);
    }

    private static Integer integer(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Float decimal(JsonObject object, String key) {
        try {
            return object.has(key) ? object.get(key).getAsFloat() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ResourceLocation effectId(JsonObject object) {
        try {
            return object.has("ID") ? ResourceLocation.tryParse(object.get("ID").getAsString()) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record FoodValues(int nutrition, float saturationModifier, float healthRegenModifier,
                             float drinkBonus, List<EffectValues> extraEffects) {
    }

    public record EffectValues(ResourceLocation id, float activeFraction, int level) {
    }
}
