package com.chi__you.cuisinejournal.client;

import com.chi__you.cuisinejournal.culinary.LevelCurve;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;
import com.chi__you.cuisinejournal.mastery.MasteryRank;
import com.chi__you.cuisinejournal.mastery.CategoryMastery;
import com.chi__you.cuisinejournal.network.Packets;
import com.chi__you.cuisinejournal.network.JournalSyncPackets;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the client knows, and nothing more.
 *
 * <p>Pure data: no Minecraft client class is touched here, so the dedicated
 * server can safely load this class even though nothing on it ever will. The
 * screens read from it; the packet handler writes to it.</p>
 *
 * <p>This is a cache of what the server said, never a source of truth. Toggling
 * a favourite optimistically here would only be overwritten by the server's
 * echo, so it does not.</p>
 */
public final class ClientJournalState {
    private static com.chi__you.cuisinejournal.network.JournalFeaturePackets.Sync journalFeatures;
    public static com.chi__you.cuisinejournal.network.JournalFeaturePackets.Sync journalFeatures(){return journalFeatures;}
    public static void acceptJournalFeatures(com.chi__you.cuisinejournal.network.JournalFeaturePackets.Sync value){journalFeatures=value;revision++;}
    private static com.chi__you.cuisinejournal.skills.SkillPackets.Sync skills;
    public static com.chi__you.cuisinejournal.skills.SkillPackets.Sync skills() { return skills; }
    public static void acceptSkills(com.chi__you.cuisinejournal.skills.SkillPackets.Sync value) { skills=value;revision++; }
    private static MasteryRank skillLimit(MasteryRank rank) {
        return skills!=null&&skills.active()?MasteryRank.byIndex(Math.min(rank.ordinal(),skills.starCap())):rank;
    }

    private static final Map<ResourceLocation, Packets.DishView> CATALOGUE = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Packets.RecordView> RECORDS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, CategoryProgress> CATEGORY_PROGRESS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Packets.SyncDishDetail> DETAILS = new LinkedHashMap<>();
    private static final Deque<Packets.Notify> PENDING_NOTIFICATIONS = new ArrayDeque<>();

    private record CategoryProgress(long preparations, int discovered, int total) {
    }

    private static volatile boolean catalogueComplete;
    private static volatile long revision;
    private static volatile long detailGeneration;
    public static long detailGeneration() { return detailGeneration; }

    /** Monotonic client snapshot version for page caches, including same-sized updates. */
    public static long revision() { return revision; }
    private static volatile long cookingXp;
    private static volatile long mealsPrepared;
    private static volatile long mealsEaten;
    private static volatile long portionsServed;
    private static volatile int ingredientsDiscovered;
    private static volatile int cookingStreak;
    private static volatile int varietyStreak;
    private static volatile List<ResourceLocation> badges = List.of();
    private static volatile List<ResourceLocation> milestones = List.of();

    private static volatile List<Packets.ChallengeView> challenges = List.of();
    private static volatile List<Packets.CollectionView> collections = List.of();
    private static volatile List<Packets.IngredientView> ingredients = List.of();
    private static volatile List<Packets.MethodView> methods = List.of();
    private static volatile List<Packets.ActivityView> activity = List.of();
    private static volatile int totalIngredients;

    /** Whether the container screen currently open is a cooking surface. */
    private static volatile boolean menuCulinary;
    private static volatile boolean categoryEditPermission;
    private static volatile boolean categoryEditing;
    private static volatile Packets.CategoryEditResult lastCategoryEditResult;
    private static volatile Packets.SyncCategoryEditor categoryEditor = new Packets.SyncCategoryEditor(List.of(), List.of(), Map.of());
    public static Packets.SyncCategoryEditor categoryEditor() { return categoryEditor; }
    public static void acceptCategoryEditor(Packets.SyncCategoryEditor packet) {
        categoryEditor = packet; EditClientState.accept(packet.versions()); revision++;
    }
    private static volatile ResourceLocation menuMethod;

    private static volatile LevelCurve levelCurve = LevelCurve.DEFAULT;
    private static volatile MasteryCurve masteryCurve = MasteryCurve.DEFAULT;
    private static volatile String masteryRewards = "COSMETIC_ONLY";
    private static volatile boolean leaderboardsEnabled;
    private static volatile Packets.SyncRules rules;
    private static volatile Packets.SyncTodayDish todayDish;
    public static Packets.SyncTodayDish todayDish(){return todayDish;}
    private static volatile Packets.SyncServerSettings serverSettings;
    public static void acceptServerSettings(Packets.SyncServerSettings value){serverSettings=value;revision++;}
    /** Server rules for the settings screen, or null before the server has sent them. */
    public static Packets.SyncServerSettings serverSettings(){return serverSettings;}
    public static void acceptTodayDish(Packets.SyncTodayDish value){todayDish=value;revision++;}

