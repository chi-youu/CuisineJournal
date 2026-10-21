package com.chi__you.cuisinejournal.network;

import com.chi__you.cuisinejournal.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Journal packet records and their bounded wire codecs.
 *
 * <p>The split follows the client is given the smallest projection that
 * lets it draw a screen, never a copy of the server's databases. The dish
 * catalogue is sent once in chunks and cached; per-dish detail is pulled on
 * demand; profile updates after the initial sync are deltas.</p>
 *
 * <p>Client requests cover queries, preferences and permission-checked edits.
 * Earned progression is server-owned; handlers validate requests before changes.</p>
 */
public final class Packets {

    private Packets() {
    }

    private static ResourceLocation packetId(String path) {
        return new ResourceLocation(Constants.MOD_ID, path);
    }

    // ------------------------------------------------------- server to client

    /** One dish, as the recipe browser needs it. */
    public record DishView(ResourceLocation target, String sourceModId, ResourceLocation category,
                           List<ResourceLocation> methods, int nutrition, float saturation,
                           ResourceLocation subcategory, ResourceLocation dimension,
                           ResourceLocation preparationTarget) {

        public DishView(ResourceLocation target,String mod,ResourceLocation category,List<ResourceLocation> methods,
                        int nutrition,float saturation,ResourceLocation subcategory,ResourceLocation dimension) {
            this(target,mod,category,methods,nutrition,saturation,subcategory,dimension,target);
        }

        /** Compatibility constructor for callers using the pre-subcategory view. */
        public DishView(ResourceLocation target, String sourceModId, ResourceLocation category,
                        List<ResourceLocation> methods, int nutrition, float saturation) {
            this(target, sourceModId, category, methods, nutrition, saturation, category,
                    new ResourceLocation("minecraft", "overworld"));
        }

        public DishView {
            methods = List.copyOf(methods);
            if(preparationTarget==null)preparationTarget=target;
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(target);
            buffer.writeUtf(sourceModId, 64);
            buffer.writeResourceLocation(category);
            buffer.writeVarInt(methods.size());
            methods.forEach(buffer::writeResourceLocation);
            buffer.writeVarInt(nutrition);
            buffer.writeFloat(saturation);
            buffer.writeResourceLocation(subcategory);
            buffer.writeResourceLocation(dimension);
            buffer.writeResourceLocation(preparationTarget);
        }

        static DishView read(FriendlyByteBuf buffer) {
            ResourceLocation target = buffer.readResourceLocation();
            String modId = buffer.readUtf(64);
            ResourceLocation category = buffer.readResourceLocation();
            int methodCount = PacketSafety.count(buffer, 4096);
            List<ResourceLocation> methods = new ArrayList<>(methodCount);
            for (int i = 0; i < methodCount; i++) {
                methods.add(buffer.readResourceLocation());
            }
            return new DishView(target, modId, category, methods,
                    buffer.readVarInt(), buffer.readFloat(), buffer.readResourceLocation(),
                    buffer.readResourceLocation(),buffer.readResourceLocation());
        }
    }

    /**
     * A slice of the dish catalogue.
     *
     * @param first whether the client should discard whatever it had cached;
     *              set on the first chunk of a sync so a datapack reload
     *              replaces the catalogue rather than appending to it
     */
    public record SyncCatalogue(boolean first, boolean last, List<DishView> dishes)
            implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("sync_catalogue");

        public SyncCatalogue {
            dishes = List.copyOf(dishes);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(first);
            buffer.writeBoolean(last);
            buffer.writeVarInt(dishes.size());
            dishes.forEach(dish -> dish.write(buffer));
        }

