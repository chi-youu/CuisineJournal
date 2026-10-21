package com.chi__you.cuisinejournal.culinary;

import com.chi__you.cuisinejournal.data.NbtUtil;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything a player has ever done with one dish, in one place.
 *
 * <p>Discovery, mastery, preparation counts, consumption and per-method usage
 * all live in a single record keyed by the mastery target so its identifier is
 * written to disk exactly once instead of once per subsystem.</p>
 *
 * <p>Mastery is tracked in <em>points</em>, not preparations, so the optional
 * quality contributions (serving, eating, using the intended
 * workstation) can feed the same track without inventing a second currency.</p>
 */
public final class MealRecord {
    private static final long NO_DAY = Long.MIN_VALUE;

    private final ResourceLocation target;

    private boolean discovered;
    private long discoveredDay = NO_DAY;

    private long prepared;
    private long eaten;
    private long served;

    private long masteryPoints;
    private long bonusMasteryPoints;
    private boolean firstMealClaimed;
    /** Highest rank actually announced to the player; keeps notifications from repeating. */
    private int announcedRank;
    private int highestBrewedStars=-1;
    public int highestBrewedStars(){return highestBrewedStars;}
    public void recordBrewedStars(int stars){highestBrewedStars=Math.max(highestBrewedStars,Math.max(0,Math.min(5,stars)));}

    private long firstPreparedDay = NO_DAY;
    private long lastPreparedDay = NO_DAY;
    private long firstEatenDay = NO_DAY;
    private long lastEatenDay = NO_DAY;

    private long currentDay = NO_DAY;
    private int preparedOnCurrentDay;
    private int eatenOnCurrentDay;
    private int bestPreparedInOneDay;
    private int bestEatenInOneDay;

    private boolean favorite;
    private boolean pinned;

    private final Map<CookingMethod, Long> preparationsByMethod = new LinkedHashMap<>();
    private final Set<ResourceLocation> ingredientVariantsUsed = new LinkedHashSet<>();

    public MealRecord(ResourceLocation target) {
        this.target = target;
    }

    public ResourceLocation target() {
        return target;
    }

    // ---------------------------------------------------------------- state

    public boolean isDiscovered() {
        return discovered;
    }

    public long discoveredDay() {
        return discoveredDay;
    }

    /** @return {@code true} the first time this dish is discovered, {@code false} afterwards. */
    public boolean discover(long day) {
        if (discovered) {
            return false;
        }
        discovered = true;
        discoveredDay = day;
        return true;
    }

    public long prepared() {
        return prepared;
    }

    public long eaten() {
        return eaten;
    }

    public long served() {
        return served;
    }

    public long masteryPoints() {
        return masteryPoints;
    }

    public long bonusMasteryPoints() { return bonusMasteryPoints; }

    public void addBonusMasteryPoints(long points) {
        if (points <= 0) return;
        bonusMasteryPoints = Math.min(Long.MAX_VALUE - points, bonusMasteryPoints) + points;
        addMasteryPoints(points);
    }