    private ClientJournalState() {
    }

    // ------------------------------------------------------------- catalogue

    public static synchronized void acceptCatalogue(Packets.SyncCatalogue packet) {
        if (packet.first()) {
            CATALOGUE.clear();
            DETAILS.clear();
            detailGeneration++;
            catalogueComplete = false;
        }
        for (Packets.DishView dish : packet.dishes()) {
            CATALOGUE.put(dish.target(), dish);
        }
        if (packet.last()) {
            catalogueComplete = true;
        }
        rebuildCategoryProgress();
        refreshIngredientDiscovery();
        revision++;
    }

    public static boolean isCatalogueComplete() {
        return catalogueComplete;
    }

    public static synchronized List<Packets.DishView> dishes() {
        return List.copyOf(CATALOGUE.values());
    }

    public static synchronized Packets.DishView dish(ResourceLocation target) {
        return CATALOGUE.get(target);
    }

    public static synchronized int dishCount() {
        return CATALOGUE.size();
    }

    // --------------------------------------------------------------- profile

    public static synchronized void acceptProfile(Packets.SyncProfile packet) {
        if (packet.full()) {
            RECORDS.clear();
        }
        for (Packets.RecordView record : packet.records()) {
            RECORDS.put(record.target(), record);
        }
        cookingXp = packet.cookingXp();
        mealsPrepared = packet.mealsPrepared();
        mealsEaten = packet.mealsEaten();
        portionsServed = packet.portionsServed();
        ingredientsDiscovered = packet.ingredientsDiscovered();
        cookingStreak = packet.cookingStreak();
        varietyStreak = packet.varietyStreak();
        badges = List.copyOf(packet.badges());
        milestones = List.copyOf(packet.milestones());
        rebuildCategoryProgress();
        refreshIngredientDiscovery();
        revision++;
    }

    public static synchronized Packets.RecordView record(ResourceLocation target) {
        Packets.RecordView own=RECORDS.get(target);
        ResourceLocation source=preparationTarget(target);
        if(source.equals(target))return own;
        Packets.RecordView prepared=RECORDS.get(source);
        if(prepared==null&&own==null)return null;
        boolean discovered=own!=null&&own.discovered()||prepared!=null&&prepared.discovered();
        return new Packets.RecordView(target,discovered,prepared==null?0:prepared.prepared(),own==null?0:own.eaten(),
                own==null?0:own.served(),own!=null&&own.favorite(),own!=null&&own.pinned(),
                prepared==null?Long.MIN_VALUE:prepared.firstPreparedDay(),prepared==null?Long.MIN_VALUE:prepared.lastPreparedDay(),
                prepared==null?0:prepared.masteryPoints(),prepared==null?0:prepared.bonusMasteryPoints(),prepared==null?-1:prepared.highestBrewedStars());
    }

    public static synchronized ResourceLocation preparationTarget(ResourceLocation target) {
        Packets.DishView dish=CATALOGUE.get(target);
        return dish==null?target:dish.preparationTarget();
    }

    public static synchronized List<Packets.RecordView> records() {
        return List.copyOf(RECORDS.values());
    }

    public static long cookingXp() {
        return cookingXp;
    }

    public static int cookingLevel() {
        return levelCurve.levelForXp(cookingXp);
    }

    public static long xpIntoLevel() {
        return levelCurve.xpIntoLevel(cookingXp);
    }

    public static long xpForCurrentLevel() {
        return levelCurve.xpToNextLevel(cookingXp);
    }

    public static long mealsPrepared() {
        return mealsPrepared;
    }

    public static long mealsEaten() {
        return mealsEaten;
    }

    public static long portionsServed() {
        return portionsServed;
    }

    public static int ingredientsDiscovered() {
        return (int) ingredients.stream().filter(Packets.IngredientView::countedIngredient)
                .filter(Packets.IngredientView::known).count();
    }

    public static int cookingStreak() {
        return cookingStreak;
    }

    public static int varietyStreak() {
        return varietyStreak;
    }

    public static List<ResourceLocation> badges() {
        return badges;
    }

