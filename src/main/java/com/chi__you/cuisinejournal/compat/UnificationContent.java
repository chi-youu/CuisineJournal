package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/** Reload-scoped policy for the explicitly listed food groups. */
public final class UnificationContent {
    public static final ResourceLocation POLICY = id("cuisinejournal:cuisinejournal/unification/runtime.json");
    public record Plan(Map<ResourceLocation, ResourceLocation> replacements,
                       Map<String, ResourceLocation> winners, Set<ResourceLocation> excluded,
                       Set<String> disabledPlants, Set<ResourceLocation> keptRecipeOutputs) {
        public ResourceLocation replace(ResourceLocation item) { return replacements.getOrDefault(item, item); }
        public ResourceLocation winner(String group) { return winners.get(group); }
        public boolean enabled() { return !winners.isEmpty(); }
    }
    public static final Plan EMPTY = new Plan(Map.of(), Map.of(), Set.of(), Set.of(), Set.of());
    private UnificationContent() {}

    public static Plan compile(JsonObject document, boolean enabled, Predicate<ResourceLocation> exists, Predicate<String> loaded) {
        if (!enabled) return EMPTY;
        var compiled = UnificationRules.compile(document, true, exists, loaded);
        var replacements = new LinkedHashMap<>(compiled.replacements());
        var winners = new LinkedHashMap<String, ResourceLocation>();
        var keptRecipeOutputs = new LinkedHashSet<ResourceLocation>();
        for (var entry : document.entrySet()) {
            if (compiled.problems().stream().anyMatch(p -> p.startsWith(entry.getKey() + ":"))) continue;
            var rule = entry.getValue().getAsJsonObject();
            if (rule.has("enabled") && !rule.get("enabled").getAsBoolean()) continue;
            if (rule.has("required_mods") && !allLoaded(rule.getAsJsonArray("required_mods"), loaded)) continue;
            var order = UnificationRules.priority(rule, loaded);
            if (rule.has("keep_recipes") && rule.get("keep_recipes").getAsBoolean())
                rule.getAsJsonArray("items").forEach(value -> {
                    var item = id(value.getAsString());
                    if (exists.test(item)) keptRecipeOutputs.add(item);
                });
            if (rule.has("keep_recipe_items")) rule.getAsJsonArray("keep_recipe_items").forEach(value -> {
                var item=id(value.getAsString());if(exists.test(item))keptRecipeOutputs.add(item);
            });
            for (var item : order) {
                if (exists.test(item)) { winners.put(entry.getKey(), item); break; }
            }
        }
        var excluded = new LinkedHashSet<ResourceLocation>();
        if (exists.test(id("dungeonsdelight:soft_serve_sniffer_egg")))
            winners.put("cuisinejournal:sniffer_smoking", id("dungeonsdelight:soft_serve_sniffer_egg"));
        var egg = winners.get("cuisinejournal:sniffer_egg");
        if (egg != null && Set.of("trailandtales_delight", "immortalers_delight").contains(egg.getNamespace())
                && exists.test(id("dungeonsdelight:soft_serve_sniffer_egg"))) excluded.add(id("dungeonsdelight:soft_serve_sniffer_egg"));
        if (winners.containsKey("cuisinejournal:glow_squid")) {
            for (String item : List.of("oceanic_delight:glow_squid_tentacles", "crabbersdelight:raw_glow_squid_tentacles",
                    "crabbersdelight:cooked_glow_squid_tentacles")) if (exists.test(id(item))) excluded.add(id(item));
        }
        if (id("vintagedelight:peanut").equals(winners.get("cuisinejournal:peanut"))
                && exists.test(id("braziliandelight:peanut_seeds"))) {
            excluded.add(id("braziliandelight:peanut_seeds"));
            replacements.put(id("braziliandelight:peanut_seeds"), id("vintagedelight:peanut"));
        }
        var plants = new LinkedHashSet<String>();
        plant(plants, winners, "cucumber", "youkaishomecoming", "youkaishomecoming:wild_cucumber");
        plant(plants, winners, "cucumber", "culturaldelights", "culturaldelights:wild_cucumbers");
        plant(plants, winners, "cucumber", "vintagedelight", "vintagedelight:wild_cucumbers");
        plant(plants, winners, "peanut", "braziliandelight", "braziliandelight:wild_peanuts");
        plant(plants, winners, "peanut", "vintagedelight", "vintagedelight:wild_peanuts");
        plant(plants, winners, "corn", "corn_delight", "corn_delight:wild_corn");
        plant(plants, winners, "corn", "culturaldelights", "culturaldelights:wild_corn");
        plant(plants, winners, "corn", "braziliandelight", "braziliandelight:wild_corn");
        plant(plants, winners, "corn", "farm_and_charm", "farm_and_charm:wild_corn");
        plant(plants, winners, "oat", "farm_and_charm", "farm_and_charm:wild_oat");
        plant(plants, winners, "oat", "vintagedelight", "vintagedelight:wild_oats");
        plant(plants, winners, "strawberry", "farm_and_charm", "farm_and_charm:wild_strawberries");
        plant(plants, winners, "coffee_raw", "rusticdelight", "rusticdelight:wild_coffee");
        plant(plants, winners, "coffee_raw", "herbalbrews", "herbalbrews:wild_coffee_plant");
        plant(plants, winners, "coffee_raw", "braziliandelight", "braziliandelight:wild_coffee_bush");
        // Brazilian beans are roasted from berries, not the raw beans used by Rustic/Youkai.
        var roasted = winners.get("cuisinejournal:coffee_roasted");
        if (roasted != null && !roasted.getNamespace().equals("braziliandelight")) {
            plants.add("braziliandelight:wild_coffee_bush");
            if (exists.test(id("braziliandelight:coffee_seeds"))) {
                excluded.add(id("braziliandelight:coffee_seeds"));
                var seed = winners.get("cuisinejournal:coffee_raw");
                if (seed != null) replacements.put(id("braziliandelight:coffee_seeds"), seed);
            }
        }
        UnificationCrops.configure(winners,replacements,excluded,plants,exists);
        UnificationIngredientAliases.configure(winners,replacements,excluded,plants,exists);
        return new Plan(Map.copyOf(replacements), Map.copyOf(winners), Set.copyOf(excluded), Set.copyOf(plants), Set.copyOf(keptRecipeOutputs));
    }
    private static void plant(Set<String> plants, Map<String, ResourceLocation> winners, String group, String mod, String plant) {
        var winner = winners.get("cuisinejournal:" + group);
        if (winner != null && !winner.getNamespace().equals(mod)) plants.add(plant);
    }
    private static boolean allLoaded(JsonArray array, Predicate<String> loaded) {
        for (var value : array) if (!loaded.test(value.getAsString())) return false;
        return true;
    }
    public static JsonObject encode(Plan plan) {
        var result = new JsonObject(); var replacements = new JsonObject(); var winners = new JsonObject();
        plan.replacements().forEach((from, to) -> replacements.addProperty(from.toString(), to.toString()));
        plan.winners().forEach((group, item) -> winners.addProperty(group, item.toString()));
        result.add("replacements", replacements); result.add("winners", winners);
        var excluded = new JsonArray(); plan.excluded().forEach(item -> excluded.add(item.toString())); result.add("excluded", excluded);
        var plants = new JsonArray(); plan.disabledPlants().forEach(plants::add); result.add("disabled_plants", plants);
        var kept = new JsonArray(); plan.keptRecipeOutputs().forEach(item -> kept.add(item.toString())); result.add("kept_recipe_outputs", kept);
        return result;
    }
    public static Plan read(ResourceManager resources) {
        var resource = resources.getResource(POLICY); if (resource.isEmpty()) return EMPTY;
        try (var reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var replacements = new HashMap<ResourceLocation, ResourceLocation>();
            var winners = new HashMap<String, ResourceLocation>();
            json.getAsJsonObject("replacements").entrySet().forEach(e -> replacements.put(id(e.getKey()), id(e.getValue().getAsString())));
            json.getAsJsonObject("winners").entrySet().forEach(e -> winners.put(e.getKey(), id(e.getValue().getAsString())));
            var excluded = new HashSet<ResourceLocation>(); json.getAsJsonArray("excluded").forEach(e -> excluded.add(id(e.getAsString())));
            var plants = new HashSet<String>(); json.getAsJsonArray("disabled_plants").forEach(e -> plants.add(e.getAsString()));
            var kept = new HashSet<ResourceLocation>();
            if (json.has("kept_recipe_outputs")) json.getAsJsonArray("kept_recipe_outputs").forEach(e -> kept.add(id(e.getAsString())));
            return new Plan(Map.copyOf(replacements), Map.copyOf(winners), Set.copyOf(excluded), Set.copyOf(plants), Set.copyOf(kept));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Invalid active unification policy", failure);
        }
    }
    public static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
