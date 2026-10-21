package com.chi__you.cuisinejournal.compat;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/** Repairs only the audited host tables, without replacing other packs or global modifiers. */
public final class SquidLootCompat {
    private static final ResourceLocation SQUID = id("minecraft:entities/squid");
    private static final ResourceLocation GLOW = id("minecraft:entities/glow_squid");
    private static final ResourceLocation DUMPLING_MODIFIER = id("dumplings_delight:loot_modifiers/add_calamari.json");
    private SquidLootCompat() {}

    public static int apply(Map<ResourceLocation, JsonElement> tables, ResourceManager resources,
                            Predicate<String> loaded) {
        return apply(tables, loaded, table -> resources.getResource(ResourceLocation.fromNamespaceAndPath(
                table.getNamespace(), "loot_tables/" + table.getPath() + ".json"))
                .map(resource -> Set.of("mod_resources", "mod:culturaldelights", "mod:dumplings_delight")
                        .contains(resource.sourcePackId())).orElse(false), brokenDumplingModifier(resources));
    }

    static boolean brokenDumplingModifier(ResourceManager resources) {
        var resource = resources.getResource(DUMPLING_MODIFIER);
        if (resource.isEmpty()) return false;
        try (var reader = resource.get().openAsReader()) {
            var value = JsonParser.parseReader(reader);
            return value.isJsonObject() && !value.getAsJsonObject().has("type");
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    static int apply(Map<ResourceLocation, JsonElement> tables, Predicate<String> loaded,
                     Predicate<ResourceLocation> modResource, boolean brokenDumplingModifier) {
        if (!loaded.test("culturaldelights")) return 0;
        int changed = 0;
        for (var table : List.of(SQUID, GLOW)) {
            if (!modResource.test(table)) continue;
            boolean glow = table.equals(GLOW);
            var raw = tables.get(table);
            boolean cultural = culturalTable(glow).equals(raw);
            boolean dumpling = !glow && loaded.test("dumplings_delight") && dumplingTable().equals(raw);
            if (!cultural && !dumpling) continue;
            var result = raw.deepCopy().getAsJsonObject();
            var pools = result.getAsJsonArray("pools");
            if (cultural) {
                pools.add(named(pool(glow ? "minecraft:glow_ink_sac" : "minecraft:ink_sac", 1, 3, 0, 1, false),
                        "cuisinejournal:restored_ink"));
                // A valid upstream modifier already supplies this chance roll. Never enable the broken one.
                if (!glow && loaded.test("dumplings_delight") && brokenDumplingModifier)
                    pools.add(named(dumplingTable().getAsJsonArray("pools").get(1).deepCopy().getAsJsonObject(),
                            "cuisinejournal:restored_dumpling_calamari"));
            } else {
                pools.add(named(culturalTable(false).getAsJsonArray("pools").get(0).deepCopy().getAsJsonObject(),
                        "cuisinejournal:restored_cultural_squid"));
            }
            tables.put(table, result);
            changed++;
        }
        return changed;
    }

    // Structural signatures of installed Cultural Delights 0.16.7 and Dumplings Delight 1.3.3.
    // Any upstream/custom changes make the repair opt out rather than overwrite their intent.
    static JsonObject culturalTable(boolean glow) {
        var result = table();
        result.getAsJsonArray("pools").add(pool(glow ? "culturaldelights:glow_squid" : "culturaldelights:squid",
                1, 1, 1, 1, true));
        return result;
    }
    static JsonObject dumplingTable() {
        var result = table();
        var pools = result.getAsJsonArray("pools");
        pools.add(pool("minecraft:ink_sac", 1, 3, 0, 1, false));
        var meat = pool("dumplings_delight:calamari", 1, 3, 0, 1, false);
        var conditions = new JsonArray();
        var chance = new JsonObject();
        chance.addProperty("condition", "minecraft:random_chance");
        chance.addProperty("chance", .5);
        conditions.add(chance);
        meat.add("conditions", conditions);
        pools.add(meat);
        result.addProperty("random_sequence", "minecraft:entities/squid");
        return result;
    }
    private static JsonObject table() {
        var result = new JsonObject();
        result.addProperty("type", "minecraft:entity");
        result.add("pools", new JsonArray());
        return result;
    }
    private static JsonObject named(JsonObject pool, String name) { pool.addProperty("name", name); return pool; }
    private static JsonObject pool(String item, int min, int max, int lootMin, int lootMax, boolean cultural) {
        var pool = new JsonObject();
        pool.addProperty("rolls", 1);
        if (cultural) pool.addProperty("bonus_rolls", 0);
        var entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        var functions = new JsonArray();
        var count = new JsonObject();
        count.addProperty("function", "minecraft:set_count");
        count.add("count", uniform(min, max));
        if (cultural) count.addProperty("add", false);
        functions.add(count);
        var looting = new JsonObject();
        looting.addProperty("function", "minecraft:looting_enchant");
        looting.add("count", uniform(lootMin, lootMax));
        functions.add(looting);
        entry.add("functions", functions);
        var entries = new JsonArray();
        entries.add(entry);
        pool.add("entries", entries);
        return pool;
    }
    private static JsonObject uniform(int min, int max) {
        var count = new JsonObject();
        count.addProperty("type", "minecraft:uniform");
        count.addProperty("min", min);
        count.addProperty("max", max);
        return count;
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