    public static List<ResourceLocation> milestones() {
        return milestones;
    }

    private static volatile Packets.SyncStatistics statistics;

    public static void acceptStatistics(Packets.SyncStatistics packet) {
        statistics = packet;
        revision++;
    }

    /** Personal records, streaks and achievements; null until the server has sent them. */
    public static Packets.SyncStatistics statistics() {
        return statistics;
    }

    public static synchronized int discoveredCount() {
        int count = 0;
        for (ResourceLocation item : CATALOGUE.keySet()) {
            if (isDiscovered(item)) {
                count++;
            }
        }
        return count;
    }

    public static synchronized int masteredCount() {
        int count = 0;
        for (ResourceLocation target : CATALOGUE.keySet()) {
            if (rank(target).isMastered()) {
                count++;
            }
        }
        return count;
    }

    public static synchronized List<ResourceLocation> pinned() {
        List<ResourceLocation> result = new ArrayList<>();
        RECORDS.forEach((target, record) -> {
            if (record.pinned()) {
                result.add(target);
            }
        });
        return result;
    }

    public static boolean isDiscovered(ResourceLocation target) {
        Packets.RecordView record = record(target);
        return record != null && record.discovered();
    }

    /** True when stars belong to each dish rather than to its subcategory (server config). */
    public static boolean perDishMastery() {
        return rules != null && rules.perDishMastery();
    }

    /**
     * Star collection for the overview: {earned, maximum, full}. In subcategory scope these
     * count subcategories (5 stars each); in per-dish scope, dishes.
     */
    public static synchronized int[] starSummary() {
        int earned = 0, max = 0, full = 0, top = MasteryRank.highestIndex();
        if (perDishMastery()) {
            for (ResourceLocation dish : CATALOGUE.keySet()) {
                int rank = rank(dish).ordinal();
                earned += rank;
                if (rank >= top) full++;
            }
            max = CATALOGUE.size() * top;
        } else {
            for (CategoryProgress progress : CATEGORY_PROGRESS.values()) {
                int rank = skillLimit(CategoryMastery.rank(progress.preparations(), progress.discovered(),
                        progress.total(), masteryCurve)).ordinal();
                earned += rank;
                if (rank >= top) full++;
            }
            max = CATEGORY_PROGRESS.size() * top;
        }
        return new int[]{earned, max, full};
    }

    /** True when the server runs vanilla hunger instead of Cuisine Journal's food system. */
    public static boolean vanillaFood() {
        return rules != null && rules.vanillaFood();
    }

    public static synchronized MasteryRank rank(ResourceLocation target) {
        if (rules != null && !rules.starsEnabled()) return MasteryRank.values()[2];
        Packets.RecordView record = record(target);
        if (record == null || !record.discovered()) {
            return MasteryRank.UNFAMILIAR;
        }
        if(record.highestBrewedStars()>=0)return MasteryRank.byIndex(record.highestBrewedStars());
        if (perDishMastery()) return skillLimit(masteryCurve.rankFor(categoryMasteryPoints(target)));
        Packets.DishView dish = dish(target);
        if (dish == null) {
            return MasteryRank.UNFAMILIAR;
        }
        CategoryProgress progress = CATEGORY_PROGRESS.get(dish.subcategory());
        return progress == null ? MasteryRank.UNFAMILIAR
                : skillLimit(CategoryMastery.rank(progress.preparations(), progress.discovered(),
                progress.total(), masteryCurve));
    }

    /** Points behind {@link #rank}: the dish's own mastery points, or its subcategory's preparations. */
    public static synchronized long categoryMasteryPoints(ResourceLocation target) {
        if (perDishMastery()) {
            return com.chi__you.cuisinejournal.recipe.LatiaoMastery.points(target,id->{
                Packets.RecordView record=record(id);return record==null?0L:record.masteryPoints();
            });
        }
        Packets.DishView dish = dish(target);
        if (dish == null) {
            return 0L;
        }
        CategoryProgress progress = CATEGORY_PROGRESS.get(dish.subcategory());
        return progress == null ? 0L : progress.preparations();
    }

    public static synchronized int categoryRankCap(ResourceLocation target) {
        int limit=skills!=null&&skills.active()?skills.starCap():MasteryRank.highestIndex();
        if (perDishMastery()) return isDiscovered(target) ? limit : 0;
        Packets.DishView dish = dish(target);
        CategoryProgress progress = dish == null ? null : CATEGORY_PROGRESS.get(dish.subcategory());
        return progress == null ? 0
                : Math.min(limit,CategoryMastery.rankCap(progress.discovered(), progress.total()));
    }

