package com.chi__you.cuisinejournal.culinary;

import com.chi__you.cuisinejournal.mastery.MasteryRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Quality data carried by a cooked stack: who made it, how good they were, and
 * whether that particular attempt came out perfect.
 *
 * <p>Owns only the quality compound. Actual ingredient snapshots and pending
 * production have separate namespaced keys and independent owners.</p>
 *
 * <p>Stored under our own key in the stack's tag, so an unstamped stack is
 * byte-identical to vanilla food. Stamped and unstamped stacks do not merge,
 * which is what keeps a batch of good food from being diluted by a batch of
 * bad.</p>
 *
 * @param stars   the cook's rank with this dish, 0..{@link MasteryRank#highestIndex()}
 * @param perfect whether this attempt rolled a perfect result
 * @param cookId  who cooked it, so they can be credited when it is eaten
 * @param cookName their name at the time, for the tooltip
 */
public record MealStamp(int stars, boolean perfect, int starCap, UUID cookId, String cookName) {

    public static final String ROOT = "cuisinejournal:quality";
    private static final String STARS = "stars";
    private static final String PERFECT = "perfect";
    private static final String STAR_CAP = "starCap";
    private static final String COOK_ID = "cook";
    private static final String COOK_NAME = "cookName";

    public MealStamp {
        stars = Math.max(0, Math.min(MasteryRank.highestIndex(), stars));
        starCap = Math.max(0, Math.min(MasteryRank.highestIndex(), starCap));
        cookName = cookName == null ? "" : cookName;
    }

    /** Effective stars including the perfect bonus, capped at the maximum. */
    public int effectiveStars() {
        return Math.min(starCap, perfect ? stars + 1 : stars);
    }

    public boolean isMeaningful() {
        // A recorded cook name alone still makes the stamp worth showing: a zero-star
        // dish from the journal's history has no cook id, but the player asked to see
        // who made it. The only other consumer, villager trade bonuses, pays zero at
        // zero stars either way.
        return stars > 0 || perfect || cookId != null || !cookName.isEmpty();
    }

    public static MealStamp read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(ROOT, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = root.getCompound(ROOT);
        UUID cookId = tag.hasUUID(COOK_ID) ? tag.getUUID(COOK_ID) : null;
        int cap = tag.contains(STAR_CAP) ? tag.getInt(STAR_CAP) : MasteryRank.highestIndex();
        return new MealStamp(tag.getInt(STARS), tag.getBoolean(PERFECT), cap, cookId,
                tag.getString(COOK_NAME));
    }

    public static boolean isStamped(ItemStack stack) {
        return read(stack) != null;
    }

    public void write(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(STARS, stars);
        tag.putInt(STAR_CAP, starCap);
        if (perfect) {
            tag.putBoolean(PERFECT, true);
        }
        if (cookId != null) {
            tag.putUUID(COOK_ID, cookId);
        }
        if (!cookName.isEmpty()) {
            tag.putString(COOK_NAME, cookName);
        }
        stack.getOrCreateTag().put(ROOT, tag);
    }
}