        public static SyncCatalogue read(FriendlyByteBuf buffer) {
            boolean first = buffer.readBoolean();
            boolean last = buffer.readBoolean();
            int count = PacketSafety.count(buffer, 65_536);
            List<DishView> dishes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                dishes.add(DishView.read(buffer));
            }
            return new SyncCatalogue(first, last, dishes);
        }
    }

    /** One dish's entry in the player's own history. */
    public record RecordView(ResourceLocation target, boolean discovered, long prepared, long eaten,
                             long served, boolean favorite, boolean pinned,
                             long firstPreparedDay, long lastPreparedDay, long masteryPoints, long bonusMasteryPoints, int highestBrewedStars) {
        public RecordView(ResourceLocation target,boolean discovered,long prepared,long eaten,long served,boolean favorite,boolean pinned,
                          long firstPreparedDay,long lastPreparedDay,long masteryPoints,long bonusMasteryPoints){
            this(target,discovered,prepared,eaten,served,favorite,pinned,firstPreparedDay,lastPreparedDay,masteryPoints,bonusMasteryPoints,-1);
        }

        public RecordView(ResourceLocation target, boolean discovered, long prepared, long eaten,
                          long served, boolean favorite, boolean pinned, long firstPreparedDay,
                          long lastPreparedDay, long masteryPoints) {
            this(target, discovered, prepared, eaten, served, favorite, pinned, firstPreparedDay,
                    lastPreparedDay, masteryPoints, 0);
        }

        public RecordView(ResourceLocation target, boolean discovered, long prepared, long eaten,
                          long served, boolean favorite, boolean pinned,
                          long firstPreparedDay, long lastPreparedDay) {
            this(target, discovered, prepared, eaten, served, favorite, pinned,
                    firstPreparedDay, lastPreparedDay, prepared);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(target);
            byte flags = (byte) ((discovered ? 1 : 0) | (favorite ? 2 : 0) | (pinned ? 4 : 0));
            buffer.writeByte(flags);
            buffer.writeVarLong(Math.max(0L, prepared));
            buffer.writeVarLong(Math.max(0L, eaten));
            buffer.writeVarLong(Math.max(0L, served));
            buffer.writeLong(firstPreparedDay);
            buffer.writeLong(lastPreparedDay);
            buffer.writeVarLong(Math.max(0L, masteryPoints));
            buffer.writeVarLong(Math.max(0L, bonusMasteryPoints));
            buffer.writeByte(highestBrewedStars);
        }

        static RecordView read(FriendlyByteBuf buffer) {
            ResourceLocation target = buffer.readResourceLocation();
            byte flags = buffer.readByte();
            return new RecordView(target, (flags & 1) != 0,
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong(),
                    (flags & 2) != 0, (flags & 4) != 0,
                    buffer.readLong(), buffer.readLong(), buffer.readVarLong(), buffer.readVarLong(),buffer.readByte());
        }
    }

    /**
     * The player's own profile.
     *
     * @param full when false this is a delta and {@code records} only contains
     *             what changed
     */
    public record SyncProfile(boolean full, long cookingXp, long mealsPrepared, long mealsEaten,
                              long portionsServed, int ingredientsDiscovered, int cookingStreak,
                              int varietyStreak, List<RecordView> records,
                              List<ResourceLocation> badges, List<ResourceLocation> milestones)
            implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("sync_profile");

        public SyncProfile {
            records = List.copyOf(records);
            badges = List.copyOf(badges);
            milestones = List.copyOf(milestones);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(full);
            buffer.writeVarLong(Math.max(0L, cookingXp));
            buffer.writeVarLong(Math.max(0L, mealsPrepared));
            buffer.writeVarLong(Math.max(0L, mealsEaten));
            buffer.writeVarLong(Math.max(0L, portionsServed));
            buffer.writeVarInt(ingredientsDiscovered);
            buffer.writeVarInt(cookingStreak);
            buffer.writeVarInt(varietyStreak);
            buffer.writeVarInt(records.size());
            records.forEach(record -> record.write(buffer));
            buffer.writeVarInt(badges.size());
            badges.forEach(buffer::writeResourceLocation);
            buffer.writeVarInt(milestones.size());
            milestones.forEach(buffer::writeResourceLocation);
        }

        public static SyncProfile read(FriendlyByteBuf buffer) {
            boolean full = buffer.readBoolean();
            long xp = buffer.readVarLong();
            long prepared = buffer.readVarLong();
            long eaten = buffer.readVarLong();
            long served = buffer.readVarLong();
            int ingredients = buffer.readVarInt();
            int cookingStreak = buffer.readVarInt();
            int varietyStreak = buffer.readVarInt();

            int recordCount = PacketSafety.count(buffer, 65_536);
            List<RecordView> records = new ArrayList<>(recordCount);
            for (int i = 0; i < recordCount; i++) {
                records.add(RecordView.read(buffer));
            }
            List<ResourceLocation> badges = readIds(buffer);
            List<ResourceLocation> milestones = readIds(buffer);
            return new SyncProfile(full, xp, prepared, eaten, served, ingredients,
                    cookingStreak, varietyStreak, records, badges, milestones);
        }
    }

    /**
     * The progression rules the client needs to draw a progress bar correctly.
     * Sent by the server so a client with different config cannot mis-render.
     */
    public record SyncRules(int levelBase, int levelLinear, int levelQuadratic, int maxLevel,
                            List<Integer> masteryThresholds, String masteryRewards,
                            boolean leaderboardsEnabled, boolean bonusesEnabled,
                            double effectChance, double perfectChance, double extraPortionChance,
                            double cookingSpeed, float saturationPerRank, int effectSeconds,
                             boolean perDishMastery, boolean vanillaFood, double vanillaNutritionPerStar,
                             double vanillaSaturationMultiplierPerStar, float vanillaMaxStarSaturation,
                             double vanillaPerfectNutritionMultiplier, boolean starsEnabled, boolean foodEffectsEnabled,
                             boolean flavorsEnabled, boolean nutritionDisplayEnabled, double minimumPerfectChance)
            implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("sync_rules");

        public SyncRules {
            masteryThresholds = List.copyOf(masteryThresholds);
        }

        public SyncRules(int levelBase,int levelLinear,int levelQuadratic,int maxLevel,List<Integer> masteryThresholds,
                         String masteryRewards,boolean leaderboardsEnabled,boolean bonusesEnabled,double effectChance,
                         double perfectChance,double extraPortionChance,double cookingSpeed,float saturationPerRank,
                         int effectSeconds,boolean perDishMastery,boolean vanillaFood,double vanillaNutritionPerStar,
                         double vanillaSaturationMultiplierPerStar,float vanillaMaxStarSaturation,double vanillaPerfectNutritionMultiplier,
                         boolean starsEnabled,boolean foodEffectsEnabled,boolean flavorsEnabled,boolean nutritionDisplayEnabled) {
            this(levelBase,levelLinear,levelQuadratic,maxLevel,masteryThresholds,masteryRewards,leaderboardsEnabled,
                    bonusesEnabled,effectChance,perfectChance,extraPortionChance,cookingSpeed,saturationPerRank,
                    effectSeconds,perDishMastery,vanillaFood,vanillaNutritionPerStar,vanillaSaturationMultiplierPerStar,
                    vanillaMaxStarSaturation,vanillaPerfectNutritionMultiplier,starsEnabled,foodEffectsEnabled,
                    flavorsEnabled,nutritionDisplayEnabled,.01);
        }

        public SyncRules(int levelBase,int levelLinear,int levelQuadratic,int maxLevel,List<Integer> masteryThresholds,
                         String masteryRewards,boolean leaderboardsEnabled,boolean bonusesEnabled,double effectChance,
                         double perfectChance,double extraPortionChance,double cookingSpeed,float saturationPerRank,
                         int effectSeconds,boolean perDishMastery,boolean vanillaFood,double vanillaNutritionPerStar,
                         double vanillaSaturationMultiplierPerStar,float vanillaMaxStarSaturation,double vanillaPerfectNutritionMultiplier,
                         boolean starsEnabled,boolean foodEffectsEnabled) {
            this(levelBase,levelLinear,levelQuadratic,maxLevel,masteryThresholds,masteryRewards,leaderboardsEnabled,
                    bonusesEnabled,effectChance,perfectChance,extraPortionChance,cookingSpeed,saturationPerRank,
                    effectSeconds,perDishMastery,vanillaFood,vanillaNutritionPerStar,vanillaSaturationMultiplierPerStar,
                    vanillaMaxStarSaturation,vanillaPerfectNutritionMultiplier,starsEnabled,foodEffectsEnabled,true,true);
        }

        public SyncRules(int levelBase,int levelLinear,int levelQuadratic,int maxLevel,List<Integer> masteryThresholds,
                         String masteryRewards,boolean leaderboardsEnabled,boolean bonusesEnabled,double effectChance,
                         double perfectChance,double extraPortionChance,double cookingSpeed,float saturationPerRank,
                         int effectSeconds,boolean perDishMastery,boolean vanillaFood,double vanillaNutritionPerStar,
                         double vanillaSaturationMultiplierPerStar,float vanillaMaxStarSaturation,double vanillaPerfectNutritionMultiplier) {
            this(levelBase,levelLinear,levelQuadratic,maxLevel,masteryThresholds,masteryRewards,leaderboardsEnabled,
                    bonusesEnabled,effectChance,perfectChance,extraPortionChance,cookingSpeed,saturationPerRank,
                    effectSeconds,perDishMastery,vanillaFood,vanillaNutritionPerStar,vanillaSaturationMultiplierPerStar,
                    vanillaMaxStarSaturation,vanillaPerfectNutritionMultiplier,true,true);
        }

        public SyncRules(int levelBase,int levelLinear,int levelQuadratic,int maxLevel,List<Integer> masteryThresholds,
                         String masteryRewards,boolean leaderboardsEnabled,boolean bonusesEnabled,double effectChance,
                         double perfectChance,double extraPortionChance,double cookingSpeed,float saturationPerRank,
                         int effectSeconds,boolean perDishMastery,boolean vanillaFood,double vanillaNutritionPerStar) {
            this(levelBase,levelLinear,levelQuadratic,maxLevel,masteryThresholds,masteryRewards,leaderboardsEnabled,
                    bonusesEnabled,effectChance,perfectChance,extraPortionChance,cookingSpeed,saturationPerRank,
                    effectSeconds,perDishMastery,vanillaFood,vanillaNutritionPerStar,0.05,1,1.5);
        }

        public SyncRules(int levelBase, int levelLinear, int levelQuadratic, int maxLevel,
                         List<Integer> masteryThresholds, String masteryRewards,
                         boolean leaderboardsEnabled, boolean bonusesEnabled, double effectChance,
                         double perfectChance, double extraPortionChance, double cookingSpeed,
                         float saturationPerRank, int effectSeconds, boolean perDishMastery, boolean vanillaFood) {
            this(levelBase,levelLinear,levelQuadratic,maxLevel,masteryThresholds,masteryRewards,leaderboardsEnabled,
                    bonusesEnabled,effectChance,perfectChance,extraPortionChance,cookingSpeed,saturationPerRank,
                    effectSeconds,perDishMastery,vanillaFood,0);
        }

        public SyncRules(int levelBase, int levelLinear, int levelQuadratic, int maxLevel,
                         List<Integer> masteryThresholds, String masteryRewards,
                         boolean leaderboardsEnabled, boolean bonusesEnabled,
                         double effectChance, double perfectChance, double extraPortionChance,
                         double cookingSpeed, float saturationPerRank, int effectSeconds) {
            this(levelBase, levelLinear, levelQuadratic, maxLevel, masteryThresholds, masteryRewards,
                    leaderboardsEnabled, bonusesEnabled, effectChance, perfectChance, extraPortionChance,
                    cookingSpeed, saturationPerRank, effectSeconds, false, false);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(levelBase);
            buffer.writeVarInt(levelLinear);
            buffer.writeVarInt(levelQuadratic);
            buffer.writeVarInt(maxLevel);
            buffer.writeVarInt(masteryThresholds.size());
            masteryThresholds.forEach(buffer::writeVarInt);
            buffer.writeUtf(masteryRewards, 32);
            buffer.writeBoolean(leaderboardsEnabled);
            buffer.writeBoolean(bonusesEnabled);
            buffer.writeDouble(effectChance);
            buffer.writeDouble(perfectChance);
            buffer.writeDouble(extraPortionChance);
            buffer.writeDouble(cookingSpeed);
            buffer.writeFloat(saturationPerRank);
            buffer.writeVarInt(effectSeconds);
            buffer.writeBoolean(perDishMastery);
            buffer.writeBoolean(vanillaFood);
            buffer.writeDouble(vanillaNutritionPerStar);
            buffer.writeDouble(vanillaSaturationMultiplierPerStar);
            buffer.writeFloat(vanillaMaxStarSaturation);
            buffer.writeDouble(vanillaPerfectNutritionMultiplier);
            buffer.writeBoolean(starsEnabled);
            buffer.writeBoolean(foodEffectsEnabled);
            buffer.writeBoolean(flavorsEnabled);
            buffer.writeBoolean(nutritionDisplayEnabled);
            buffer.writeDouble(minimumPerfectChance);
        }

        public static SyncRules read(FriendlyByteBuf buffer) {
            int base = buffer.readVarInt();
            int linear = buffer.readVarInt();
            int quadratic = buffer.readVarInt();
            int maxLevel = buffer.readVarInt();
            int count = PacketSafety.count(buffer, 6);
            List<Integer> thresholds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                thresholds.add(buffer.readVarInt());
            }
            return new SyncRules(base, linear, quadratic, maxLevel, thresholds,
                    buffer.readUtf(32), buffer.readBoolean(), buffer.readBoolean(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readFloat(), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readDouble(),buffer.readDouble(),
                    buffer.readFloat(),buffer.readDouble(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),buffer.readDouble());
        }
    }

    /** Detail for one dish, requested when its page is opened. */
    /**
     * One ingredient position of a recipe as the detail panel shows it.
     *
     * @param items   every item the position accepts (for cycling a tag)
     * @param tag     the journal tag entry the position names, or null for a plain item
     * @param count   how many recipe positions this collapses (identical positions are merged)
     */
    public record DetailSlot(List<ResourceLocation> items, ResourceLocation tag, int count) {
        public DetailSlot { items = List.copyOf(items); }
        void write(FriendlyByteBuf b) {
            b.writeVarInt(items.size()); items.forEach(b::writeResourceLocation);
            writeOptional(b, tag); b.writeVarInt(count);
        }
        static DetailSlot read(FriendlyByteBuf b) {
            List<ResourceLocation> items = readIds(b);
            return new DetailSlot(items, readOptional(b), b.readVarInt());
        }
    }

    /** One way to make the dish: where, and its ingredient positions in order. */
    public record DetailRecipe(ResourceLocation recipe, ResourceLocation method, List<DetailSlot> slots) {
        public DetailRecipe { slots = List.copyOf(slots); }
        void write(FriendlyByteBuf b) {
            b.writeResourceLocation(recipe); writeOptional(b, method);
            b.writeVarInt(slots.size()); slots.forEach(slot -> slot.write(b));
        }
        static DetailRecipe read(FriendlyByteBuf b) {
            ResourceLocation recipe = b.readResourceLocation(); ResourceLocation method = readOptional(b);
            int count = b.readVarInt();
            if (count < 0 || count > 64) throw new IllegalArgumentException("Too many recipe slots");
            List<DetailSlot> slots = new ArrayList<>(count);
            for (int i = 0; i < count; i++) slots.add(DetailSlot.read(b));
            return new DetailRecipe(recipe, method, slots);
        }
    }

    /**
     * @param ingredients flat item list (kept for the cooking HUD)
     * @param grouped     recipes with their ingredient positions, for the detail panel
     * @param hideUndiscovered server setting: hide attributes/flavors/recipes until discovered
     */
    public record SyncDishDetail(ResourceLocation target, List<ResourceLocation> ingredients,
                                 List<ResourceLocation> recipes, List<DetailRecipe> grouped,
                                 boolean hideUndiscovered) implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("sync_dish_detail");

        public SyncDishDetail {
            ingredients = List.copyOf(ingredients);
            recipes = List.copyOf(recipes);
            grouped = List.copyOf(grouped);
        }

        public SyncDishDetail(ResourceLocation target, List<ResourceLocation> ingredients, List<ResourceLocation> recipes) {
            this(target, ingredients, recipes, List.of(), false);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(target);
            buffer.writeVarInt(ingredients.size());
            ingredients.forEach(buffer::writeResourceLocation);
            buffer.writeVarInt(recipes.size());
            recipes.forEach(buffer::writeResourceLocation);
            buffer.writeVarInt(grouped.size());
            grouped.forEach(recipe -> recipe.write(buffer));
            buffer.writeBoolean(hideUndiscovered);
        }

        public static SyncDishDetail read(FriendlyByteBuf buffer) {
            ResourceLocation target = buffer.readResourceLocation();
            List<ResourceLocation> ingredients = readIds(buffer), recipes = readIds(buffer);
            int count = PacketSafety.count(buffer, 4096);
            if (count < 0 || count > 256) throw new IllegalArgumentException("Too many recipes");
            List<DetailRecipe> grouped = new ArrayList<>(count);
            for (int i = 0; i < count; i++) grouped.add(DetailRecipe.read(buffer));
            return new SyncDishDetail(target, ingredients, recipes, grouped, buffer.readBoolean());
        }
    }

    /** A single toast-worthy event. Rate limiting happens on the client. */
    public record Notify(Kind kind, ResourceLocation subject, long amount)
            implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("notify");

        public enum Kind {
            DISCOVERY, MASTERY_RANK, MASTERED, LEVEL_UP, CHALLENGE, MILESTONE, XP;

            private static final Kind[] VALUES = values();

            static Kind byOrdinal(int ordinal) {
                return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : XP;
            }
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(kind.ordinal());
            buffer.writeBoolean(subject != null);
            if (subject != null) {
                buffer.writeResourceLocation(subject);
            }
            buffer.writeVarLong(Math.max(0L, amount));
        }

        public static Notify read(FriendlyByteBuf buffer) {
            Kind kind = Kind.byOrdinal(buffer.readVarInt());
            ResourceLocation subject = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            return new Notify(kind, subject, buffer.readVarLong());
        }
    }

    /**
     * Personal statistics page and the sidebar player card.
     *
     * <p>Days are {@code Long.MIN_VALUE} when unknown; meal ids may be null. Achievements list
     * every milestone and badge with what earns it, so locked ones can be shown greyed out.</p>
     */
    public record SyncStatistics(long day, int mealsToday,
                                 long ingredientUses, long feastsPrepared,
                                 int mostMealsInOneDay, long mostMealsDay,
                                 int mostUniqueInOneDay, long mostUniqueDay,
                                 ResourceLocation fastestMeal, long fastestDays,
                                 ResourceLocation firstMasteredMeal, long firstMasteredDay,
                                 int mostFeastPortionsInOneDay,
                                 int discoveryStreak, int longestCooking, int longestVariety,
                                 int longestDiscovery, ResourceLocation selectedBadge,
                                 List<Achievement> achievements) implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("sync_statistics");

        /**
         * One milestone ({@code badge == false}) or badge.
         *
         * @param condition translation key describing how it is earned (milestone type, or the
         *                  challenge title for a badge)
         * @param amount    milestone threshold, or 0
         */
        /**
         * @param progress milestone: the player's current value of the counted stat (else 0)
         * @param reward   milestone: the badge it awards, or null
         */
        public record Achievement(ResourceLocation id, boolean badge, boolean earned,
                                  String condition, long amount, ResourceLocation icon,
                                  long progress, ResourceLocation reward) {
            public Achievement(ResourceLocation id,boolean badge,boolean earned,String condition,long amount){
                this(id,badge,earned,condition,amount,null,0,null);
            }
            public Achievement(ResourceLocation id,boolean badge,boolean earned,String condition,long amount,ResourceLocation icon){
                this(id,badge,earned,condition,amount,icon,0,null);
            }
            void write(FriendlyByteBuf buffer) {
                buffer.writeResourceLocation(id);
                buffer.writeBoolean(badge);
                buffer.writeBoolean(earned);
                buffer.writeUtf(condition == null ? "" : condition, 256);
                buffer.writeVarLong(Math.max(0L, amount));
                writeOptional(buffer,icon);
                buffer.writeVarLong(Math.max(0L, progress));
                writeOptional(buffer,reward);
            }

            static Achievement read(FriendlyByteBuf buffer) {
                return new Achievement(buffer.readResourceLocation(), buffer.readBoolean(), buffer.readBoolean(),
                        buffer.readUtf(256), buffer.readVarLong(),readOptional(buffer),buffer.readVarLong(),readOptional(buffer));
            }
        }

        public SyncStatistics {
            achievements = List.copyOf(achievements);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
            buffer.writeBoolean(id != null);
            if (id != null) buffer.writeResourceLocation(id);
        }

        private static ResourceLocation readId(FriendlyByteBuf buffer) {
            return buffer.readBoolean() ? buffer.readResourceLocation() : null;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeLong(day);
            buffer.writeVarInt(mealsToday);
            buffer.writeVarLong(Math.max(0L, ingredientUses));
            buffer.writeVarLong(Math.max(0L, feastsPrepared));
            buffer.writeVarInt(mostMealsInOneDay);
            buffer.writeLong(mostMealsDay);
            buffer.writeVarInt(mostUniqueInOneDay);
            buffer.writeLong(mostUniqueDay);
            writeId(buffer, fastestMeal);
            buffer.writeLong(fastestDays);
            writeId(buffer, firstMasteredMeal);
            buffer.writeLong(firstMasteredDay);
            buffer.writeVarInt(mostFeastPortionsInOneDay);
            buffer.writeVarInt(discoveryStreak);
            buffer.writeVarInt(longestCooking);
            buffer.writeVarInt(longestVariety);
            buffer.writeVarInt(longestDiscovery);
            writeId(buffer, selectedBadge);
            buffer.writeVarInt(achievements.size());
            achievements.forEach(achievement -> achievement.write(buffer));
        }

        public static SyncStatistics read(FriendlyByteBuf buffer) {
            long day = buffer.readLong();
            int today = buffer.readVarInt();
            long uses = buffer.readVarLong();
            long feasts = buffer.readVarLong();
            int mostMeals = buffer.readVarInt();
            long mostMealsDay = buffer.readLong();
            int mostUnique = buffer.readVarInt();
            long mostUniqueDay = buffer.readLong();
            ResourceLocation fastestMeal = readId(buffer);
            long fastestDays = buffer.readLong();
            ResourceLocation firstMeal = readId(buffer);
            long firstDay = buffer.readLong();
            int feastPortions = buffer.readVarInt();
            int discovery = buffer.readVarInt();
            int longestCooking = buffer.readVarInt();
            int longestVariety = buffer.readVarInt();
            int longestDiscovery = buffer.readVarInt();
            ResourceLocation selectedBadge = readId(buffer);
            int count = PacketSafety.count(buffer, 4096);
            List<Achievement> achievements = new ArrayList<>(count);
            for (int i = 0; i < count; i++) achievements.add(Achievement.read(buffer));
            return new SyncStatistics(day, today, uses, feasts, mostMeals, mostMealsDay, mostUnique, mostUniqueDay,
                    fastestMeal, fastestDays, firstMeal, firstDay, feastPortions, discovery, longestCooking,
                    longestVariety, longestDiscovery, selectedBadge, achievements);
        }
    }

    /** Tells the client to open the journal, in response to {@code /cj open}. */
    public record OpenJournal() implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("open_journal");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
        }

        public static OpenJournal read(FriendlyByteBuf buffer) {
            return new OpenJournal();
        }
    }

    public record SyncCategoryPermissions(boolean canEdit) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("sync_category_permissions");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) { buffer.writeBoolean(canEdit); }
        public static SyncCategoryPermissions read(FriendlyByteBuf buffer) {
            return new SyncCategoryPermissions(buffer.readBoolean());
        }
    }

    public record CategoryEditModeRequest(boolean enabled) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("category_edit_mode_request");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) { buffer.writeBoolean(enabled); }
        public static CategoryEditModeRequest read(FriendlyByteBuf buffer) {
            return new CategoryEditModeRequest(buffer.readBoolean());
        }
    }

    public record SyncCategoryEditMode(boolean enabled) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("sync_category_edit_mode");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) { buffer.writeBoolean(enabled); }
        public static SyncCategoryEditMode read(FriendlyByteBuf buffer) {
            return new SyncCategoryEditMode(buffer.readBoolean());
        }
    }

    /**
     * {@code targetId} is the category being edited. It must not be called {@code id}:
     * {@link CuisineJournalPacket#id()} returns the packet type and would shadow it.
     */
    public record CategoryEditRequest(int requestId, Action action, ResourceLocation targetId, ResourceLocation parent,
                                     String name, ResourceLocation icon, List<ResourceLocation> items,
                                     Map<String, Long> expectedVersions)
            implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("category_edit_request");
        public enum Action {
            CREATE_ROOT, CREATE_SUB, EDIT_ROOT, EDIT_SUB, MOVE_SUB,
            DELETE_ROOT, DELETE_SUB, BLOCK_ROOT, BLOCK_SUB,
            RESTORE_ROOT, RESTORE_SUB, ASSIGN_FOOD, ASSIGN_FOODS, RESET_ROOT, RESET_SUB, UNASSIGN_FOOD;
            private static final Action[] VALUES = values();
            static Action byOrdinal(int value) {
                if (value < 0 || value >= VALUES.length) throw new IllegalArgumentException("Invalid category action");
                return VALUES[value];
            }
        }
        /** Compatibility constructor for tests; existing targets require expected versions on the server. */
        public CategoryEditRequest(int requestId, Action action, ResourceLocation targetId, ResourceLocation parent,
                                   String name, ResourceLocation icon, List<ResourceLocation> items) {
            this(requestId, action, targetId, parent, name, icon, items, Map.of());
        }
        public CategoryEditRequest {
            items = List.copyOf(items == null ? List.of() : items);
            expectedVersions = copyVersions(expectedVersions);
        }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(requestId);
            buffer.writeVarInt(action.ordinal());
            writeOptional(buffer, targetId); writeOptional(buffer, parent);
            buffer.writeBoolean(name != null); if (name != null) buffer.writeUtf(name, 256);
            writeOptional(buffer, icon);
            buffer.writeVarInt(items.size()); items.forEach(buffer::writeResourceLocation);
            writeVersions(buffer, expectedVersions);
        }
        public static CategoryEditRequest read(FriendlyByteBuf buffer) {
            int requestId = buffer.readVarInt();
            Action action = Action.byOrdinal(buffer.readVarInt());
            ResourceLocation id = readOptional(buffer), parent = readOptional(buffer);
            String name = buffer.readBoolean() ? buffer.readUtf(256) : null;
            ResourceLocation icon = readOptional(buffer);
            int count = buffer.readVarInt();
            if (count < 0 || count > 4096) throw new IllegalArgumentException("Too many category items");
            List<ResourceLocation> items = new ArrayList<>(count);
            for (int i = 0; i < count; i++) items.add(buffer.readResourceLocation());
            return new CategoryEditRequest(requestId, action, id, parent, name, icon, items, readVersions(buffer));
        }
    }

    public record CategoryEditResult(int requestId, boolean success, String message, List<String> args) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("category_edit_result");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(requestId);
            buffer.writeBoolean(success); buffer.writeUtf(message == null ? "" : message, 256);
            buffer.writeVarInt(args.size()); args.forEach(arg -> buffer.writeUtf(arg, 256));
        }
        public static CategoryEditResult read(FriendlyByteBuf buffer) {
            int id = buffer.readVarInt(); boolean success = buffer.readBoolean(); String message = buffer.readUtf(256);
            int count = buffer.readVarInt();
            if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid result arguments");
            List<String> args = new ArrayList<>();
            for (int i = 0; i < count; i++) args.add(buffer.readUtf(256));
            return new CategoryEditResult(id, success, message, List.copyOf(args));
        }
    }

    public record CategoryMetadata(ResourceLocation id, ResourceLocation parent, String titleKey,
                                   boolean literalTitle, ResourceLocation icon, byte source,
                                   boolean protectedEntry, boolean hidden, String originalTitleKey,
                                   boolean originalLiteralTitle, ResourceLocation originalIcon) {
        void write(FriendlyByteBuf b) {
            b.writeResourceLocation(id); writeOptional(b, parent); b.writeUtf(titleKey == null ? "" : titleKey, 256);
            b.writeBoolean(literalTitle); writeOptional(b, icon); b.writeByte(source);
            b.writeBoolean(protectedEntry); b.writeBoolean(hidden);
            b.writeUtf(originalTitleKey == null ? (titleKey == null ? "" : titleKey) : originalTitleKey, 256);
            b.writeBoolean(originalLiteralTitle); writeOptional(b, originalIcon);
        }
        static CategoryMetadata read(FriendlyByteBuf b) {
            return new CategoryMetadata(b.readResourceLocation(), readOptional(b), b.readUtf(256),
                    b.readBoolean(), readOptional(b), b.readByte(), b.readBoolean(), b.readBoolean(),
                    b.readUtf(256), b.readBoolean(), readOptional(b));
        }
    }

    public record SyncCategoryEditor(List<CategoryMetadata> categories, List<ResourceLocation> manualItems,
                                     Map<String, Long> versions)
            implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("sync_category_editor");
        /** Compatibility constructor for tests. */
        public SyncCategoryEditor(List<CategoryMetadata> categories, List<ResourceLocation> manualItems) {
            this(categories, manualItems, Map.of());
        }
        public SyncCategoryEditor {
            categories = List.copyOf(categories); manualItems = List.copyOf(manualItems);
            versions = copyVersions(versions);
        }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(PacketSafety.size(categories.size(),16384)); categories.forEach(value -> value.write(b));
            b.writeVarInt(PacketSafety.size(manualItems.size(),65536)); manualItems.forEach(b::writeResourceLocation);
            writeVersions(b, versions);
        }
        public static SyncCategoryEditor read(FriendlyByteBuf b) {
            int count = b.readVarInt();
            if (count < 0 || count > 16384) throw new IllegalArgumentException("Invalid category count");
            List<CategoryMetadata> categories = new ArrayList<>();
            for (int i = 0; i < count; i++) categories.add(CategoryMetadata.read(b));
            int items = b.readVarInt();
            if (items < 0 || items > 65536) throw new IllegalArgumentException("Invalid manual item count");
            List<ResourceLocation> manual = new ArrayList<>();
            for (int i = 0; i < items; i++) manual.add(b.readResourceLocation());
            return new SyncCategoryEditor(categories, manual, readVersions(b));
        }
    }

    private static void writeOptional(FriendlyByteBuf buffer, ResourceLocation value) {
        buffer.writeBoolean(value != null); if (value != null) buffer.writeResourceLocation(value);
    }
    private static ResourceLocation readOptional(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readResourceLocation() : null;
    }


    /** One challenge as the journal draws it. */
    public record ChallengeView(ResourceLocation id, String titleKey, String descriptionKey,
                                boolean completed, int progress, int goal, String scope, boolean manual,
                                boolean started, boolean failed, boolean locked, long remainingTicks,
                                List<ObjectiveView> objectives, int cookingXp, ResourceLocation badge,
                                List<String> itemRewards) {
        public ChallengeView(ResourceLocation id,String titleKey,String descriptionKey,boolean completed,int progress,int goal){
            this(id,titleKey,descriptionKey,completed,progress,goal,"PERMANENT",false,true,false,false,-1,List.of(),0,null,List.of());
        }
        public ChallengeView(ResourceLocation id,String titleKey,String descriptionKey,boolean completed,int progress,int goal,
                             String scope,boolean manual,boolean started,boolean failed,boolean locked,long remainingTicks,
                             List<ObjectiveView> objectives,int cookingXp,ResourceLocation badge){
            this(id,titleKey,descriptionKey,completed,progress,goal,scope,manual,started,failed,locked,remainingTicks,
                    objectives,cookingXp,badge,List.of());
        }
        public ChallengeView{objectives=List.copyOf(objectives);itemRewards=List.copyOf(itemRewards);}
        /** {@code unique}: counts distinct subjects (种) rather than repeats (份/次). */
        public record ObjectiveView(String type,String value,int progress,int goal,boolean unique){
            public ObjectiveView(String type,String value,int progress,int goal){this(type,value,progress,goal,false);}
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(titleKey, 256);
            buffer.writeUtf(descriptionKey, 256);
            buffer.writeBoolean(completed);
            buffer.writeVarInt(progress);
            buffer.writeVarInt(goal);
            buffer.writeUtf(scope,32);buffer.writeBoolean(manual);buffer.writeBoolean(started);buffer.writeBoolean(failed);
            buffer.writeBoolean(locked);buffer.writeLong(remainingTicks);buffer.writeVarInt(objectives.size());
            for(var objective:objectives){buffer.writeUtf(objective.type(),64);buffer.writeUtf(objective.value(),256);
                buffer.writeVarInt(objective.progress());buffer.writeVarInt(objective.goal());buffer.writeBoolean(objective.unique());}
            buffer.writeVarInt(cookingXp);writeOptional(buffer,badge);buffer.writeVarInt(itemRewards.size());
            itemRewards.forEach(item->buffer.writeUtf(item,256));
        }

        static ChallengeView read(FriendlyByteBuf buffer) {
            var id=buffer.readResourceLocation();var title=buffer.readUtf(256);var description=buffer.readUtf(256);
            boolean completed=buffer.readBoolean();int progress=buffer.readVarInt(),goal=buffer.readVarInt();
            String scope=buffer.readUtf(32);boolean manual=buffer.readBoolean(),started=buffer.readBoolean(),failed=buffer.readBoolean(),locked=buffer.readBoolean();
            long remaining=buffer.readLong();int count=buffer.readVarInt();if(count<0||count>16)throw new IllegalArgumentException("Invalid objective count");
            var objectives=new ArrayList<ObjectiveView>();for(int i=0;i<count;i++)objectives.add(new ObjectiveView(buffer.readUtf(64),buffer.readUtf(256),buffer.readVarInt(),buffer.readVarInt(),buffer.readBoolean()));
            int cookingXp=buffer.readVarInt();var badge=readOptional(buffer);int itemCount=buffer.readVarInt();
            if(itemCount<0||itemCount>64)throw new IllegalArgumentException("Invalid challenge reward item count");
            var items=new ArrayList<String>();for(int i=0;i<itemCount;i++)items.add(buffer.readUtf(256));
            return new ChallengeView(id,title,description,completed,progress,goal,scope,manual,started,failed,locked,remaining,objectives,cookingXp,badge,items);
        }
    }

    /**
     * The server rules the settings screen shows and edits (only admins may change them).
     *
     * @param foodMode     "valheim" or "vanilla" (effective)
     * @param manual       which of flavor / potion / nutrition differ from the food mode's default
     * @param starMode     0 = stars off, 1 = per subcategory, 2 = per dish
     * @param discovery    ConfigEnums.DiscoveryMode name
     * @param sharing      ConfigEnums.SharedDiscovery name
     * @param modeLocked   the food mode cannot be switched (for example sol_valheim is installed)
     */
    public record SyncServerSettings(boolean canEdit, String foodMode, boolean modeLocked, boolean flavors, boolean potions,
                                     boolean nutrition, int manual, int starMode, String discovery, String sharing,
                                     boolean hideUndiscovered, boolean unification, boolean compatInstalled,
                                     boolean unificationBusy) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("sync_server_settings");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeBoolean(canEdit); b.writeUtf(foodMode, 16); b.writeBoolean(modeLocked);
            b.writeBoolean(flavors); b.writeBoolean(potions); b.writeBoolean(nutrition); b.writeVarInt(manual);
            b.writeVarInt(starMode); b.writeUtf(discovery, 32); b.writeUtf(sharing, 32); b.writeBoolean(hideUndiscovered);
            b.writeBoolean(unification); b.writeBoolean(compatInstalled); b.writeBoolean(unificationBusy);
        }
        public static SyncServerSettings read(FriendlyByteBuf b) {
            return new SyncServerSettings(b.readBoolean(), b.readUtf(16), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                    b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readUtf(32), b.readUtf(32), b.readBoolean(),
                    b.readBoolean(), b.readBoolean(), b.readBoolean());
        }
    }

    /** One server rule change from the settings screen; the server checks admin permission. */
    public record SetServerSetting(Key key, String value) implements CuisineJournalPacket {
        public enum Key { FOOD_MODE, FLAVORS, POTIONS, NUTRITION, RESET_FLAVORS, RESET_POTIONS, RESET_NUTRITION,
            STAR_MODE, DISCOVERY, SHARING, HIDE_UNDISCOVERED, UNIFICATION }
        public static final ResourceLocation ID = packetId("set_server_setting");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) { b.writeEnum(key); b.writeUtf(value == null ? "" : value, 32); }
        public static SetServerSetting read(FriendlyByteBuf b) { return new SetServerSetting(b.readEnum(Key.class), b.readUtf(32)); }
    }

    public record SyncTodayDish(long day,ResourceLocation dish) implements CuisineJournalPacket {
        public static final ResourceLocation ID=packetId("sync_today_dish");
        @Override public ResourceLocation id(){return ID;}
        @Override public void write(FriendlyByteBuf buffer){buffer.writeLong(day);writeOptional(buffer,dish);}
        public static SyncTodayDish read(FriendlyByteBuf buffer){return new SyncTodayDish(buffer.readLong(),readOptional(buffer));}
    }

    /**
     * One collection and how far into it the player is.
     *
     * @param titleKey  a translation key for datapack collections, or a literal
     *                  mod name for the automatically generated per-mod ones
     * @param literalTitle whether {@code titleKey} is already display text
     */
    public record SubcategoryView(ResourceLocation id, String titleKey, boolean literalTitle,
                                  ResourceLocation icon) {
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(titleKey == null ? "" : titleKey, 256);
            buffer.writeBoolean(literalTitle);
            buffer.writeBoolean(icon != null);
            if (icon != null) {
                buffer.writeResourceLocation(icon);
            }
        }

        static SubcategoryView read(FriendlyByteBuf buffer) {
            ResourceLocation id = buffer.readResourceLocation();
            String titleKey = buffer.readUtf(256);
            boolean literalTitle = buffer.readBoolean();
            ResourceLocation icon = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            return new SubcategoryView(id, titleKey, literalTitle, icon);
        }
    }

    public record CollectionView(ResourceLocation id, String titleKey, boolean literalTitle,
                                 ResourceLocation icon, int total, int discovered, int mastered,
                                 List<SubcategoryView> subcategories, boolean isModCategory) {

        /** Compatibility constructor with one implicit legacy subcategory. */
        public CollectionView(ResourceLocation id, String titleKey, boolean literalTitle,
                              ResourceLocation icon, int total, int discovered, int mastered) {
            this(id, titleKey, literalTitle, icon, total, discovered, mastered,
                    List.of(new SubcategoryView(id, titleKey, literalTitle, icon)), false);
        }

        public CollectionView {
            subcategories = List.copyOf(subcategories);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(titleKey == null ? "" : titleKey, 256);
            buffer.writeBoolean(literalTitle);
            buffer.writeBoolean(icon != null);
            if (icon != null) {
                buffer.writeResourceLocation(icon);
            }
            buffer.writeVarInt(total);
            buffer.writeVarInt(discovered);
            buffer.writeVarInt(mastered);
            buffer.writeVarInt(subcategories.size());
            subcategories.forEach(subcategory -> subcategory.write(buffer));
            buffer.writeBoolean(isModCategory);
        }

        static CollectionView read(FriendlyByteBuf buffer) {
            ResourceLocation id = buffer.readResourceLocation();
            String title = buffer.readUtf(256);
            boolean literal = buffer.readBoolean();
            ResourceLocation icon = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            int total = buffer.readVarInt();
            int discovered = buffer.readVarInt();
            int mastered = buffer.readVarInt();
            List<SubcategoryView> subs = readSubcategories(buffer);
            boolean isModCategory = buffer.readBoolean();
            return new CollectionView(id, title, literal, icon, total, discovered, mastered, subs, isModCategory);
        }

        private static List<SubcategoryView> readSubcategories(FriendlyByteBuf buffer) {
            int count = PacketSafety.count(buffer, 16_384);
            List<SubcategoryView> subcategories = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                subcategories.add(SubcategoryView.read(buffer));
            }
            return subcategories;
        }
    }

    /**
     * One entry in the ingredient journal: an item, or a merged ingredient tag.
     *
     * <p>For an item, {@code meals} are dishes that name it directly and {@code tagIds} the
     * tag entries it belongs to. For a tag, {@code item} is the entry key, {@code members} its
     * items, {@code meals} dishes that name the tag and {@code tagIds} the merged real tag ids;
     * a tag has no use records.</p>
     */
    public record IngredientTagSource(ResourceLocation tag, List<ResourceLocation> members) {
        public IngredientTagSource { members = List.copyOf(members); }
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(tag);
            buffer.writeVarInt(members.size()); members.forEach(buffer::writeResourceLocation);
        }
        static IngredientTagSource read(FriendlyByteBuf buffer) {
            return new IngredientTagSource(buffer.readResourceLocation(), readIds(buffer));
        }
    }

    public record IngredientTagUse(ResourceLocation meal, ResourceLocation recipe, int slotIndex,
                                   ResourceLocation tag, List<ResourceLocation> acceptedItems) {
        public IngredientTagUse { acceptedItems = List.copyOf(acceptedItems); }
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(meal); buffer.writeBoolean(recipe != null);
            if (recipe != null) buffer.writeResourceLocation(recipe);
            buffer.writeInt(slotIndex); buffer.writeResourceLocation(tag);
            buffer.writeVarInt(acceptedItems.size()); acceptedItems.forEach(buffer::writeResourceLocation);
        }
        static IngredientTagUse read(FriendlyByteBuf buffer) {
            ResourceLocation meal = buffer.readResourceLocation();
            ResourceLocation recipe = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            return new IngredientTagUse(meal, recipe, buffer.readInt(), buffer.readResourceLocation(), readIds(buffer));
        }
    }

    /**
     * An ingredient's flavor binding as the journal shows and edits it.
     *
     * @param source    whether the ingredient passes its flavors on to dishes
     * @param flavors   bound preset ids, in order
     * @param local     whether an in-game edit overrides the data pack binding
     * @param legacy    whether old inline {@code stages} are still configured (read-only)
     * @param inherited flavors passed down from tag entries this item belongs to
     * @param problems  unknown preset ids and similar diagnostics
     * @param version   binding version of this displayed ingredient, used for edit preconditions
     */
    public record FlavorInfo(boolean source, List<ResourceLocation> flavors, boolean local, boolean legacy,
                             List<InheritedFlavor> inherited, List<String> problems, long version) {
        public static final FlavorInfo NONE = new FlavorInfo(false, List.of(), false, false, List.of(), List.of(), 0L);
        /** Compatibility constructor for tests. */
        public FlavorInfo(boolean source, List<ResourceLocation> flavors, boolean local, boolean legacy,
                          List<InheritedFlavor> inherited, List<String> problems) {
            this(source, flavors, local, legacy, inherited, problems, 0L);
        }
        public FlavorInfo {
            flavors = List.copyOf(flavors); inherited = List.copyOf(inherited); problems = List.copyOf(problems);
            if (version < 0) throw new IllegalArgumentException("Invalid binding version");
        }
        /** Whether anything flavours this ingredient (its own binding or a tag it belongs to). */
        public boolean any() {
            return source && (!flavors.isEmpty() || legacy) || inherited.stream().anyMatch(value -> !value.flavors().isEmpty());
        }
        void write(FriendlyByteBuf b) {
            b.writeBoolean(source); b.writeVarInt(flavors.size()); flavors.forEach(b::writeResourceLocation);
            b.writeBoolean(local); b.writeBoolean(legacy);
            b.writeVarInt(inherited.size());
            for (InheritedFlavor value : inherited) {
                b.writeResourceLocation(value.tag()); b.writeVarInt(value.flavors().size());
                value.flavors().forEach(b::writeResourceLocation);
            }
            b.writeVarInt(problems.size()); problems.forEach(problem -> b.writeUtf(problem, 256));
            b.writeLong(version);
        }
        static FlavorInfo read(FriendlyByteBuf b) {
            boolean source = b.readBoolean(); List<ResourceLocation> flavors = readIds(b);
            boolean local = b.readBoolean(), legacy = b.readBoolean();
            int count = b.readVarInt(); if (count < 0 || count > 256) throw new IllegalArgumentException("Too many inherited flavors");
            List<InheritedFlavor> inherited = new ArrayList<>();
            for (int i = 0; i < count; i++) inherited.add(new InheritedFlavor(b.readResourceLocation(), readIds(b)));
            int problemCount = b.readVarInt(); if (problemCount < 0 || problemCount > 64) throw new IllegalArgumentException("Too many problems");
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < problemCount; i++) problems.add(b.readUtf(256));
            return new FlavorInfo(source, flavors, local, legacy, inherited, problems, b.readLong());
        }
    }

    public record InheritedFlavor(ResourceLocation tag, List<ResourceLocation> flavors) {
        public InheritedFlavor { flavors = List.copyOf(flavors); }
    }

    /**
     * Every flavor preset, sent whole (as its data-pack JSON) so the journal can show stage
     * tables and the editor can start from the exact stored value.
     *
     * @param origin 0 = data pack, 1 = data pack edited in game, 2 = created in game
     * @param usedBy ingredient entries that bind the preset
     */
    public record FlavorPresetView(ResourceLocation id, String json, byte origin, int usedBy) {
        void write(FriendlyByteBuf b) {
            b.writeResourceLocation(id); b.writeUtf(json, 65536); b.writeByte(origin); b.writeVarInt(usedBy);
        }
        static FlavorPresetView read(FriendlyByteBuf b) {
            return new FlavorPresetView(b.readResourceLocation(), b.readUtf(65536), b.readByte(), b.readVarInt());
        }
    }

    /** All presets plus whether this player may edit flavors and what is waiting to be exported. */
    public record SyncFlavors(List<FlavorPresetView> presets, boolean canEdit, int localPresets,
                              int localBindings, boolean localCategories, Map<String, Long> versions) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("sync_flavors");
        /** Compatibility constructor for tests. */
        public SyncFlavors(List<FlavorPresetView> presets, boolean canEdit, int localPresets,
                           int localBindings, boolean localCategories) {
            this(presets, canEdit, localPresets, localBindings, localCategories, Map.of());
        }
        public SyncFlavors { presets = List.copyOf(presets); versions = copyVersions(versions); }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(PacketSafety.size(presets.size(),4096)); presets.forEach(value -> value.write(b));
            b.writeBoolean(canEdit); b.writeVarInt(localPresets); b.writeVarInt(localBindings); b.writeBoolean(localCategories);
            writeVersions(b, versions);
        }
        public static SyncFlavors read(FriendlyByteBuf b) {
            int count = b.readVarInt(); if (count < 0 || count > 4096) throw new IllegalArgumentException("Too many flavors");
            List<FlavorPresetView> presets = new ArrayList<>();
            for (int i = 0; i < count; i++) presets.add(FlavorPresetView.read(b));
            return new SyncFlavors(presets, b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readBoolean(), readVersions(b));
        }
    }

    /**
     * One flavor edit.
     *
     * <ul>
     *   <li>BIND — target ingredient ({@code tag} says item or tag entry), {@code source} and
     *       {@code flavors} replace its binding</li>
     *   <li>REVERT_BINDING — drop the in-game binding of the target ingredient</li>
     *   <li>SAVE_PRESET — {@code json} is the full preset for id {@code target}; a null target
     *       creates a new in-game preset</li>
     *   <li>REVERT_PRESET / DELETE_PRESET — drop the local copy of {@code target}</li>
     * </ul>
     */
    public record FlavorEditRequest(int requestId, Action action, ResourceLocation target, boolean tag,
                                    boolean source, List<ResourceLocation> flavors, String json, int flags,
                                    Map<String, Long> expectedVersions)
            implements CuisineJournalPacket {
        public enum Action { BIND, REVERT_BINDING, SAVE_PRESET, REVERT_PRESET, DELETE_PRESET }
        public static final ResourceLocation ID = packetId("flavor_edit_request");
        /** Compatibility constructor for tests; existing targets require expected versions on the server. */
        public FlavorEditRequest(int requestId, Action action, ResourceLocation target, boolean tag,
                                 boolean source, List<ResourceLocation> flavors, String json, int flags) {
            this(requestId, action, target, tag, source, flavors, json, flags, Map.of());
        }
        public FlavorEditRequest {
            flavors = List.copyOf(flavors); json = json == null ? "" : json;
            expectedVersions = copyVersions(expectedVersions);
        }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(requestId); b.writeEnum(action); writeOptional(b, target); b.writeBoolean(tag);
            b.writeBoolean(source); b.writeVarInt(flavors.size()); flavors.forEach(b::writeResourceLocation);
            b.writeUtf(json, 65536); b.writeVarInt(flags);
            writeVersions(b, expectedVersions);
        }
        public static FlavorEditRequest read(FriendlyByteBuf b) {
            int id = b.readVarInt(); Action action = b.readEnum(Action.class); ResourceLocation target = readOptional(b);
            boolean tag = b.readBoolean(), source = b.readBoolean();
            List<ResourceLocation> flavors = readIds(b);
            if (flavors.size() > 64) throw new IllegalArgumentException("Too many flavors");
            return new FlavorEditRequest(id, action, target, tag, source, flavors, b.readUtf(65536), b.readVarInt(), readVersions(b));
        }
    }

    public record FlavorEditResult(int requestId, boolean success, String message, List<String> args,
                                   ResourceLocation created) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("flavor_edit_result");
        public FlavorEditResult { args = List.copyOf(args); }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(requestId); b.writeBoolean(success); b.writeUtf(message == null ? "" : message, 256);
            b.writeVarInt(args.size()); args.forEach(arg -> b.writeUtf(arg, 256)); writeOptional(b, created);
        }
        public static FlavorEditResult read(FriendlyByteBuf b) {
            int id = b.readVarInt(); boolean success = b.readBoolean(); String message = b.readUtf(256);
            int count = b.readVarInt(); if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid result arguments");
            List<String> args = new ArrayList<>(); for (int i = 0; i < count; i++) args.add(b.readUtf(256));
            return new FlavorEditResult(id, success, message, args, readOptional(b));
        }
    }

    /**
     * One scheme in the editor list.
     *
     * @param source 0 = generated default, 1 = data pack, 2 = the player's own
     * @param counts flavor: [flavors, bindings]; category: [roots, subcategories, assigned, hidden, changed]
     */
    public record SchemeView(String id, byte source, String name, String description, String saved, List<Integer> counts) {
        public SchemeView { counts = List.copyOf(counts); }
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 256); b.writeByte(source); b.writeUtf(name, 256); b.writeUtf(description, 512); b.writeUtf(saved, 64);
            b.writeVarInt(counts.size()); counts.forEach(b::writeVarInt);
        }
        static SchemeView read(FriendlyByteBuf b) {
            String id = b.readUtf(256); byte source = b.readByte(); String name = b.readUtf(256), description = b.readUtf(512), saved = b.readUtf(64);
            int count = b.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Too many counts");
            List<Integer> counts = new ArrayList<>(); for (int i = 0; i < count; i++) counts.add(b.readVarInt());
            return new SchemeView(id, source, name, description, saved, counts);
        }
    }

    /** Both kinds of scheme, which one each kind is based on, and whether there are unsaved changes. */
    public record SyncSchemes(List<SchemeView> flavor, List<SchemeView> category, String currentFlavor,
                              String currentCategory, boolean dirtyFlavor, boolean dirtyCategory, boolean canEdit,
                              List<Integer> localFlavor, List<Integer> localCategory, Map<String, Long> versions)
            implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("sync_schemes");
        /** Compatibility constructor for tests. */
        public SyncSchemes(List<SchemeView> flavor, List<SchemeView> category, String currentFlavor,
                           String currentCategory, boolean dirtyFlavor, boolean dirtyCategory, boolean canEdit,
                           List<Integer> localFlavor, List<Integer> localCategory) {
            this(flavor, category, currentFlavor, currentCategory, dirtyFlavor, dirtyCategory, canEdit,
                    localFlavor, localCategory, Map.of());
        }
        public SyncSchemes {
            flavor = List.copyOf(flavor); category = List.copyOf(category);
            currentFlavor = currentFlavor == null ? "" : currentFlavor; currentCategory = currentCategory == null ? "" : currentCategory;
            localFlavor = List.copyOf(localFlavor); localCategory = List.copyOf(localCategory);
            versions = copyVersions(versions);
        }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(PacketSafety.size(flavor.size(),512)); flavor.forEach(v -> v.write(b));
            b.writeVarInt(PacketSafety.size(category.size(),512)); category.forEach(v -> v.write(b));
            b.writeUtf(currentFlavor, 256); b.writeUtf(currentCategory, 256);
            b.writeBoolean(dirtyFlavor); b.writeBoolean(dirtyCategory); b.writeBoolean(canEdit);
            b.writeVarInt(localFlavor.size()); localFlavor.forEach(b::writeVarInt);
            b.writeVarInt(localCategory.size()); localCategory.forEach(b::writeVarInt);
            writeVersions(b, versions);
        }
        private static List<SchemeView> views(FriendlyByteBuf b) {
            int count = b.readVarInt(); if (count < 0 || count > 512) throw new IllegalArgumentException("Too many schemes");
            List<SchemeView> list = new ArrayList<>(); for (int i = 0; i < count; i++) list.add(SchemeView.read(b));
            return list;
        }
        private static List<Integer> ints(FriendlyByteBuf b) {
            int count = b.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Too many counts");
            List<Integer> list = new ArrayList<>(); for (int i = 0; i < count; i++) list.add(b.readVarInt());
            return list;
        }
        public static SyncSchemes read(FriendlyByteBuf b) {
            List<SchemeView> flavor = views(b), category = views(b);
            String cf = b.readUtf(256), cc = b.readUtf(256);
            boolean df = b.readBoolean(), dc = b.readBoolean(), edit = b.readBoolean();
            return new SyncSchemes(flavor, category, cf, cc, df, dc, edit, ints(b), ints(b), readVersions(b));
        }
    }

    /**
     * A scheme action. {@code category} picks the kind. SAVE_NEW uses name/description;
     * OVERWRITE, LOAD, DELETE and EXPORT use id; RENAME uses id + name; COPY uses id + name.
     */
    public record SchemeRequest(int requestId, boolean category, Action action, String scheme, String name, String description,
                                Map<String, Long> expectedVersions)
            implements CuisineJournalPacket {
        public enum Action { SAVE_NEW, OVERWRITE, LOAD, RENAME, DELETE, COPY, EXPORT }
        public static final ResourceLocation ID = packetId("scheme_request");
        /** Compatibility constructor for tests; existing targets require expected versions on the server. */
        public SchemeRequest(int requestId, boolean category, Action action, String scheme, String name, String description) {
            this(requestId, category, action, scheme, name, description, Map.of());
        }
        public SchemeRequest {
            scheme = scheme == null ? "" : scheme; name = name == null ? "" : name;
            description = description == null ? "" : description;
            expectedVersions = copyVersions(expectedVersions);
        }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(requestId); b.writeBoolean(category); b.writeEnum(action);
            b.writeUtf(scheme, 256); b.writeUtf(name, 256); b.writeUtf(description, 512);
            writeVersions(b, expectedVersions);
        }
        public static SchemeRequest read(FriendlyByteBuf b) {
            return new SchemeRequest(b.readVarInt(), b.readBoolean(), b.readEnum(Action.class), b.readUtf(256),
                    b.readUtf(256), b.readUtf(512), readVersions(b));
        }
    }

    public record SchemeResult(int requestId, boolean success, String message, List<String> args, String scheme)
            implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("scheme_result");
        public SchemeResult { args = List.copyOf(args); scheme = scheme == null ? "" : scheme; }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeVarInt(requestId); b.writeBoolean(success); b.writeUtf(message == null ? "" : message, 256);
            b.writeVarInt(args.size()); args.forEach(a -> b.writeUtf(a, 256)); b.writeUtf(scheme, 256);
        }
        public static SchemeResult read(FriendlyByteBuf b) {
            int id = b.readVarInt(); boolean ok = b.readBoolean(); String message = b.readUtf(256);
            int count = b.readVarInt(); if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid result arguments");
            List<String> args = new ArrayList<>(); for (int i = 0; i < count; i++) args.add(b.readUtf(256));
            return new SchemeResult(id, ok, message, args, b.readUtf(256));
        }
    }

    /** Asks for a dish's food values at one star level, with and without flavors. */
    public record FlavorPreviewRequest(ResourceLocation dish, int stars) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("flavor_preview_request");
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) { b.writeResourceLocation(dish); b.writeVarInt(stars); }
        public static FlavorPreviewRequest read(FriendlyByteBuf b) {
            return new FlavorPreviewRequest(b.readResourceLocation(), Math.max(0, Math.min(5, b.readVarInt())));
        }
    }

    /**
     * Preview numbers. Health is in half-hearts, regen in half-hearts per second, duration in
     * seconds; {@code values} is false when the food system is vanilla (only effects apply).
     */
    public record FlavorPreview(ResourceLocation dish, int stars, boolean values, float baseHealth, float health,
                                float baseRegen, float regen, int baseSeconds, int seconds,
                                List<ResourceLocation> effects, List<Integer> levels, List<Integer> effectSeconds,
                                List<ResourceLocation> flavors) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("flavor_preview");
        public FlavorPreview {
            effects = List.copyOf(effects); levels = List.copyOf(levels); effectSeconds = List.copyOf(effectSeconds);
            flavors = List.copyOf(flavors);
        }
        @Override public ResourceLocation id() { return ID; }
        @Override public void write(FriendlyByteBuf b) {
            b.writeResourceLocation(dish); b.writeVarInt(stars); b.writeBoolean(values);
            b.writeFloat(baseHealth); b.writeFloat(health); b.writeFloat(baseRegen); b.writeFloat(regen);
            b.writeVarInt(baseSeconds); b.writeVarInt(seconds);
            b.writeVarInt(effects.size());
            for (int i = 0; i < effects.size(); i++) {
                b.writeResourceLocation(effects.get(i)); b.writeVarInt(levels.get(i)); b.writeVarInt(effectSeconds.get(i));
            }
            b.writeVarInt(flavors.size()); flavors.forEach(b::writeResourceLocation);
        }
        public static FlavorPreview read(FriendlyByteBuf b) {
            ResourceLocation dish = b.readResourceLocation(); int stars = b.readVarInt(); boolean values = b.readBoolean();
            float bh = b.readFloat(), h = b.readFloat(), br = b.readFloat(), r = b.readFloat();
            int bs = b.readVarInt(), sec = b.readVarInt();
            int count = b.readVarInt(); if (count < 0 || count > 64) throw new IllegalArgumentException("Too many effects");
            List<ResourceLocation> effects = new ArrayList<>(); List<Integer> levels = new ArrayList<>(), seconds = new ArrayList<>();
            for (int i = 0; i < count; i++) { effects.add(b.readResourceLocation()); levels.add(b.readVarInt()); seconds.add(b.readVarInt()); }
            return new FlavorPreview(dish, stars, values, bh, h, br, r, bs, sec, effects, levels, seconds, readIds(b));
        }
    }

    public record IngredientView(ResourceLocation item, boolean known, long timesUsed,
                                 long firstUsedDay, long lastUsedDay, int mealCount,
                                 int discoveredCount, byte kind, List<ResourceLocation> types,
                                 boolean flavor, ResourceLocation effect,
                                 List<ResourceLocation> methods, List<ResourceLocation> meals,
                                 boolean tag, List<ResourceLocation> members,
                                 List<ResourceLocation> tagIds, String displayName,
                                 List<IngredientTagSource> tagSources, List<IngredientTagUse> tagUses,
                                 FlavorInfo flavorInfo) {

        public IngredientView {
            flavorInfo = flavorInfo == null ? FlavorInfo.NONE : flavorInfo;
            methods = List.copyOf(methods);
            types = List.copyOf(types);
            meals = List.copyOf(meals);
            members = List.copyOf(members);
            tagIds = List.copyOf(tagIds);
            tagSources = List.copyOf(tagSources);
            tagUses = List.copyOf(tagUses);
        }

        public IngredientView(ResourceLocation item, boolean known, long timesUsed,
                              long firstUsedDay, long lastUsedDay, int mealCount,
                              int discoveredCount, byte kind, List<ResourceLocation> types,
                              boolean flavor, ResourceLocation effect,
                              List<ResourceLocation> methods, List<ResourceLocation> meals,
                              boolean tag, List<ResourceLocation> members,
                              List<ResourceLocation> tagIds, String displayName,
                              List<IngredientTagSource> tagSources, List<IngredientTagUse> tagUses) {
            this(item, known, timesUsed, firstUsedDay, lastUsedDay, mealCount, discoveredCount,
                    kind, types, flavor, effect, methods, meals, tag, members, tagIds, displayName,
                    tagSources, tagUses, FlavorInfo.NONE);
        }

        public IngredientView(ResourceLocation item, boolean known, long timesUsed,
                              long firstUsedDay, long lastUsedDay, int mealCount,
                              int discoveredCount, byte kind, List<ResourceLocation> types,
                              boolean flavor, ResourceLocation effect,
                              List<ResourceLocation> methods, List<ResourceLocation> meals,
                              boolean tag, List<ResourceLocation> members,
                              List<ResourceLocation> tagIds, String displayName) {
            this(item, known, timesUsed, firstUsedDay, lastUsedDay, mealCount, discoveredCount,
                    kind, types, flavor, effect, methods, meals, tag, members, tagIds, displayName,
                    List.of(), List.of());
        }

        public IngredientView(ResourceLocation item, boolean known, long timesUsed,
                              long firstUsedDay, long lastUsedDay, int mealCount,
                              int discoveredCount, byte kind, List<ResourceLocation> types,
                              boolean flavor, ResourceLocation effect,
                              List<ResourceLocation> methods, List<ResourceLocation> meals,
                              boolean tag, List<ResourceLocation> members,
                              List<ResourceLocation> tagIds) {
            this(item, known, timesUsed, firstUsedDay, lastUsedDay, mealCount, discoveredCount,
                    kind, types, flavor, effect, methods, meals, tag, members, tagIds, null);
        }

        public IngredientView(ResourceLocation item, boolean known, long timesUsed,
                              long firstUsedDay, long lastUsedDay, int mealCount,
                              int discoveredCount, byte kind, List<ResourceLocation> types,
                              boolean flavor, ResourceLocation effect,
                              List<ResourceLocation> methods, List<ResourceLocation> meals) {
            this(item, known, timesUsed, firstUsedDay, lastUsedDay, mealCount, discoveredCount,
                    kind, types, flavor, effect, methods, meals, false, List.of(), List.of(), null);
        }

        public IngredientView(ResourceLocation item, boolean known, long timesUsed,
                              long firstUsedDay, long lastUsedDay, int mealCount,
                              int discoveredCount, byte kind, List<ResourceLocation> types,
                              boolean flavor, ResourceLocation effect, List<ResourceLocation> methods) {
            this(item, known, timesUsed, firstUsedDay, lastUsedDay, mealCount, discoveredCount,
                    kind, types, flavor, effect, methods, List.of(), false, List.of(), List.of(), null);
        }

        /** Same entry with a recomputed discovered-dish count. */
        public boolean listed() {
            return tag || !meals.isEmpty();
        }

        public boolean countedIngredient() {
            return !tag && !meals.isEmpty();
        }

        public IngredientView withDiscovered(int count) {
            return new IngredientView(item, known, timesUsed, firstUsedDay, lastUsedDay, mealCount,
                    count, kind, types, flavor, effect, methods, meals, tag, members, tagIds, displayName,
                    tagSources, tagUses, flavorInfo);
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(item);
            buffer.writeBoolean(known);
            buffer.writeVarLong(Math.max(0L, timesUsed));
            buffer.writeLong(firstUsedDay);
            buffer.writeLong(lastUsedDay);
            buffer.writeVarInt(mealCount);
            buffer.writeVarInt(discoveredCount);
            buffer.writeByte(kind);
            buffer.writeVarInt(types.size());
            types.forEach(buffer::writeResourceLocation);
            buffer.writeBoolean(flavor);
            buffer.writeBoolean(effect != null);
            if (effect != null) buffer.writeResourceLocation(effect);
            buffer.writeVarInt(methods.size());
            methods.forEach(buffer::writeResourceLocation);
            buffer.writeVarInt(meals.size());
            meals.forEach(buffer::writeResourceLocation);
            buffer.writeBoolean(tag);
            buffer.writeVarInt(members.size());
            members.forEach(buffer::writeResourceLocation);
            buffer.writeVarInt(tagIds.size());
            tagIds.forEach(buffer::writeResourceLocation);
            buffer.writeBoolean(displayName != null);
            if (displayName != null) buffer.writeUtf(displayName, 256);
            buffer.writeVarInt(tagSources.size()); tagSources.forEach(value -> value.write(buffer));
            buffer.writeVarInt(tagUses.size()); tagUses.forEach(value -> value.write(buffer));
            flavorInfo.write(buffer);
        }

        static IngredientView read(FriendlyByteBuf buffer) {
            ResourceLocation item = buffer.readResourceLocation();
            boolean known = buffer.readBoolean();
            long used = buffer.readVarLong();
            long firstDay = buffer.readLong();
            long lastDay = buffer.readLong();
            int mealCount = buffer.readVarInt();
            int discoveredCount = buffer.readVarInt();
            byte kind = buffer.readByte();
            int typeCount = PacketSafety.count(buffer, 4096);
            List<ResourceLocation> types = new ArrayList<>(typeCount);
            for (int i = 0; i < typeCount; i++) types.add(buffer.readResourceLocation());
            boolean flavor = buffer.readBoolean();
            ResourceLocation effect = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            int methodCount = PacketSafety.count(buffer, 4096);
            List<ResourceLocation> methods = new ArrayList<>(methodCount);
            for (int i = 0; i < methodCount; i++) {
                methods.add(buffer.readResourceLocation());
            }
            List<ResourceLocation> meals = readIds(buffer);
            boolean tag = buffer.readBoolean();
            List<ResourceLocation> members = readIds(buffer);
            List<ResourceLocation> tagIds = readIds(buffer);
            String displayName = buffer.readBoolean() ? buffer.readUtf(256) : null;
            int sourceCount = PacketSafety.count(buffer, 65_536);
            List<IngredientTagSource> sources = new ArrayList<>(sourceCount);
            for (int i = 0; i < sourceCount; i++) sources.add(IngredientTagSource.read(buffer));
            int useCount = PacketSafety.count(buffer, 65_536);
            List<IngredientTagUse> uses = new ArrayList<>(useCount);
            for (int i = 0; i < useCount; i++) uses.add(IngredientTagUse.read(buffer));
            return new IngredientView(item, known, used, firstDay, lastDay, mealCount,
                    discoveredCount, kind, types, flavor, effect, methods, meals, tag, members, tagIds,
                    displayName, sources, uses, FlavorInfo.read(buffer));
        }
    }

    /** One cooking method's statistics. */
    public record MethodView(ResourceLocation method, long preparations, int uniqueMeals,
                             int masteredMeals, ResourceLocation mostPrepared) {

        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(method);
            buffer.writeVarLong(Math.max(0L, preparations));
            buffer.writeVarInt(uniqueMeals);
            buffer.writeVarInt(masteredMeals);
            buffer.writeBoolean(mostPrepared != null);
            if (mostPrepared != null) {
                buffer.writeResourceLocation(mostPrepared);
            }
        }

        static MethodView read(FriendlyByteBuf buffer) {
            ResourceLocation method = buffer.readResourceLocation();
            long preparations = buffer.readVarLong();
            int unique = buffer.readVarInt();
            int mastered = buffer.readVarInt();
            return new MethodView(method, preparations, unique, mastered,
                    buffer.readBoolean() ? buffer.readResourceLocation() : null);
        }
    }

    /**
     * Everything else the journal draws, recomputed server-side.
     *
     * <p>All of it depends on the player's own progress, so it is derived on the
     * server and sent as finished numbers rather than shipping definitions and
     * having the client work them out.</p>
     *
     * @param totalIngredients how many ingredients the installed recipes use in
     *                         total, which is the denominator in "47 / 82"
     */
    public record SyncJournalExtras(List<ChallengeView> challenges,
                                    List<CollectionView> collections,
                                    List<IngredientView> ingredients,
                                    List<MethodView> methods,
                                    List<ActivityView> activity,
                                    int totalIngredients) implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("sync_journal_extras");

        public SyncJournalExtras {
            challenges = List.copyOf(challenges);
            collections = List.copyOf(collections);
            ingredients = List.copyOf(ingredients);
            methods = List.copyOf(methods);
            activity = List.copyOf(activity);
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(challenges.size());
            challenges.forEach(challenge -> challenge.write(buffer));
            buffer.writeVarInt(collections.size());
            collections.forEach(collection -> collection.write(buffer));
            buffer.writeVarInt(ingredients.size());
            ingredients.forEach(ingredient -> ingredient.write(buffer));
            buffer.writeVarInt(methods.size());
            methods.forEach(method -> method.write(buffer));
            buffer.writeVarInt(activity.size());
            activity.forEach(entry -> entry.write(buffer));
            buffer.writeVarInt(totalIngredients);
        }

        public static SyncJournalExtras read(FriendlyByteBuf buffer) {
            int challengeCount = PacketSafety.count(buffer, 4096);
            List<ChallengeView> challenges = new ArrayList<>(challengeCount);
            for (int i = 0; i < challengeCount; i++) {
                challenges.add(ChallengeView.read(buffer));
            }
            int collectionCount = PacketSafety.count(buffer, 4096);
            List<CollectionView> collections = new ArrayList<>(collectionCount);
            for (int i = 0; i < collectionCount; i++) {
                collections.add(CollectionView.read(buffer));
            }
            int ingredientCount = PacketSafety.count(buffer, 65_536);
            List<IngredientView> ingredients = new ArrayList<>(ingredientCount);
            for (int i = 0; i < ingredientCount; i++) {
                ingredients.add(IngredientView.read(buffer));
            }
            int methodCount = PacketSafety.count(buffer, 4096);
            List<MethodView> methods = new ArrayList<>(methodCount);
            for (int i = 0; i < methodCount; i++) {
                methods.add(MethodView.read(buffer));
            }
            int activityCount = PacketSafety.count(buffer, 64);
            List<ActivityView> activity = new ArrayList<>(activityCount);
            for (int i = 0; i < activityCount; i++) {
                activity.add(ActivityView.read(buffer));
            }
            return new SyncJournalExtras(challenges, collections, ingredients, methods,
                    activity, buffer.readVarInt());
        }
    }

    /**
     * One line of the Recent Activity feed.
     *
     * @param stars    the cook's stars at that moment, so hovering the icon can rebuild the
     *                 stamped tooltip of the dish that was actually made
     * @param cookName who cooked it; empty for events that are not preparations
     */
    public record ActivityView(String type, ResourceLocation subject, long detail, long day,
                               int stars, String cookName, long amount, boolean perfect, int starCap,
                               java.util.UUID cookId) {

        public ActivityView(String type, ResourceLocation subject, long detail, long day, int stars, String cookName) {
            this(type, subject, detail, day, stars, cookName, 1, false, 5, null);
        }

        public ActivityView(String type, ResourceLocation subject, long detail, long day) {
            this(type, subject, detail, day, 0, "");
        }

        public ActivityView {
            stars = Math.max(0, Math.min(5, stars));
            cookName = cookName == null ? "" : cookName;
            amount = Math.max(1, amount);
            starCap = Math.max(0, Math.min(5, starCap));
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(type, 64);
            buffer.writeBoolean(subject != null);
            if (subject != null) {
                buffer.writeResourceLocation(subject);
            }
            buffer.writeVarLong(Math.max(0L, detail));
            buffer.writeLong(day);
            buffer.writeVarInt(stars);
            buffer.writeUtf(cookName, 64);
            buffer.writeVarLong(amount);
            buffer.writeBoolean(perfect);
            buffer.writeVarInt(starCap);
            buffer.writeBoolean(cookId != null);
            if (cookId != null) buffer.writeUUID(cookId);
        }

        static ActivityView read(FriendlyByteBuf buffer) {
            String type = buffer.readUtf(64);
            ResourceLocation subject = buffer.readBoolean() ? buffer.readResourceLocation() : null;
            return new ActivityView(type, subject, buffer.readVarLong(), buffer.readLong(),
                    buffer.readVarInt(), buffer.readUtf(64), buffer.readVarLong(), buffer.readBoolean(),
                    buffer.readVarInt(), buffer.readBoolean() ? buffer.readUUID() : null);
        }
    }

    /**
     * Tells the client whether the screen it currently has open is a cooking
     * surface.
     *
     * <p>The server already works this out for attribution - it is the same
     * check that decides whether taking an item counts as cooking - so the
     * client is told rather than guessing. That is what makes the stats panel
     * appear beside a Cooking Pot and any addon workstation, but not beside a
     * chest or the plain inventory.</p>
     */
    public record MenuContext(boolean culinary, ResourceLocation method)
            implements CuisineJournalPacket {

        public static final ResourceLocation ID = packetId("menu_context");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeBoolean(culinary);
            buffer.writeBoolean(method != null);
            if (method != null) {
                buffer.writeResourceLocation(method);
            }
        }

        public static MenuContext read(FriendlyByteBuf buffer) {
            boolean culinary = buffer.readBoolean();
            return new MenuContext(culinary,
                    buffer.readBoolean() ? buffer.readResourceLocation() : null);
        }
    }

    // ------------------------------------------------------- client to server


    public record RequestDishDetail(ResourceLocation target) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("request_dish_detail");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(target);
        }

        public static RequestDishDetail read(FriendlyByteBuf buffer) {
            return new RequestDishDetail(buffer.readResourceLocation());
        }
    }

    /** Asks for a fresh full profile; sent when the journal is opened. */
    public record RequestProfile() implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("request_profile");

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
        }

        public static RequestProfile read(FriendlyByteBuf buffer) {
            return new RequestProfile();
        }
    }

    /**
     * A personal preference, not a progression claim: the server stores the
     * flag and echoes back the authoritative value.
     */
    public record SetPreference(Kind kind, ResourceLocation target) implements CuisineJournalPacket {
        public static final ResourceLocation ID = packetId("set_preference");

        public enum Kind {
            FAVORITE, PIN, BADGE, SIGNATURE;

            private static final Kind[] VALUES = values();

            static Kind byOrdinal(int ordinal) {
                return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : FAVORITE;
            }
        }

        @Override
        public ResourceLocation id() {
            return ID;
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(kind.ordinal());
            buffer.writeBoolean(target != null);
            if (target != null) {
                buffer.writeResourceLocation(target);
            }
        }

        public static SetPreference read(FriendlyByteBuf buffer) {
            Kind kind = Kind.byOrdinal(buffer.readVarInt());
            return new SetPreference(kind,
                    buffer.readBoolean() ? buffer.readResourceLocation() : null);
        }
    }

    private static final int MAX_VERSION_ENTRIES = 65536;
    private static final int MAX_VERSION_KEY_LENGTH = 512;

    private static void validateVersions(Map<String, Long> versions) {
        if (versions.size() > MAX_VERSION_ENTRIES) throw new IllegalArgumentException("Too many version entries");
        versions.forEach((key, version) -> {
            if (key == null || key.length() > MAX_VERSION_KEY_LENGTH) throw new IllegalArgumentException("Invalid version key");
            if (version == null || version < 0) throw new IllegalArgumentException("Invalid version value");
        });
    }

    private static Map<String, Long> copyVersions(Map<String, Long> versions) {
        validateVersions(versions);
        return Map.copyOf(versions);
    }

    private static void writeVersions(FriendlyByteBuf buffer, Map<String, Long> versions) {
        validateVersions(versions);
        buffer.writeVarInt(versions.size());
        versions.forEach((key, version) -> {
            buffer.writeUtf(key, MAX_VERSION_KEY_LENGTH);
            buffer.writeLong(version);
        });
    }

    private static Map<String, Long> readVersions(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_VERSION_ENTRIES) throw new IllegalArgumentException("Invalid version entry count");
        Map<String, Long> versions = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String key = buffer.readUtf(MAX_VERSION_KEY_LENGTH);
            long version = buffer.readLong();
            if (version < 0) throw new IllegalArgumentException("Invalid version value");
            if (versions.putIfAbsent(key, version) != null) throw new IllegalArgumentException("Duplicate version key");
        }
        return versions;
    }

    private static List<ResourceLocation> readIds(FriendlyByteBuf buffer) {
        int count = PacketSafety.count(buffer, 65_536);
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(buffer.readResourceLocation());
        }
        return ids;
    }
}