    private static void rebuildCategoryProgress() {
        CATEGORY_PROGRESS.clear();
        java.util.Set<String> counted=new java.util.HashSet<>();
        for (Packets.DishView dish : CATALOGUE.values()) {
            Packets.RecordView record = record(dish.target());
            CategoryProgress previous = CATEGORY_PROGRESS.getOrDefault(dish.subcategory(),
                    new CategoryProgress(0L, 0, 0));
            CATEGORY_PROGRESS.put(dish.subcategory(), new CategoryProgress(
                    previous.preparations() + (record == null || !counted.add(dish.subcategory()+"|"+dish.preparationTarget()) ? 0L : record.prepared()+record.bonusMasteryPoints()),
                    previous.discovered() + (record != null && record.discovered() ? 1 : 0),
                    previous.total() + 1));
        }
    }

    // ----------------------------------------------------------------- rules

    /** The server's bonus numbers, so tooltips can state real odds. */
    public static Packets.SyncRules rules() {
        return rules;
    }

    public static void acceptRules(Packets.SyncRules packet) {
        rules = packet;
        levelCurve = new LevelCurve(packet.levelBase(), packet.levelLinear(),
                packet.levelQuadratic(), packet.maxLevel());
        masteryCurve = new MasteryCurve(packet.masteryThresholds());
        masteryRewards = packet.masteryRewards();
        leaderboardsEnabled = packet.leaderboardsEnabled();
        revision++;
    }

    public static LevelCurve levelCurve() {
        return levelCurve;
    }

    public static MasteryCurve masteryCurve() {
        return masteryCurve;
    }

    public static String masteryRewards() {
        return masteryRewards;
    }

    public static boolean leaderboardsEnabled() {
        return leaderboardsEnabled;
    }

    // ---------------------------------------------------------------- extras

    private static JournalSyncPackets.Directory pendingDirectory;
    private static long directoryGeneration, progressSequence;
    private static boolean directoryReady, directoryFailed;
    private static long retryAfter;
    private static long syncStarted;
    private static final java.util.Set<ResourceLocation> TAG_DETAILS_LOADED = new java.util.HashSet<>();
    private static final Map<ResourceLocation, Long> TAG_DETAILS_REQUESTED = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> TAG_DETAILS_ATTEMPTS = new LinkedHashMap<>();
    private static int syncRetries;
    public static boolean directoryReady() { return directoryReady; }
    public static boolean directoryFailed() { return directoryFailed; }

    public static void beginJournalSync() {
        if ((!directoryReady || !catalogueComplete) && syncStarted == 0) syncStarted = System.nanoTime() / 1_000_000;
        if (directoryFailed) { syncRetries = 0; retryAfter = 0; }
    }

    public static void syncFailed(ResourceLocation id) {
        if (!id.equals(JournalSyncPackets.Directory.ID) && !id.equals(JournalSyncPackets.Progress.ID)
                && !id.equals(Packets.SyncCatalogue.ID) && !id.equals(Packets.SyncJournalExtras.ID)) return;
        pendingDirectory = null; directoryFailed = true; revision++;
    }

    public static void tickSync() {
        long now = System.nanoTime() / 1_000_000;
        if ((!directoryReady || !catalogueComplete) && !directoryFailed && syncStarted != 0 && now - syncStarted >= 30_000)
            syncFailed(JournalSyncPackets.Directory.ID);
        if (!directoryFailed || syncRetries >= 3 || now < retryAfter) return;
        retryAfter = now + 3000; syncRetries++;
        com.chi__you.cuisinejournal.network.Network.toServer(new JournalSyncPackets.Request());
    }

    public static void acceptDirectory(JournalSyncPackets.Directory packet) {
        if (packet.generation() < directoryGeneration || pendingDirectory != null
                && packet.generation() < pendingDirectory.generation()) return;
        pendingDirectory = packet;
    }

    public static void requestTagDetails(Packets.IngredientView value) {
        if (value == null || !value.tag() || !directoryReady || directoryFailed || pendingDirectory != null
                || TAG_DETAILS_LOADED.contains(value.item())) return;
        long now = System.nanoTime() / 1_000_000;
        Long requested = TAG_DETAILS_REQUESTED.get(value.item());
        int attempts = TAG_DETAILS_ATTEMPTS.getOrDefault(value.item(), 0);
        if (attempts >= 3 || requested != null && now - requested < 3000) return;
        TAG_DETAILS_REQUESTED.put(value.item(), now); TAG_DETAILS_ATTEMPTS.put(value.item(), attempts + 1);
        com.chi__you.cuisinejournal.network.Network.toServer(new JournalSyncPackets.TagRequest(directoryGeneration, value.item()));
    }

