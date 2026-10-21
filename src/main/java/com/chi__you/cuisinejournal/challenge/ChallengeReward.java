package com.chi__you.cuisinejournal.challenge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * What completing a challenge grants.
 *
 * <p>No custom reward items exist, and none are invented: a pack may hand out
 * items that already exist, advancements, XP or a cosmetic journal badge.
 * Command rewards are supported because servers ask for them, but they only run
 * when {@code challenges.allowCommandRewards} is switched on.</p>
 *
 * @param cookingXp  Cuisine Journal progression XP
 * @param masteryXp  mastery points, applied to the dish that completed it
 * @param vanillaXp  ordinary experience orbs
 * @param advancement an advancement id to award, or null
 * @param badge      a cosmetic journal badge id, or null
 * @param items      item ids with counts, written as {@code "modid:item 3"}
 * @param commands   server commands, gated behind configuration
 */
public record ChallengeReward(int cookingXp,
                              int masteryXp,
                              int vanillaXp,
                              ResourceLocation advancement,
                              ResourceLocation badge,
                              List<String> items,
                              List<String> commands, ResourceLocation masteryDish) {
    public ChallengeReward(int cookingXp,int masteryXp,int vanillaXp,ResourceLocation advancement,ResourceLocation badge,
                           List<String> items,List<String> commands){this(cookingXp,masteryXp,vanillaXp,advancement,badge,items,commands,null);}

    public static final ChallengeReward NONE =
            new ChallengeReward(0, 0, 0, null, null, List.of(), List.of());

    public ChallengeReward {
        items = normalizeItems(items);
        commands = List.copyOf(commands);
        cookingXp = Math.max(0, cookingXp);
        masteryXp = Math.max(0, masteryXp);
        vanillaXp = Math.max(0, vanillaXp);
    }

    public record ItemReward(ResourceLocation id, int count) {}

    public static ItemReward parseItem(String entry) {
        if (entry == null || entry.isBlank()) return null;
        String[] parts = entry.strip().split("\\s+");
        if (parts.length > 2) return null;
        ResourceLocation id = ResourceLocation.tryParse(parts[0]);
        if (id == null) return null;
        try {
            int count = parts.length == 1 ? 1 : Integer.parseInt(parts[1]);
            return count > 0 && count <= 4096 ? new ItemReward(id, count) : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    public static List<String> normalizeItems(List<String> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String entry : entries) {
            ItemReward item = parseItem(entry);
            if (item == null) {
                com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Skipping invalid challenge item reward: '{}'", entry);
                continue;
            }
            if (result.size() == 64) {
                com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Challenge item rewards exceed the 64-entry limit");
                break;
            }
            String normalized = item.id() + " " + item.count();
            if (normalized.length() > 256) {
                com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Challenge item reward id is too long: '{}'", entry);
                continue;
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    public static ChallengeReward fromJson(JsonObject json) {
        int cookingXp = json.has("cooking_xp") ? json.get("cooking_xp").getAsInt() : 0;
        int masteryXp = json.has("mastery_xp") ? json.get("mastery_xp").getAsInt() : 0;
        int vanillaXp = json.has("vanilla_xp") ? json.get("vanilla_xp").getAsInt() : 0;

        ResourceLocation advancement = optionalId(json, "advancement");
        ResourceLocation badge = optionalId(json, "badge");

        return new ChallengeReward(cookingXp, masteryXp, vanillaXp, advancement, badge,
                stringList(json, "items"), stringList(json, "commands"),optionalId(json,"mastery_dish"));
    }

    private static ResourceLocation optionalId(JsonObject json, String key) {
        if (!json.has(key)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(json.get(key).getAsString());
        if (id == null) {
            throw new ChallengeParseException("reward \"" + key + "\" is not a valid id");
        }
        return id;
    }

    private static List<String> stringList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray array = json.getAsJsonArray(key);
        List<String> values = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            values.add(array.get(i).getAsString());
        }
        return values;
    }
}
