package com.chi__you.cuisinejournal.culinary;

import com.chi__you.cuisinejournal.data.NbtUtil;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One line of the journal's Recent Activity feed.
 *
 * <p>Stored as a compact triple rather than pre-rendered text so the feed can
 * be re-translated when the player changes language, and so a renamed item
 * still shows its current name.</p>
 *
 * @param type    what happened
 * @param subject the dish, ingredient, method or challenge involved; may be null
 * @param detail  a small numeric payload whose meaning depends on {@code type}
 * @param day     the Minecraft day it happened on
 * @param stars   the cook's stars with the dish at that moment; 0 for other event types
 * @param cookName who cooked it, so the history can rebuild the stack's tooltip
 */
public record ActivityEntry(Type type, ResourceLocation subject, long detail, long day,
                            int stars, String cookName, long amount, boolean perfect, int starCap,
                            java.util.UUID cookId) {

    public ActivityEntry(Type type, ResourceLocation subject, long detail, long day, int stars, String cookName) {
        this(type, subject, detail, day, stars, cookName, 1, false, MasteryRank.highestIndex(), null);
    }

    public static ActivityEntry prepared(ResourceLocation subject, long xp, long day, long amount, MealStamp stamp) {
        return new ActivityEntry(Type.MEAL_PREPARED, subject, xp, day, stamp.stars(), stamp.cookName(),
                amount, stamp.perfect(), stamp.starCap(), stamp.cookId());
    }

    /** Every event other than a preparation carries no quality data. */
    public ActivityEntry(Type type, ResourceLocation subject, long detail, long day) {
        this(type, subject, detail, day, 0, "");
    }

    public ActivityEntry {
        stars = Math.max(0, Math.min(MasteryRank.highestIndex(), stars));
        cookName = cookName == null ? "" : cookName;
        amount = Math.max(1, amount);
        starCap = Math.max(0, Math.min(MasteryRank.highestIndex(), starCap));
    }

    public enum Type {
        RECIPE_DISCOVERED("recipe_discovered"),
        MEAL_PREPARED("meal_prepared"),
        MASTERY_RANK("mastery_rank"),
        MEAL_MASTERED("meal_mastered"),
        LEVEL_UP("level_up"),
        METHOD_DISCOVERED("method_discovered"),
        INGREDIENT_DISCOVERED("ingredient_discovered"),
        CHALLENGE_COMPLETED("challenge_completed"),
        MILESTONE_REACHED("milestone_reached"),
        COLLECTION_COMPLETED("collection_completed");

        private static final Type[] VALUES = values();
        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "cuisinejournal.activity." + id;
        }

        public static Type byId(String id) {
            for (Type type : VALUES) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return MEAL_PREPARED;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.id());
        NbtUtil.putIfPresent(tag, "subject", subject);
        NbtUtil.putIfNonZero(tag, "detail", detail);
        tag.putLong("day", day);
        tag.putLong("amount", amount);
        tag.putBoolean("perfect", perfect);
        tag.putInt("starCap", starCap);
        if (cookId != null) tag.putUUID("cookId", cookId);
        if (stars > 0) {
            tag.putInt("stars", stars);
        }
        if (!cookName.isEmpty()) {
            tag.putString("cook", cookName);
        }
        return tag;
    }

    public static ActivityEntry load(CompoundTag tag) {
        return new ActivityEntry(
                Type.byId(tag.getString("type")),
                NbtUtil.readId(tag, "subject"),
                tag.getLong("detail"),
                tag.getLong("day"),
                tag.contains("stars") ? tag.getInt("stars") : 0,
                tag.contains("cook") ? tag.getString("cook") : "",
                tag.contains("amount") ? tag.getLong("amount") : 1,
                tag.getBoolean("perfect"),
                tag.contains("starCap") ? tag.getInt("starCap") : MasteryRank.highestIndex(),
                tag.hasUUID("cookId") ? tag.getUUID("cookId") : null);
    }
}