    public static void acceptTagDetails(JournalSyncPackets.TagDetails packet) {
        if (!directoryReady || pendingDirectory != null || packet.generation() != directoryGeneration) return;
        boolean exists = ingredients.stream().anyMatch(value -> value.tag() && value.item().equals(packet.tag()));
        if (!exists) return;
        ingredients = ingredients.stream().map(value -> value.tag() && value.item().equals(packet.tag())
                ? packet.apply(value) : value).toList();
        TAG_DETAILS_LOADED.add(packet.tag()); TAG_DETAILS_REQUESTED.remove(packet.tag());
        TAG_DETAILS_ATTEMPTS.remove(packet.tag()); revision++;
    }

    public static void acceptProgress(JournalSyncPackets.Progress packet) {
        boolean replacing = pendingDirectory != null && pendingDirectory.generation() == packet.generation();
        if (packet.generation() < directoryGeneration || pendingDirectory != null
                && packet.generation() < pendingDirectory.generation()) return;
        if (replacing && !packet.full() || !replacing && (packet.generation() != directoryGeneration || !directoryReady)) {
            syncFailed(JournalSyncPackets.Progress.ID); return;
        }
        if (!replacing && packet.sequence() <= progressSequence) return;
        if (!replacing && !packet.full() && packet.sequence() != progressSequence + 1) {
            syncFailed(JournalSyncPackets.Progress.ID); return;
        }
        var baseIngredients = replacing ? pendingDirectory.ingredients() : ingredients;
        var baseCollections = replacing ? pendingDirectory.collections() : collections;
        Map<JournalSyncPackets.IngredientKey, JournalSyncPackets.IngredientProgress> ingredientChanges = new LinkedHashMap<>();
        for (var value : packet.ingredients()) ingredientChanges.put(value.key(), value);
        Map<ResourceLocation, JournalSyncPackets.CollectionProgress> collectionChanges = new LinkedHashMap<>();
        for (var value : packet.collections()) collectionChanges.put(value.id(), value);
        var nextIngredients = baseIngredients.stream().map(value -> {
            var change = ingredientChanges.get(new JournalSyncPackets.IngredientKey(value.item(), value.tag()));
            return change == null ? value : change.apply(value);
        }).toList();
        var nextCollections = baseCollections.stream().map(value -> {
            var change = collectionChanges.get(value.id()); return change == null ? value : change.apply(value);
        }).toList();
        ingredients = nextIngredients; collections = nextCollections;
        if (replacing) {
            totalIngredients = pendingDirectory.totalIngredients();
            TAG_DETAILS_LOADED.clear(); TAG_DETAILS_REQUESTED.clear(); TAG_DETAILS_ATTEMPTS.clear();
        }
        pendingDirectory = null; directoryGeneration = packet.generation(); progressSequence = packet.sequence();
        challenges = packet.challenges(); methods = packet.methods(); activity = packet.activity();
        challengeReceivedTick = challengeClientTick;
        directoryReady = true; directoryFailed = false; syncRetries = 0;
        if (catalogueComplete) syncStarted = 0;
        refreshIngredientDiscovery(); revision++;
    }

    public static void acceptExtras(Packets.SyncJournalExtras packet) {
        directoryReady = true;
        challenges = List.copyOf(packet.challenges());
        challengeReceivedTick=challengeClientTick;
        collections = List.copyOf(packet.collections());
        ingredients = List.copyOf(packet.ingredients());
        refreshIngredientDiscovery();
        methods = List.copyOf(packet.methods());
        activity = List.copyOf(packet.activity());
        totalIngredients = packet.totalIngredients();
        revision++;
    }

    private static long challengeReceivedTick;
    private static long challengeClientTick;
    public static void tickChallenges(){challengeClientTick++;}
    public static long challengeRemaining(Packets.ChallengeView challenge){
        return challenge.remainingTicks()<0?-1:Math.max(0,challenge.remainingTicks()-
                Math.max(0,challengeClientTick-challengeReceivedTick));
    }