    public boolean claimFirstMeal() {
        if (firstMealClaimed || eaten > 0) return false;
        firstMealClaimed = true;
        return true;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public long firstPreparedDay() {
        return hasDay(firstPreparedDay) ? firstPreparedDay : NO_DAY;
    }

    public long lastPreparedDay() {
        return lastPreparedDay;
    }

    public long firstEatenDay() {
        return firstEatenDay;
    }

    public long lastEatenDay() {
        return lastEatenDay;
    }

    public int bestPreparedInOneDay() {
        return bestPreparedInOneDay;
    }

    public int bestEatenInOneDay() {
        return bestEatenInOneDay;
    }

    /** Preparations already made today; drives the anti-farming curve. */
    public int preparedOnDay(long day) {
        return currentDay == day ? preparedOnCurrentDay : 0;
    }

    public Map<CookingMethod, Long> preparationsByMethod() {
        return Collections.unmodifiableMap(preparationsByMethod);
    }

    public Set<ResourceLocation> ingredientVariantsUsed() {
        return Collections.unmodifiableSet(ingredientVariantsUsed);
    }

    public boolean hasEverBeenPrepared() {
        return prepared > 0L;
    }

    // -------------------------------------------------------------- mutation

    public void recordPreparation(long day, int amount, CookingMethod method) {
        if (amount <= 0) {
            return;
        }
        rollDay(day);
        prepared += amount;
        preparedOnCurrentDay += amount;
        bestPreparedInOneDay = Math.max(bestPreparedInOneDay, preparedOnCurrentDay);
        if (!hasDay(firstPreparedDay)) {
            firstPreparedDay = day;
        }
        lastPreparedDay = day;
        if (method != null) {
            preparationsByMethod.merge(method, (long) amount, Long::sum);
        }
    }

    public void recordEaten(long day, int amount) {
        if (amount <= 0) {
            return;
        }
        rollDay(day);
        eaten += amount;
        eatenOnCurrentDay += amount;
        bestEatenInOneDay = Math.max(bestEatenInOneDay, eatenOnCurrentDay);
        if (!hasDay(firstEatenDay)) {
            firstEatenDay = day;
        }
        lastEatenDay = day;
    }

    public void recordServed(long amount) {
        if (amount > 0L) {
            served += amount;
        }
    }

    /** @return {@code true} when this variant had never been used before. */
    public boolean recordIngredientVariant(ResourceLocation ingredient) {
        return ingredient != null && ingredientVariantsUsed.add(ingredient);
    }

    public void addMasteryPoints(long points) {
        if (points > 0L) {
            masteryPoints = Math.min(Long.MAX_VALUE - points, masteryPoints) + points;
        }
    }

    /**
     * Overwrites the mastery total. Only for administrative correction — normal
     * play may only ever add points.
     *
     * <p>Lowering the total also lowers the announced rank, so the player is
     * told about the rank again if they earn it back rather than silently
     * skipping the notification.</p>
     */
    public void setMasteryPoints(long points, MasteryCurve curve) {
        masteryPoints = Math.max(0L, points);
        announcedRank = Math.min(announcedRank, curve.rankFor(masteryPoints).ordinal());
    }

    public MasteryRank rank(MasteryCurve curve) {
        return curve.rankFor(masteryPoints);
    }

    /**
     * Records that {@code rank} has been shown to the player.
     *
     * @return {@code true} when this is a newly reached rank worth announcing
     */
    public boolean claimRankAnnouncement(MasteryRank rank) {
        if (rank.ordinal() <= announcedRank) {
            return false;
        }
        announcedRank = rank.ordinal();
        return true;
    }

    public MasteryRank announcedRank() {
        return MasteryRank.byIndex(announcedRank);
    }

    private void rollDay(long day) {
        if (currentDay != day) {
            currentDay = day;
            preparedOnCurrentDay = 0;
            eatenOnCurrentDay = 0;
        }
    }

    private static boolean hasDay(long day) {
        return day != NO_DAY;
    }

    /** A record with nothing in it is never written, which keeps profiles small. */
    public boolean isEmpty() {
        return !discovered && prepared == 0L && eaten == 0L && served == 0L
                && masteryPoints == 0L && !favorite && !pinned
                && bonusMasteryPoints == 0L && announcedRank == 0 && highestBrewedStars < 0
                && !firstMealClaimed && ingredientVariantsUsed.isEmpty();
    }

    // --------------------------------------------------------- serialisation

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if(highestBrewedStars>=0)tag.putInt("highestBrewedStars",highestBrewedStars);
        if (isEmpty()) {
            return tag;
        }
        NbtUtil.putIfTrue(tag, "discovered", discovered);
        if (hasDay(discoveredDay)) {
            tag.putLong("discoveredDay", discoveredDay);
        }
        NbtUtil.putIfNonZero(tag, "prepared", prepared);
        NbtUtil.putIfNonZero(tag, "eaten", eaten);
        NbtUtil.putIfNonZero(tag, "served", served);
        NbtUtil.putIfNonZero(tag, "masteryPoints", masteryPoints);
        NbtUtil.putIfNonZero(tag, "bonusMasteryPoints", bonusMasteryPoints);
        NbtUtil.putIfTrue(tag, "firstMealClaimed", firstMealClaimed);
        // Marks records whose mastery points are maintained (see load()).
        tag.putBoolean("masteryTracked", true);
        NbtUtil.putIfNonZero(tag, "announcedRank", announcedRank);
        if (hasDay(firstPreparedDay)) {
            tag.putLong("firstPreparedDay", firstPreparedDay);
        }
        if (hasDay(lastPreparedDay)) {
            tag.putLong("lastPreparedDay", lastPreparedDay);
        }
        if (hasDay(firstEatenDay)) {
            tag.putLong("firstEatenDay", firstEatenDay);
        }
        if (hasDay(lastEatenDay)) {
            tag.putLong("lastEatenDay", lastEatenDay);
        }
        if (hasDay(currentDay)) {
            tag.putLong("currentDay", currentDay);
            NbtUtil.putIfNonZero(tag, "preparedToday", preparedOnCurrentDay);
            NbtUtil.putIfNonZero(tag, "eatenToday", eatenOnCurrentDay);
        }
        NbtUtil.putIfNonZero(tag, "bestPreparedDay", bestPreparedInOneDay);
        NbtUtil.putIfNonZero(tag, "bestEatenDay", bestEatenInOneDay);
        NbtUtil.putIfTrue(tag, "favorite", favorite);
        NbtUtil.putIfTrue(tag, "pinned", pinned);

        if (!preparationsByMethod.isEmpty()) {
            CompoundTag methods = new CompoundTag();
            preparationsByMethod.forEach((method, count) ->
                    methods.putLong(method.id().toString(), count));
            tag.put("methods", methods);
        }
        if (!ingredientVariantsUsed.isEmpty()) {
            tag.put("variants", NbtUtil.writeIds(ingredientVariantsUsed));
        }
        return tag;
    }

