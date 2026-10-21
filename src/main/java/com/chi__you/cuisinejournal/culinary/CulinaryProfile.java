package com.chi__you.cuisinejournal.culinary;

import com.chi__you.cuisinejournal.Constants;
import com.chi__you.cuisinejournal.challenge.ChallengeState;
import com.chi__you.cuisinejournal.data.NbtUtil;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.mastery.CategoryMastery;
import com.chi__you.cuisinejournal.recipe.CookingMethod;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistries;
import com.chi__you.cuisinejournal.recipe.CulinaryRegistry;
import com.chi__you.cuisinejournal.recipe.MealEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's entire culinary history.
 *
 * <p>This is the aggregate root: it owns the per-dish records, the ingredient
 * and method journals, lifetime statistics, records and streaks, and it is the
 * only thing persisted per player. It is <em>server-authoritative</em> — the
 * client receives projections of it and never sends one back.</p>
 *
 * <p>Unrecognised top-level sections are round-tripped untouched so a profile
 * written by a newer build survives a downgrade instead of being silently
 * truncated.</p>
 */
public final class CulinaryProfile {
    /** Recent Activity is capped; the cap itself is client configuration. */
    public static final int MAX_ACTIVITY_ENTRIES = 64;

    private static final Set<String> KNOWN_SECTIONS = Set.of(
            "schemaVersion", "cookingXp", "meals", "ingredients", "methods",
            "stats", "records", "streaks", "activity", "pinned",
            "challenges", "badges", "milestones", "collections", "signature",
            "categoryAnnouncements", "experimentation", "socialRewards", "selectedBadge", "skillBonuses", "skillsActive", "recipeTracks", "signatureChosen", "signatureChangedDay", "skillMode");

    private final UUID playerId;

    private int schemaVersion = Constants.PROFILE_SCHEMA_VERSION;
    private long cookingXp;
    private CompoundTag skillBonuses = new CompoundTag();
    private boolean skillsActive;
    private String skillMode="valheim";
    public String skillMode(){return skillMode;}
    public void setSkillMode(String value){if(!skillMode.equals(value)){skillMode=value;markDirty();}}
    public CompoundTag skillBonuses() { return skillBonuses; }
    public boolean skillsActive() { return skillsActive; }
    public void setSkillBonuses(CompoundTag value) { skillBonuses=value.copy();markDirty(); }
    public void setSkillsActive(boolean value) { if(skillsActive!=value){skillsActive=value;markDirty();} }
    private long experimentationDay = Long.MIN_VALUE;
    private int experimentationXp;
    private SocialRewards socialRewards = new SocialRewards();

    private final Map<ResourceLocation, MealRecord> meals = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> categoryAnnouncements = new LinkedHashMap<>();
    private final Map<ResourceLocation, IngredientRecord> ingredients = new LinkedHashMap<>();
    private final Map<CookingMethod, MethodRecord> methods = new LinkedHashMap<>();

    private CulinaryStats stats = new CulinaryStats();
    private PersonalRecords records = new PersonalRecords();
    private StreakState streaks = new StreakState();

    private final Deque<ActivityEntry> activity = new ArrayDeque<>();
    /** Ordered because the HUD tracker renders pins in the order they were added. */
    private final List<ResourceLocation> pinned = new ArrayList<>();
    private ResourceLocation selectedBadge;
    private ResourceLocation signatureDish;
    private boolean signatureChosen;
    private long signatureChangedDay=Long.MIN_VALUE;
    public record RecipeTrack(ResourceLocation recipe,int target) {}
    private final Map<ResourceLocation,RecipeTrack> recipeTracks=new LinkedHashMap<>();
    public Map<ResourceLocation,RecipeTrack> recipeTracks(){return Collections.unmodifiableMap(recipeTracks);}
    /**
     * Tracks {@code recipe} for {@code dish} at {@code count} portions, or stops tracking when
     * {@code recipe} is null. A dish already tracked may switch recipe or change count freely; a
     * new dish is refused (returns false, nothing changes) once {@code limit} dishes are tracked —
     * nothing older is pushed out. Lowering the limit keeps existing tracks.
     */
    public boolean trackRecipe(ResourceLocation dish,ResourceLocation recipe,int count,int limit){
        if(recipe==null){recipeTracks.remove(dish);pinned.remove(dish);meal(dish).setPinned(false);markDirty();return true;}
        if(!recipeTracks.containsKey(dish)&&recipeTracks.size()>=Math.max(1,limit))return false;
        recipeTracks.put(dish,new RecipeTrack(recipe,count));if(!pinned.contains(dish))pinned.add(dish);meal(dish).setPinned(true);
        markDirty();return true;
    }
    public boolean signatureChosen(){return signatureChosen;}
    public long signatureChangedDay(){return signatureChangedDay;}
    public void selectSignature(ResourceLocation dish,long day){signatureDish=dish;signatureChosen=true;signatureChangedDay=day;markDirty();}

