package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

/** Temporary repair of known 1.20.1 Compat overrides shipped under recipe/ instead of recipes/. */
public final class CompatRecipePathPatch {
    private static final String OLD_RICE = "farmersdelight:cooked_rice";
    private static final String NEW_RICE = "kaleidoscope_cookery:cooked_rice";
    private static final Set<ResourceLocation> TARGETS;
    static {
        var targets = new HashSet<ResourceLocation>();
        for (String path : List.of("beef_burrito","calamari_roll","chicken_roll","egg_roll",
                "midori_roll","pufferfish_roll","rice_ball","tropical_roll"))
            targets.add(ResourceLocation.parse("culturaldelights:" + path));
        for (String path : List.of("bell_pepper_roll_green","bell_pepper_roll_red","bell_pepper_roll_yellow",
                "calamari_roll","cherry_blossom_roll")) targets.add(ResourceLocation.parse("rusticdelight:" + path));
        targets.add(ResourceLocation.parse("youkaisfeasts:shaved_ice_over_rice"));
        targets.add(ResourceLocation.parse("youkaishomecoming:shaved_ice_over_rice"));
        TARGETS = Set.copyOf(targets);
    }
    private CompatRecipePathPatch() {}

    public static void apply(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager) {
        if (!ModList.get().isLoaded("kaleidoscope_compat")) return;
        boolean unified = manager.listPacks().anyMatch(pack ->
                pack.packId().equals("kaleidoscope_compat:unite_farmersdelight"));
        var host = ModList.get().getModFileById("kaleidoscope_compat").getFile();
        int changed = repair(recipes, unified, id -> {
            String namespace = id.getNamespace().equals("culturaldelights") ? "culturalrecipes"
                    : id.getNamespace().equals("youkaishomecoming") ? "youkaisfeasts" : id.getNamespace();
            String root = "packs/unite_farmersdelight/data/";
            return Files.isRegularFile(host.findResource(root + id.getNamespace() + "/recipes/" + id.getPath() + ".json"))
                    || !Files.isRegularFile(host.findResource(root + namespace + "/recipe/" + id.getPath() + ".json"));
        });
        if (changed > 0) com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.info(
                "Repaired rice inputs in {} recipes affected by Compat's 1.20.1 resource paths", changed);
    }

    public static int repair(Map<ResourceLocation, JsonElement> recipes, boolean unified,
                             Predicate<ResourceLocation> upstreamFixed) {
        if (!unified) return 0;
        int changed = 0;
        for (var id : TARGETS) {
            JsonElement original = recipes.get(id);
            if (original == null || !original.isJsonObject() || upstreamFixed.test(id)) continue;
            JsonObject copy = original.deepCopy().getAsJsonObject();
            if (recipe(copy)) { recipes.put(id, copy); changed++; }
        }
        return changed;
    }

    private static boolean recipe(JsonObject json) {
        boolean changed = false;
        if (json.has("ingredients")) changed |= ingredients(json.get("ingredients"));
        if (json.has("key") && json.get("key").isJsonObject())
            for (var entry : json.getAsJsonObject("key").entrySet()) changed |= ingredients(entry.getValue());
        // Preserve Forge conditional wrappers and the installed mod's ingredient alternatives.
        if (json.has("recipes") && json.get("recipes").isJsonArray())
            for (var branch : json.getAsJsonArray("recipes"))
                if (branch.isJsonObject() && branch.getAsJsonObject().has("recipe")
                        && branch.getAsJsonObject().get("recipe").isJsonObject())
                    changed |= recipe(branch.getAsJsonObject().getAsJsonObject("recipe"));
        return changed;
    }

    private static boolean ingredients(JsonElement value) {
        boolean changed = false;
        if (value.isJsonArray()) {
            for (var entry : value.getAsJsonArray()) changed |= ingredients(entry);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("item") && object.get("item").isJsonPrimitive()
                    && OLD_RICE.equals(object.get("item").getAsString())) {
                object.addProperty("item", NEW_RICE); changed = true;
            }
        }
        return changed;
    }
}