    public static MealRecord load(ResourceLocation target, CompoundTag tag) {
        MealRecord record = new MealRecord(target);
        record.discovered = tag.getBoolean("discovered");
        record.discoveredDay = tag.contains("discoveredDay") ? tag.getLong("discoveredDay") : NO_DAY;
        record.prepared = tag.getLong("prepared");
        record.eaten = tag.getLong("eaten");
        record.served = tag.getLong("served");
        record.masteryPoints = tag.getLong("masteryPoints");
        record.bonusMasteryPoints = Math.max(0, tag.getLong("bonusMasteryPoints"));
        record.firstMealClaimed = tag.getBoolean("firstMealClaimed") || record.eaten > 0;
        // Saves from before per-dish mastery points were maintained: seed them from the
        // preparation count once, so switching the star scope to DISH keeps earned progress.
        if (!tag.getBoolean("masteryTracked") && record.masteryPoints == 0L && record.prepared > 0L) {
            int points=Math.max(0,com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.pointsPerPreparation);
            record.masteryPoints = points>0&&record.prepared>Long.MAX_VALUE/points?Long.MAX_VALUE:record.prepared*points;
        }
        record.announcedRank = tag.getInt("announcedRank");
        record.highestBrewedStars=tag.contains("highestBrewedStars")?Math.max(0,Math.min(5,tag.getInt("highestBrewedStars"))):-1;
        record.firstPreparedDay = tag.contains("firstPreparedDay") ? tag.getLong("firstPreparedDay") : NO_DAY;
        record.lastPreparedDay = tag.contains("lastPreparedDay") ? tag.getLong("lastPreparedDay") : NO_DAY;
        record.firstEatenDay = tag.contains("firstEatenDay") ? tag.getLong("firstEatenDay") : NO_DAY;
        record.lastEatenDay = tag.contains("lastEatenDay") ? tag.getLong("lastEatenDay") : NO_DAY;
        record.currentDay = tag.contains("currentDay") ? tag.getLong("currentDay") : NO_DAY;
        record.preparedOnCurrentDay = tag.getInt("preparedToday");
        record.eatenOnCurrentDay = tag.getInt("eatenToday");
        record.bestPreparedInOneDay = tag.getInt("bestPreparedDay");
        record.bestEatenInOneDay = tag.getInt("bestEatenDay");
        record.favorite = tag.getBoolean("favorite");
        record.pinned = tag.getBoolean("pinned");

        if (tag.contains("methods", Tag.TAG_COMPOUND)) {
            CompoundTag methods = tag.getCompound("methods");
            for (String rawId : methods.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(rawId);
                if (id != null) {
                    record.preparationsByMethod.put(CookingMethod.of(id), methods.getLong(rawId));
                }
            }
        }
        NbtUtil.forEachId(tag, "variants", record.ingredientVariantsUsed::add);
        return record;
    }
}