    private final Map<ResourceLocation, ChallengeState> challenges = new LinkedHashMap<>();
    private ListTag rewardDeliveries=new ListTag();
    public ListTag rewardDeliveries(){return rewardDeliveries;}
    private final Set<ResourceLocation> badges = new LinkedHashSet<>();
    private final Set<ResourceLocation> milestones = new LinkedHashSet<>();
    private final Set<ResourceLocation> completedCollections = new LinkedHashSet<>();

    private final CompoundTag preservedSections = new CompoundTag();

    private transient boolean dirty;

    public CulinaryProfile(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public void clearDirty() {
        dirty = false;
    }

    // ------------------------------------------------------------ progression

    public long cookingXp() {
        return cookingXp;
    }

    public int cookingLevel(LevelCurve curve) {
        return curve.levelForXp(cookingXp);
    }

    /**
     * @return the level after the grant, so callers can detect a level-up by
     *         comparing against the level they read before calling
     */
    public int addCookingXp(long amount, LevelCurve curve) {
        if (amount > 0L) {
            cookingXp = cookingXp > Long.MAX_VALUE-amount ? Long.MAX_VALUE : cookingXp+amount;
            markDirty();
        }
        return curve.levelForXp(cookingXp);
    }

    public void setCookingXp(long amount) {
        cookingXp = Math.max(0L, amount);
        markDirty();
    }

    public int experimentationXpOn(long day) {
        return experimentationDay == day ? experimentationXp : 0;
    }

    public SocialRewards.Award claimSocialReward(long day, ResourceLocation dish,
            com.chi__you.cuisinejournal.config.ServerConfig config) {
        SocialRewards.Award award = socialRewards.claim(day, CulinaryRegistries.current().preparationTarget(dish), config);
        markDirty();
        return award;
    }

    public void recordExperimentationXp(long day, int amount) {
        if (amount <= 0) return;
        if (experimentationDay != day) { experimentationDay = day; experimentationXp = 0; }
        experimentationXp = (int) Math.min(Integer.MAX_VALUE, (long) experimentationXp + amount);
        markDirty();
    }

    public boolean claimFirstMeal(ResourceLocation meal) {
        var registry = CulinaryRegistries.current();
        ResourceLocation source = registry.preparationTarget(meal);
        // Old saves store consumption on portions; any previously eaten linked form blocks the reward.
        for (var entry : meals.entrySet()) if (registry.preparationTarget(entry.getKey()).equals(source)
                && entry.getValue().eaten() > 0) return false;
        boolean claimed = meal(source).claimFirstMeal();
        if (claimed) markDirty();
        return claimed;
    }

    // ---------------------------------------------------------------- meals

    /** Returns the existing record without creating one; may be {@code null}. */
    public MealRecord peekMeal(ResourceLocation target) {
        return meals.get(target);
    }

    public MealRecord preparationRecord(ResourceLocation target) {
        return meals.get(CulinaryRegistries.current().preparationTarget(target));
    }

    public MealRecord meal(ResourceLocation target) {
        return meals.computeIfAbsent(target, MealRecord::new);
    }

    public Map<ResourceLocation, MealRecord> meals() {
        return Collections.unmodifiableMap(meals);
    }

    public boolean isDiscovered(ResourceLocation target) {
        MealRecord record = meals.get(target);
        ResourceLocation source=CulinaryRegistries.current().preparationTarget(target);
        MealRecord prepared=meals.get(source);
        return record != null && record.isDiscovered() || prepared != null && prepared.isDiscovered();
    }

    public int discoveredCount() {
        if(CulinaryRegistries.current().size()>0)
            return (int)CulinaryRegistries.current().targets().stream().filter(this::isDiscovered).count();
        int count = 0;
        for (MealRecord record : meals.values()) {
            if (record.isDiscovered()) {
                count++;
            }
        }
        return count;
    }

    public int masteredCount(MasteryCurve curve) {
        if(CulinaryRegistries.current().size()>0)
            return (int)CulinaryRegistries.current().targets().stream().filter(id->rankOf(id,curve).isMastered()).count();
        int count = 0;
        for (ResourceLocation target : meals.keySet()) {
            if (rankOf(target, curve).isMastered()) {
                count++;
            }
        }
        return count;
    }

    public boolean claimCategoryRankAnnouncement(ResourceLocation category, MasteryRank rank) {
        int previous = categoryAnnouncements.getOrDefault(category, 0);
        if (rank.ordinal() <= previous) {
            return false;
        }
        categoryAnnouncements.put(category, rank.ordinal());
        markDirty();
        return true;
    }

    public int uniquePreparedCount() {
        int count = 0;
        for (MealRecord record : meals.values()) {
            if (record.hasEverBeenPrepared()) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------- ingredients

    public IngredientRecord ingredient(ResourceLocation item) {
        return ingredients.computeIfAbsent(item, IngredientRecord::new);
    }

    public IngredientRecord peekIngredient(ResourceLocation item) {
        return ingredients.get(item);
    }

    public void markIngredientKnown(ResourceLocation item) {
        IngredientRecord record = ingredient(item);
        if (!record.known()) {
            record.markKnown();
            markDirty();
        }
    }

    public Map<ResourceLocation, IngredientRecord> ingredients() {
        return Collections.unmodifiableMap(ingredients);
    }

    public int ingredientsDiscoveredCount() {
        return ingredients.size();
    }

    public int journalIngredientsDiscoveredCount() {
        var registry = CulinaryRegistries.current();
        return (int) registry.listedIngredients().stream().filter(item ->
                com.chi__you.cuisinejournal.recipe.IngredientProgress.collect(
                        registry.ingredientSources(item), this::peekIngredient).known()).count();
    }

    public long journalIngredientUses() {
        var registry = CulinaryRegistries.current();
        java.util.Set<ResourceLocation> sources = new java.util.HashSet<>();
        registry.listedIngredients().forEach(item -> sources.addAll(registry.ingredientSources(item)));
        return com.chi__you.cuisinejournal.recipe.IngredientProgress.collect(sources, this::peekIngredient).timesUsed();
    }

    // -------------------------------------------------------------- methods

    public MethodRecord method(CookingMethod method) {
        return methods.computeIfAbsent(method, MethodRecord::new);
    }

    public MethodRecord peekMethod(CookingMethod method) {
        return methods.get(method);
    }

    public Map<CookingMethod, MethodRecord> methods() {
        return Collections.unmodifiableMap(methods);
    }

    // ------------------------------------------------------- stats & records

    public CulinaryStats stats() {
        return stats;
    }

    public PersonalRecords records() {
        return records;
    }

    public StreakState streaks() {
        return streaks;
    }

    // ------------------------------------------------------------- activity

    public void pushActivity(ActivityEntry entry) {
        activity.addFirst(entry);
        int capacity = Math.max(1, Math.min(MAX_ACTIVITY_ENTRIES,
                com.chi__you.cuisinejournal.config.ConfigManager.server().advanced.activityHistorySize));
        while (activity.size() > capacity) {
            activity.removeLast();
        }
        markDirty();
    }

    /** Most recent first. */
    public List<ActivityEntry> activity() {
        return List.copyOf(activity);
    }

    // ------------------------------------------------- favourites and pins

    public List<ResourceLocation> favorites() {
        List<ResourceLocation> result = new ArrayList<>();
        meals.forEach((id, record) -> {
            if (record.isFavorite()) {
                result.add(id);
            }
        });
        return result;
    }

    public boolean toggleFavorite(ResourceLocation target) {
        MealRecord record = meal(target);
        record.setFavorite(!record.isFavorite());
        markDirty();
        return record.isFavorite();
    }

    public List<ResourceLocation> pinned() {
        return Collections.unmodifiableList(pinned);
    }

    /**
     * @param limit maximum simultaneous pins allowed by client configuration
     * @return {@code true} if the recipe is pinned afterwards
     */
    public boolean togglePin(ResourceLocation target, int limit) {
        MealRecord record = meal(target);
        markDirty();
        if (pinned.remove(target)) {
            recipeTracks.remove(target);
            record.setPinned(false);
            return false;
        }
        pinned.add(target);
        record.setPinned(true);
        while (pinned.size() > Math.max(1, limit)) {
            ResourceLocation dropped = pinned.remove(0);
            MealRecord droppedRecord = meals.get(dropped);
            if (droppedRecord != null) {
                droppedRecord.setPinned(false);
            }
            recipeTracks.remove(dropped);
        }
        return true;
    }

    public ResourceLocation selectedBadge() {
        return selectedBadge;
    }

    /** The selected signature identity; bonus and replacement policy live in SignatureDishService. */
    public ResourceLocation signatureDish() {
        return signatureDish;
    }

    public void setSignatureDish(ResourceLocation dish) {
        this.signatureDish = dish;
        markDirty();
    }

    public boolean selectBadge(ResourceLocation badge){
        if(badge!=null&&!badges.contains(badge))return false;
        selectedBadge = badge;
        markDirty();
        return true;
    }

    // --------------------------------------- challenges, badges, milestones

    public ChallengeState challenge(ResourceLocation id, int objectiveCount) {
        var state = challenges.computeIfAbsent(id, unused -> new ChallengeState(objectiveCount));
        state.ensureObjectives(objectiveCount); return state;
    }

    public ChallengeState peekChallenge(ResourceLocation id) {
        return challenges.get(id);
    }

    public Map<ResourceLocation, ChallengeState> challenges() {
        return Collections.unmodifiableMap(challenges);
    }

    public boolean hasCompletedChallenge(ResourceLocation id) {
        ChallengeState state = challenges.get(id);
        return state != null && state.isCompleted();
    }

    /** Drops a rotating challenge's progress when its period rolls over. */
    public void clearChallenge(ResourceLocation id) {
        challenges.remove(id);
        markDirty();
    }

    public Set<ResourceLocation> badges() {
        return Collections.unmodifiableSet(badges);
    }

    /** @return {@code true} when the badge is newly earned */
    public boolean awardBadge(ResourceLocation badge) {
        if (badge == null || !badges.add(badge)) {
            return false;
        }
        markDirty();
        return true;
    }

    public Set<ResourceLocation> milestones() {
        return Collections.unmodifiableSet(milestones);
    }

    public boolean reachMilestone(ResourceLocation milestone) {
        if (milestone == null || !milestones.add(milestone)) {
            return false;
        }
        markDirty();
        return true;
    }

    public Set<ResourceLocation> completedCollections() {
        return Collections.unmodifiableSet(completedCollections);
    }

    public boolean completeCollection(ResourceLocation collection) {
        if (collection == null || !completedCollections.add(collection)) {
            return false;
        }
        markDirty();
        return true;
    }

    // ------------------------------------------------------- orphaned data

    /**
     * Records whose dish no longer exists in the current registry.
     *
     * <p>They stay on disk untouched so removing an addon for one session does
     * not destroy the progress it earned; they are simply hidden from the
     * journal until the addon returns.</p>
     */
    public List<ResourceLocation> orphanedTargets(Collection<ResourceLocation> knownTargets) {
        List<ResourceLocation> orphans = new ArrayList<>();
        for (ResourceLocation id : meals.keySet()) {
            if (!knownTargets.contains(id)) {
                orphans.add(id);
            }
        }
        return orphans;
    }

    public int purgeOrphans(Collection<ResourceLocation> knownTargets) {
        List<ResourceLocation> orphans = orphanedTargets(knownTargets);
        for (ResourceLocation id : orphans) {
            meals.remove(id);
            pinned.remove(id);
            recipeTracks.remove(id);
        }
        if (!orphans.isEmpty()) {
            markDirty();
        }
        return orphans.size();
    }

    // ---------------------------------------------------------------- resets

    public enum ResetScope {
        LEVEL, DISCOVERY, STATISTICS, ACTIVITY, CHALLENGES, EVERYTHING
    }

    public void reset(ResetScope scope) {
        switch (scope) {
            case LEVEL -> cookingXp = 0L;
            case DISCOVERY -> meals.values().forEach(record -> {
                CompoundTag saved = record.save();
                saved.remove("discovered");
                saved.remove("discoveredDay");
                meals.put(record.target(), MealRecord.load(record.target(), saved));
            });
            case STATISTICS -> {
                stats.reset();
                records = new PersonalRecords();
                streaks = new StreakState();
            }
            case ACTIVITY -> activity.clear();
            case CHALLENGES -> {
                challenges.clear();
                rewardDeliveries.clear();
            }
            case EVERYTHING -> {
                cookingXp = 0L;
                experimentationDay = Long.MIN_VALUE;
                experimentationXp = 0;
                socialRewards = new SocialRewards();
                meals.clear();
                categoryAnnouncements.clear();
                ingredients.clear();
                methods.clear();
                stats = new CulinaryStats();
                records = new PersonalRecords();
                streaks = new StreakState();
                activity.clear();
                pinned.clear();
                selectedBadge = null;
                signatureDish = null;
                signatureChosen=false;signatureChangedDay=Long.MIN_VALUE;recipeTracks.clear();
                challenges.clear();
                badges.clear();
                milestones.clear();
                completedCollections.clear();
                rewardDeliveries.clear();
            }
        }
        markDirty();
    }

    // --------------------------------------------------------- serialisation

    public CompoundTag save() {
        CompoundTag tag = preservedSections.copy();
        tag.put("rewardDeliveries",rewardDeliveries.copy());
        tag.putInt("schemaVersion", Constants.PROFILE_SCHEMA_VERSION);
        NbtUtil.putIfNonZero(tag, "cookingXp", cookingXp);
        tag.put("skillBonuses",skillBonuses.copy());tag.putBoolean("skillsActive",skillsActive);
        tag.putString("skillMode",skillMode);
        CompoundTag experimentation = new CompoundTag();
        experimentation.putLong("day", experimentationDay);
        experimentation.putInt("xp", experimentationXp);
        tag.put("experimentation", experimentation);
        tag.put("socialRewards", socialRewards.save());
        tag.put("meals", NbtUtil.writeMap(meals, MealRecord::save));
        CompoundTag announced = new CompoundTag();
        categoryAnnouncements.forEach((id, rank) -> announced.putInt(id.toString(), rank));
        tag.put("categoryAnnouncements", announced);
        tag.put("ingredients", NbtUtil.writeMap(ingredients, IngredientRecord::save));

        CompoundTag methodTag = new CompoundTag();
        methods.forEach((method, record) -> {
            CompoundTag entry = record.save();
            if (!entry.isEmpty()) {
                methodTag.put(method.id().toString(), entry);
            }
        });
        tag.put("methods", methodTag);

        tag.put("stats", stats.save());
        tag.put("records", records.save());
        tag.put("streaks", streaks.save());

        if (!activity.isEmpty()) {
            ListTag activityTag = new ListTag();
            activity.forEach(entry -> activityTag.add(entry.save()));
            tag.put("activity", activityTag);
        }
        if (!pinned.isEmpty()) {
            tag.put("pinned", NbtUtil.writeIds(pinned));
        }
        NbtUtil.putIfPresent(tag, "selectedBadge", selectedBadge);
        NbtUtil.putIfPresent(tag, "signature", signatureDish);
        tag.putBoolean("signatureChosen",signatureChosen);tag.putLong("signatureChangedDay",signatureChangedDay);
        CompoundTag tracks=new CompoundTag();recipeTracks.forEach((dish,track)->{var row=new CompoundTag();row.putString("recipe",track.recipe().toString());row.putInt("target",track.target());tracks.put(dish.toString(),row);});tag.put("recipeTracks",tracks);

        if (!challenges.isEmpty()) {
            tag.put("challenges", NbtUtil.writeMap(challenges, ChallengeState::save));
        }
        if (!badges.isEmpty()) {
            tag.put("badges", NbtUtil.writeIds(badges));
        }
        if (!milestones.isEmpty()) {
            tag.put("milestones", NbtUtil.writeIds(milestones));
        }
        if (!completedCollections.isEmpty()) {
            tag.put("collections", NbtUtil.writeIds(completedCollections));
        }
        return tag;
    }

    public static CulinaryProfile load(UUID playerId, CompoundTag tag) {
        CulinaryProfile profile = new CulinaryProfile(playerId);
        profile.rewardDeliveries=tag.getList("rewardDeliveries",Tag.TAG_COMPOUND).copy();
        profile.schemaVersion = tag.contains("schemaVersion")
                ? tag.getInt("schemaVersion") : Constants.PROFILE_SCHEMA_VERSION;
        profile.cookingXp = tag.getLong("cookingXp");
        profile.skillBonuses=tag.getCompound("skillBonuses").copy();profile.skillsActive=tag.getBoolean("skillsActive");
        profile.skillMode=tag.contains("skillMode")?tag.getString("skillMode"):"valheim";
        profile.socialRewards = SocialRewards.load(tag.getCompound("socialRewards"));
        if (tag.contains("experimentation", Tag.TAG_COMPOUND)) {
            profile.experimentationDay = tag.getCompound("experimentation").getLong("day");
            profile.experimentationXp = Math.max(0, tag.getCompound("experimentation").getInt("xp"));
        }

        NbtUtil.readMap(tag, "meals", profile.meals, MealRecord::load);
        var storedMeals=tag.getCompound("meals");
        for(String key:storedMeals.getAllKeys()){
            var meal=storedMeals.getCompound(key);
            if(!meal.getBoolean("masteryTracked")&&meal.getLong("prepared")>0)profile.markDirty();
        }
        CompoundTag announced = tag.getCompound("categoryAnnouncements");
        for (String key : announced.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) {
                profile.categoryAnnouncements.put(id, announced.getInt(key));
            }
        }
        NbtUtil.readMap(tag, "ingredients", profile.ingredients, IngredientRecord::load);

        if (tag.contains("methods", Tag.TAG_COMPOUND)) {
            CompoundTag methodTag = tag.getCompound("methods");
            for (String rawId : methodTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(rawId);
                if (id == null) {
                    continue;
                }
                CookingMethod method = CookingMethod.of(id);
                profile.methods.put(method, MethodRecord.load(method, methodTag.getCompound(rawId)));
            }
        }

        profile.stats = CulinaryStats.load(tag.getCompound("stats"));
        profile.records = PersonalRecords.load(tag.getCompound("records"));
        profile.streaks = StreakState.load(tag.getCompound("streaks"));

        if (tag.contains("activity", Tag.TAG_LIST)) {
            ListTag activityTag = tag.getList("activity", Tag.TAG_COMPOUND);
            for (int i = 0; i < activityTag.size() && i < MAX_ACTIVITY_ENTRIES; i++) {
                profile.activity.addLast(ActivityEntry.load(activityTag.getCompound(i)));
            }
        }
        NbtUtil.forEachId(tag, "pinned", profile.pinned::add);
        ResourceLocation selectedBadge = NbtUtil.readId(tag, "selectedBadge");
        profile.signatureDish = NbtUtil.readId(tag, "signature");
        profile.signatureChosen=tag.getBoolean("signatureChosen")||profile.signatureDish!=null;
        profile.signatureChangedDay=tag.contains("signatureChangedDay")?tag.getLong("signatureChangedDay"):Long.MIN_VALUE;
        var tracks=tag.getCompound("recipeTracks");for(String key:tracks.getAllKeys()){
            var dish=ResourceLocation.tryParse(key);var row=tracks.getCompound(key);var recipe=ResourceLocation.tryParse(row.getString("recipe"));
            int count=row.getInt("target");if(dish!=null&&recipe!=null&&count>=1&&count<=4096)profile.recipeTracks.put(dish,new RecipeTrack(recipe,count));
        }

        NbtUtil.readMap(tag, "challenges", profile.challenges,
                (id, entry) -> ChallengeState.load(entry, 0));
        NbtUtil.forEachId(tag, "badges", profile.badges::add);
        profile.selectedBadge = profile.badges.contains(selectedBadge) ? selectedBadge : null;
        NbtUtil.forEachId(tag, "milestones", profile.milestones::add);
        NbtUtil.forEachId(tag, "collections", profile.completedCollections::add);

        for (String key : tag.getAllKeys()) {
            if (!KNOWN_SECTIONS.contains(key) && !key.equals("rewardDeliveries")) {
                profile.preservedSections.put(key, tag.get(key).copy());
            }
        }
        return profile;
    }

    /** Convenience for the journal header and the {@code /cj stats} output. */
    public MasteryRank rankOf(ResourceLocation target, MasteryCurve curve) {
        if (!com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.enabled) return MasteryRank.values()[2];
        target=com.chi__you.cuisinejournal.recipe.DishServingVariants.target(target);
        MealRecord record = meals.get(CulinaryRegistries.current().preparationTarget(target));
        if (!isDiscovered(target)) {
            return MasteryRank.UNFAMILIAR;
        }
        if(record!=null&&record.highestBrewedStars()>=0)
            return MasteryRank.byIndex(record.highestBrewedStars());
        // Per-dish scope: the dish's own mastery points (original Meal Mastery behaviour).
        if (com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.perDish()) {
            return MasteryRank.byIndex(Math.min(curve.rankFor(masteryPointsOf(target)).ordinal(),
                    com.chi__you.cuisinejournal.skills.CookingSkills.starCap(this)));
        }
        CulinaryRegistry registry = CulinaryRegistries.current();
        MealEntry entry = registry.progressEntry(target);
        if (entry == null) {
            return MasteryRank.UNFAMILIAR;
        }
        ResourceLocation category = entry.masteryGroup();
        return categoryRank(category, curve);
    }

    /** Points behind {@link #rankOf}: the dish's own points, or its subcategory's preparations. */
    public long masteryPointsOf(ResourceLocation target) {
        target=com.chi__you.cuisinejournal.recipe.DishServingVariants.target(target);
        MealRecord record = meals.get(CulinaryRegistries.current().preparationTarget(target));
        if (com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.perDish()) {
            return com.chi__you.cuisinejournal.recipe.LatiaoMastery.points(target,id->{
                MealRecord member=meals.get(CulinaryRegistries.current().preparationTarget(id));
                return member==null?0L:member.masteryPoints();
            });
        }
        MealEntry entry = CulinaryRegistries.current().progressEntry(target);
        return entry == null ? 0L : categoryMasteryPoints(entry.masteryGroup(), CulinaryRegistries.current());
    }

    public MasteryRank categoryRank(ResourceLocation category, MasteryCurve curve) {
        if (!com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.enabled) return MasteryRank.values()[2];
        CulinaryRegistry registry = CulinaryRegistries.current();
        long points = categoryMasteryPoints(category, registry);
        int discovered = 0;
        for (MealEntry member : registry.bySubcategory(category)) {
            if (isDiscovered(member.target())) {
                discovered++;
            }
        }
        return MasteryRank.byIndex(Math.min(CategoryMastery.rank(points, discovered,
                registry.bySubcategory(category).size(), curve).ordinal(),com.chi__you.cuisinejournal.skills.CookingSkills.starCap(this)));
    }

    public long categoryMasteryPoints(ResourceLocation category, CulinaryRegistry registry) {
        long points = 0L;
        java.util.Set<ResourceLocation> counted=new java.util.HashSet<>();
        for (MealEntry member : registry.bySubcategory(category)) {
            ResourceLocation source=registry.preparationTarget(member.target());
            if(!counted.add(source))continue;
            MealRecord record = meals.get(source);
            if (record != null) {
                points += record.prepared() + record.bonusMasteryPoints();
            }
        }
        return points;
    }

    public int categoryRankCap(ResourceLocation target) {
        CulinaryRegistry registry = CulinaryRegistries.current();
        MealEntry entry = registry.progressEntry(target);
        if (entry == null) {
            return 0;
        }
        // A single dish has no "discover more" to unlock; once discovered it can reach five stars.
        if (com.chi__you.cuisinejournal.config.ConfigManager.server().mastery.perDish()) {
            return isDiscovered(target) ? com.chi__you.cuisinejournal.skills.CookingSkills.starCap(this) : 0;
        }
        int discovered = 0;
        List<MealEntry> members = registry.bySubcategory(entry.masteryGroup());
        for (MealEntry member : members) {
            if (isDiscovered(member.target())) {
                discovered++;
            }
        }
        return Math.min(CategoryMastery.rankCap(discovered, members.size()),com.chi__you.cuisinejournal.skills.CookingSkills.starCap(this));
    }
}