    private static void refreshIngredientDiscovery() {
        ingredients = ingredients.stream().map(value -> value.withDiscovered(
                (int) value.meals().stream().filter(ClientJournalState::isDiscovered).count())).toList();
    }

    public static List<Packets.ChallengeView> challenges() {
        return challenges;
    }

    public static List<Packets.CollectionView> collections() {
        return collections;
    }

    public static List<Packets.IngredientView> ingredients() {
        return ingredients;
    }

    public static List<Packets.MethodView> methods() {
        return methods;
    }

    public static int totalIngredients() {
        return totalIngredients;
    }

    public static List<Packets.ActivityView> activity() {
        return activity;
    }

    public static void acceptMenuContext(Packets.MenuContext context) {
        menuCulinary = context.culinary();
        menuMethod = context.method();
    }

    public static boolean isMenuCulinary() {
        return menuCulinary;
    }

    public static boolean canEditCategories() { return categoryEditPermission; }
    public static boolean categoryEditing() { return categoryEditing; }
    public static void acceptCategoryEditMode(Packets.SyncCategoryEditMode packet) {
        categoryEditing = packet.enabled(); revision++;
    }
    public static Packets.CategoryEditResult lastCategoryEditResult() { return lastCategoryEditResult; }
    public static void acceptCategoryPermissions(Packets.SyncCategoryPermissions packet) {
        categoryEditPermission = packet.canEdit(); revision++;
    }
    public static void acceptCategoryEditResult(Packets.CategoryEditResult result) {
        lastCategoryEditResult = result; revision++;
    }

    public static ResourceLocation menuMethod() {
        return menuMethod;
    }

    // ---------------------------------------------------------------- detail

    public static synchronized void acceptDetail(Packets.SyncDishDetail detail) {
        DETAILS.put(detail.target(), detail);
        revision++;
    }

    public static synchronized Packets.SyncDishDetail detail(ResourceLocation target) {
        return DETAILS.get(target);
    }

    // --------------------------------------------------------- notifications

    public static synchronized void queueNotification(Packets.Notify notify) {
        // Bounded so a burst of XP gains cannot grow the queue without limit.
        if (PENDING_NOTIFICATIONS.size() < 64) {
            PENDING_NOTIFICATIONS.addLast(notify);
        }
    }

    public static synchronized Packets.Notify pollNotification() {
        return PENDING_NOTIFICATIONS.pollFirst();
    }

    public static synchronized List<Packets.Notify> drainNotifications() {
        List<Packets.Notify> drained = new ArrayList<>(PENDING_NOTIFICATIONS);
        PENDING_NOTIFICATIONS.clear();
        return Collections.unmodifiableList(drained);
    }

    /** Called on disconnect so a second world never inherits the first one's journal. */
    public static synchronized void clear() {
        pendingDirectory = null; directoryGeneration = 0; progressSequence = 0;
        directoryReady = false; directoryFailed = false; retryAfter = 0; syncRetries = 0;
        syncStarted = 0;
        TAG_DETAILS_LOADED.clear(); TAG_DETAILS_REQUESTED.clear(); TAG_DETAILS_ATTEMPTS.clear();
        journalFeatures=null;
        skills=null;
        todayDish=null;
        CATALOGUE.clear();
        RECORDS.clear();
        CATEGORY_PROGRESS.clear();
        DETAILS.clear();
        detailGeneration++;
        PENDING_NOTIFICATIONS.clear();
        catalogueComplete = false;
        cookingXp = 0L;
        mealsPrepared = 0L;
        mealsEaten = 0L;
        portionsServed = 0L;
        ingredientsDiscovered = 0;
        cookingStreak = 0;
        varietyStreak = 0;
        badges = List.of();
        milestones = List.of();
        statistics = null;
        challenges = List.of();
        collections = List.of();
        ingredients = List.of();
        methods = List.of();
        activity = List.of();
        totalIngredients = 0;
        menuCulinary = false;
        categoryEditPermission = false;
        categoryEditing = false;
        lastCategoryEditResult = null;
        EditClientState.clear();
        FlavorClientState.clear();
        SchemeClientState.clear();
        categoryEditor = new Packets.SyncCategoryEditor(List.of(), List.of(), Map.of());
        menuMethod = null;
        levelCurve = LevelCurve.DEFAULT;
        masteryCurve = MasteryCurve.DEFAULT;
        masteryRewards = "COSMETIC_ONLY";
        leaderboardsEnabled = false;
        rules = null;
        revision++;
    }
}
