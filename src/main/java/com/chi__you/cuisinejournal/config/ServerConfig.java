package com.chi__you.cuisinejournal.config;

import com.chi__you.cuisinejournal.config.ConfigEnums.AutomationCredit;
import com.chi__you.cuisinejournal.config.ConfigEnums.DiscoveryMode;
import com.chi__you.cuisinejournal.config.ConfigEnums.MasteryRewards;
import com.chi__you.cuisinejournal.config.ConfigEnums.SharedDiscovery;
import com.chi__you.cuisinejournal.config.ConfigEnums.StreakClock;
import com.chi__you.cuisinejournal.culinary.LevelCurve;
import com.chi__you.cuisinejournal.mastery.MasteryCurve;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative settings.
 *
 * <p>Plain mutable fields because this is a Gson document; {@link #validate()}
 * is what makes it safe, clamping nonsense rather than refusing to load. A
 * config file a player hand-edited into an impossible state must still boot the
 * server.</p>
 *
 * <p>These settings control the journal and cooking progression. The food-slot
 * runtime has its own common configuration.</p>
 */
public final class ServerConfig {

    public Progression progression = new Progression();
    public AntiFarming antiFarming = new AntiFarming();
    public SocialRewards socialRewards = new SocialRewards();
    public Mastery mastery = new Mastery();
    public Discovery discovery = new Discovery();
    public FoodEffects foodEffects = new FoodEffects();
    public Flavors flavors = new Flavors();
    public NutritionDisplay nutritionDisplay = new NutritionDisplay();
    public FoodModeDefaults foodModeDefaults = new FoodModeDefaults();
    public Schemes schemes = new Schemes();
    public Streaks streaks = new Streaks();
    public Challenges challenges = new Challenges();
    public TodayDish todayDish = new TodayDish();
    public Multiplayer multiplayer = new Multiplayer();
    public Automation automation = new Automation();
    public Compatibility compatibility = new Compatibility();
    public Advanced advanced = new Advanced();
    public SignatureDish signatureDish = new SignatureDish();
    public static final class SignatureDish {
        @Comment("Flat perfect probability bonus for the player's personally prepared and currently mastered signature dish.")
        public double perfectChanceBonus=.10;
        @Comment("Vanilla experience levels charged for replacing or clearing a previously chosen signature. First choice is free.")
        public int changeExperienceLevels=5;
    }

    @Comment({
            "Cooking XP and the level curve.",
            "xpForLevel(n) = base + linear*(n-1) + quadratic*(n-1)^2"})
    public static final class Progression {
        @Comment("Set false to stop awarding Cooking XP entirely.")
        public boolean enabled = true;
        @Comment("Multiplies all Cooking XP. Range 0.0 - 100.0.")
        public double xpMultiplier = 1.0;

        @Comment("Cooking XP for preparing a dish that has been made before.")
        public int baseXpPerPreparation = 6;
        @Comment({
                "Extra XP the first time a dish is ever prepared.",
                "Deliberately far larger than a repeat: exploring should beat grinding."})
        public int discoveryBonusXp = 75;
        @Comment("Extra XP the first time an ingredient is ever used.")
        public int ingredientDiscoveryXp = 10;
        @Comment("Extra XP the first time a cooking method is ever used.")
        public int methodDiscoveryXp = 25;
        @Comment("Extra XP for an ingredient variant never used in that dish before.")
        public int experimentationXp = 8;
        @Comment("Cap on experimentation XP per Minecraft day, so variants cannot be farmed.")
        public int experimentationDailyCap = 60;
        @Comment("Limit experimentation XP per Minecraft day. False removes only this cap.")
        public boolean experimentationDailyLimit = true;
        @Comment("XP for personally eating a dish. Kept far below preparing it.")
        public int baseXpPerMeal = 1;
        @Comment("Cooking XP paid to the recorded cook each time another player eats their food.")
        public int baseXpPerMealServedToOthers = 8;

        @Comment("Level curve. With the defaults, level 27 costs 1,738 XP.")
        public int levelCurveBase = LevelCurve.DEFAULT_BASE;
        public int levelCurveLinear = LevelCurve.DEFAULT_LINEAR;
        public int levelCurveQuadratic = LevelCurve.DEFAULT_QUADRATIC;
        @Comment("Levelling stops here. Earned XP past it is kept, not discarded.")
        public int maxLevel = LevelCurve.DEFAULT_MAX_LEVEL;

        public LevelCurve toCurve() {
            return new LevelCurve(levelCurveBase, levelCurveLinear, levelCurveQuadratic, maxLevel);
        }
    }

    /**
     * Diminishing returns.
     *
     * <p>Cooking one cheap dish ten thousand times has to be a bad strategy, but
     * it must never become a punishment either — the floor multiplier keeps
     * repetition worth something.</p>
     */
    @Comment({
            "Diminishing returns for repeating the same dish.",
            "Repetition should be worth less, never worthless."})
    public static final class AntiFarming {
        @Comment("Set false to remove the curve entirely; every preparation pays full XP.")
        public boolean enabled = true;
        @Comment("Preparations per period that still earn full XP.")
        public int fullXpPreparations = 1;
        @Comment("Never drop below this multiplier, however often a dish is repeated.")
        public double minimumMultiplier = 0.2;

        @Comment({
                "Multiplier tiers applied once the full-XP preparations are used up.",
                "Each entry applies while the period count is at most upToPreparations.",
                "Add or remove [[antiFarming.tiers]] blocks to reshape the curve.",
                "To remove every tier use tiers = []; an absent key inherits the defaults."})
        public List<Tier> tiers = defaultTiers();

        public static final class Tier {
            @Comment("Applies while the period count is at most this many.")
            public int upToPreparations;
            @Comment("XP multiplier while this tier applies.")
            public double multiplier;

            public Tier() {
            }

            public Tier(int upToPreparations, double multiplier) {
                this.upToPreparations = upToPreparations;
                this.multiplier = multiplier;
            }
        }

        private static List<Tier> defaultTiers() {
            List<Tier> tiers = new ArrayList<>();
            tiers.add(new Tier(5, 0.75));
            tiers.add(new Tier(15, 0.50));
            return tiers;
        }
    }

    @Comment("Star progression by subcategory or dish, and its rewards.")
    public static final class Mastery {
        @Comment("Star system master switch. Stops awarding mastery and perfect products; food uses neutral two-star values. Existing history is kept.")
        public boolean enabled = true;
        @Comment({
                "What star ranks belong to. SUBCATEGORY or DISH.",
                "SUBCATEGORY: each food subcategory has 0-5 stars; every dish in it adds to the same",
                "  progress (mastery.thresholds), and discovering more of its dishes raises the star cap.",
                "DISH: every dish has its own 0-5 stars from its own mastery points",
                "  (pointsPerPreparation / pointsPerServing / ...), measured against mastery.dishThresholds.",
                "Switching keeps all saved data; only which number is shown and rewarded changes."})
        public ConfigEnums.MasteryScope scope = ConfigEnums.MasteryScope.DISH;
        @Comment({
                "How mastery shapes Cooking XP. This is separate from mastery.bonuses,",
                "which is what mastery does to the food itself.",
                "COSMETIC_ONLY, LIGHT, STANDARD or CUSTOM."})
        public MasteryRewards rewards = MasteryRewards.COSMETIC_ONLY;
        @Comment({
                "Preparations needed for Novice, Familiar, Skilled, Expert, Mastered.",
                "One entry per rank above Unfamiliar.",
                "Changing these never destroys earned points; only the shown rank moves."})
        public List<Integer> thresholds = new ArrayList<>(MasteryCurve.DEFAULT_THRESHOLDS);
        @Comment({
                "Only used when scope = DISH: mastery points one dish needs for each star.",
                "Lower than the subcategory thresholds, because a single dish is cooked far less often."})
        public List<Integer> dishThresholds = new ArrayList<>(List.of(1, 5, 15, 30, 60));

        @Comment("Mastery points granted per preparation.")
        public int pointsPerPreparation = 1;
        @Comment("Points for personally eating the dish. Off by default.")
        public int pointsPerMeal = 0;
        @Comment("One-time mastery points for a player's first consumption of a preparation identity.")
        public int pointsPerFirstMeal = 1;
        @Comment("Points for serving a portion to someone else.")
        public int pointsPerServing = 1;
        @Comment("Extra points when the dish is made with its primary method.")
        public int intendedMethodBonus = 0;

        @Comment({
                "Cooking XP bonus per mastered dish, as a fraction, and its ceiling.",
                "The safest reward shape: mastery improves progression, not the food."})
        public double xpBonusPerMasteredDish = 0.005;
        public double xpBonusCap = 0.25;

        public Bonuses bonuses = new Bonuses();

        public MasteryCurve toCurve() {
            return new MasteryCurve(scope == ConfigEnums.MasteryScope.DISH ? dishThresholds : thresholds);
        }

        public boolean perDish() {
            return scope == ConfigEnums.MasteryScope.DISH;
        }
    }

    /**
     * What mastering a dish actually does in play.
     *
     * <p>Unlike the XP shaping above, these are real mechanical effects, and
     * they are on by default. Every magnitude is a plain number here so a pack
     * can dial them from "barely noticeable" to "absurd" without touching
     * anything else.</p>
     */
    @Comment({
            "What mastery does in play. These are real mechanical effects.",
            "Perfect chance scales with rank, then applies daily/batch adjustments",
            "and minimumPerfectChance as the final floor, including first attempts.",
            "The real odds for a dish are printed on its tooltip."})
    public static final class Bonuses {
        @Comment("Apply the Valheim zero-to-five-star coefficient tables. False uses neutral two-star food values.")
        public boolean valheimStarGrowth = true;
        @Comment("Health coefficient offsets for effective stars 0..5, relative to each slot grade's two-star baseline.")
        public List<Double> valheimHealthOffsets = new ArrayList<>(List.of(-0.2, -0.1, 0.0, 0.1, 0.2, 0.3));
        @Comment("Regeneration coefficients in HEARTS/second per N*adjustedS for effective stars 0..5, before reduction.")
        public List<Double> valheimRegenCoefficients = new ArrayList<>(List.of(0.02, 0.025, 0.03, 1.0 / 30.0, 11.0 / 300.0, 0.04));
        @Comment("Successfully eating an effective five-star FOOD fills only its actual new maximum-health capacity.")
        public boolean valheimFiveStarCapacityFill = true;
        @Comment("Valheim food health multiplier added per stamped star; 0 disables.")
        public double valheimHealthPerStar = 0.0;
        @Comment("Valheim regeneration multiplier added per stamped star; 0 disables.")
        public double valheimRegenPerStar = 0.0;
        @Comment("Valheim food duration multiplier added per stamped star; 0 disables.")
        public double valheimDurationPerStar = 0.0;
        @Comment("Vanilla saturation coefficient multiplier added per effective star (0.05 = 5%).")
        public double vanillaSaturationMultiplierPerStar = 0.05;
        @Comment("Extra actual saturation at five effective stars, before the vanilla hunger cap.")
        public float vanillaMaxStarSaturation = 1.0F;
        @Comment("Perfect dishes multiply vanilla nutrition by this amount, rounded to the nearest integer.")
        public double vanillaPerfectNutritionMultiplier = 1.5;
        @Comment("Set false for a statistics-only mod: no effect on food or cooking.")
        public boolean enabled = true;

        @Comment({
                "Extra cooking progress at maximum method familiarity (125 preparations).",
                "1.0 means twice as fast. Range 0.0 - 20.0.",
                "Only verified native cooking timers are changed; no extra fuel or automation ticks."})
        public double cookingSpeedAtMaxRank = 1.0;

        @Comment("Chance a preparation comes out perfect. Range 0.0 - 1.0.")
        public double perfectChanceAtMaxRank = 0.08;
        @Comment("Minimum perfect probability after daily bonuses and batch penalties, including zero-star cooking. 0.01 = 1%. Only applies when perfect production is enabled.")
        public double minimumPerfectChance = 0.01;


        @Comment("Mastery points the cook earns when someone else eats their cooking.")
        public int masteryPerMealServedToOthers = 1;
    }

    @Comment("Cook's rewards for others eating stamped food; counts across all dishes per Minecraft day.")
    public static final class SocialRewards {
        public boolean dailyDecay = true;
        public int fullMeals = 3;
        public int halfMeals = 10;
        public int quarterMeals = 20;
        public double halfMultiplier = 0.5;
        public double quarterMultiplier = 0.25;
        public double minimumMultiplier = 0.1;
    }

    @Comment("When a dish stops being a mystery in the journal.")
    public static final class Discovery {
        @Comment({
                "COOK_RECIPE, EAT_OUTPUT or OBTAIN_OUTPUT.",
                "ALWAYS_VISIBLE is file-only: permanently discovers all dishes, switching back does not undo records."})
        public DiscoveryMode mode = DiscoveryMode.COOK_RECIPE;
        @Comment("Discover dishes obtained in inventory independently of mode. No preparation XP or mastery is granted.")
        public boolean discoverOnObtain = true;
        @Comment("PERSONAL (default), TEAM (vanilla scoreboard team) or SERVER. Shares discovery only.")
        public SharedDiscovery shared = SharedDiscovery.PERSONAL;
        @Comment({"Hide an undiscovered dish's attributes, flavors and recipes in the journal's detail panel.",
                "Item tooltips are not affected."})
        public boolean hideUndiscoveredDetails = false;
    }

    public static final class FoodEffects {
        @Comment("Enable native/configured food potion effects and their tooltips. Flavor effects and level bonuses follow flavors.enabled independently.")
        public boolean enabled = true;
    }

    public static final class Flavors {
        @Comment("Enable ingredient flavor inheritance, numeric modifiers, potion effects and level bonuses. Saved presets remain editable when disabled.")
        public boolean enabled = true;
    }

    public static final class NutritionDisplay {
        @Comment("Show hunger/saturation values, including optional AppleSkin overlays and Obscure API food icons. Does not change food nutrition.")
        public boolean enabled = false;
    }

    public static final class FoodModeDefaults {
        @Comment("Internal marker: defaults are applied only when the effective food mode changes. Leave it unchanged to preserve manual overrides.")
        public String lastAppliedMode = "";
    }

    @Comment({"Flavor and category schemes (named sets of in-game edits).",
            "A default is loaded once, on first start, when that kind has no in-game edits yet."})
    public static final class Schemes {
        @Comment({"Flavor scheme to load on first start: a data-pack id like \"mypack:starter\",",
                "or the file name of a scheme in config/cuisinejournal/schemes/flavor. Empty = none."})
        public String defaultFlavorScheme = "";
        @Comment("Category scheme to load on first start (same format). Empty = none.")
        public String defaultCategoryScheme = "";
    }

    @Comment("Cooking streaks.")
    public static final class Streaks {
        @Comment("MINECRAFT_DAYS (shared daylight calendar) or DISABLED.")
        public StreakClock clock = StreakClock.MINECRAFT_DAYS;
        @Comment("Idle days forgiven before a streak resets. Range 1 - 365.")
        public int graceDays = 1;
        @Comment("Track a separate streak for cooking something different each day.")
        public boolean varietyStreakEnabled = true;
    }

    @Comment({
            "Challenges. Objectives are generated from what is installed,",
            "so a challenge is never impossible to complete."})
    public static final class Challenges {
        public boolean enabled = true;
        public boolean dailyEnabled = true;
        @Comment("How many are offered at once. Range 0 - 20.")
        public int dailyChallengeCount = 1;
        public double challengeXpMultiplier = 1.0;
        @Comment({
                "Item rewards for each generated daily challenge.",
                "Use modid:item or modid:item count. Empty = no item reward."})
        public List<String> dailyRewardItems = new ArrayList<>();
        @Comment({
                "Datapack rewards may run commands. Off unless a pack is trusted:",
                "a reward command runs with server authority."})
        public boolean allowCommandRewards = false;
    }

    @Comment("Leaderboards and the permission levels for the admin commands.")
    public static final class Multiplayer {
        @Comment("Off by default: the mod is not competitive unless a server wants it.")
        public boolean leaderboardsEnabled = false;
        @Comment({
                "Players appear only after opting in, by carrying the scoreboard tag:",
                "  tag @s add cuisinejournal_leaderboard"})
        public boolean leaderboardsOptInOnly = true;
        @Comment("How many places a leaderboard shows. Range 1 - 100.")
        public int leaderboardSize = 10;
        @Comment("Permission level to inspect another player's profile. Range 0 - 4.")
        public int inspectPermissionLevel = 2;
        @Comment("Permission level for /cuisinejournal reload, category editing and admin commands.")
        public int adminPermissionLevel = 2;
    }

    public static final class TodayDish {
        public boolean enabled = true;
        @Comment("One-based world game day; unlocked groups remain eligible. Select group by weight, then a member uniformly.")
        public List<TodayDishGroup> groups = new ArrayList<>(List.of(new TodayDishGroup(1,
                List.of("minecraft","farmersdelight","delightful","brewinandchewin","youkaishomecoming"),1)));
        @Comment("Require a world visit to the food definition's dimension. Shares visits for the shared daily dish.")
        public boolean requireVisitedDimension = true;
    }

    public static final class TodayDishGroup {
        public int fromDay = 1;
        public List<String> mods = new ArrayList<>();
        public double weight = 1;
        public TodayDishGroup() {}
        public TodayDishGroup(int fromDay,List<String> mods,double weight){this.fromDay=fromDay;this.mods=new ArrayList<>(mods);this.weight=weight;}
    }

    static void migrateSections(java.util.Map<String,Object> root){
        Object discoverySection=root.get("discovery");
        if(discoverySection instanceof java.util.Map<?,?> values
                &&"VIEW_RECIPE".equalsIgnoreCase(String.valueOf(values.get("mode")))) {
            var migrated=new java.util.LinkedHashMap<String,Object>();
            values.forEach((key,value)->migrated.put(key.toString(),value));
            migrated.put("mode","COOK_RECIPE");root.put("discovery",migrated);
        }
        Object streaks=root.get("streaks");
        if(streaks instanceof java.util.Map<?,?> values&&"REAL_DAYS".equals(values.get("clock"))){
            var migrated=new java.util.LinkedHashMap<String,Object>();
            values.forEach((key,value)->migrated.put(key.toString(),value));
            migrated.put("clock","MINECRAFT_DAYS");root.put("streaks",migrated);
        }
        Object legacy=root.get("challenges");
        if(legacy instanceof java.util.Map<?,?> values && !root.containsKey("todayDish")
                &&values.get("recipeOfTheDayEnabled") instanceof Boolean enabled){
            var today=new java.util.LinkedHashMap<String,Object>();today.put("enabled",enabled);root.put("todayDish",today);
        }
    }

    @Comment("Who gets the credit when a machine does the cooking.")
    public static final class Automation {
        @Comment("NO_CREDIT, OWNER_CREDIT, REDUCED_CREDIT or FULL_CREDIT.")
        public AutomationCredit credit = AutomationCredit.NO_CREDIT;
        @Comment("Multiplier used by REDUCED_CREDIT.")
        public double reducedCreditMultiplier = 0.25;
    }

    @Comment({
            "Which recipes become journal entries.",
            "The default rule is structural - a recipe counts when it produces",
            "something edible, whichever mod defines it. These narrow or widen it."})
    public static final class Compatibility {
        @Comment({"Workstation menu/speed context lifetime after interaction. Range 1 - 200 ticks.",
                "This does not decide production ownership; actual consumed inputs do."})
        public int workstationContextTicks = 20;
        @Comment({
                "Enable explicit Cuisine Journal item unification rules. Default false.",
                "The in-game setting also writes Kaleidoscope Compat's COMPAT/UNITE mode when installed.",
                "The in-game switch reloads packs immediately. No item groups are bundled by default."})
        public boolean unifiedMode = false;
        @Comment({
                "Extra blocks treated as cooking workstations, for addons with their own.",
                "\"modid:block\" or \"modid:block=modid:method\"."})
        public List<String> extraWorkstationBlocks = new ArrayList<>();
        @Comment("Menu classes never treated as culinary, on top of the built-in list.")
        public List<String> excludedMenuClasses = new ArrayList<>();
        @Comment("Recipe type ids to ignore, e.g. \"farmersdelight:cutting\".")
        public List<String> excludedRecipeTypes = new ArrayList<>();
        @Comment("Dish item ids to ignore.")
        public List<String> excludedItems = new ArrayList<>();
        @Comment({
                "Item tags whose outputs are ingredients or intermediate materials, not dishes.",
                "These items remain usable as ingredients; they only lose dish mastery/stars.",
                "Both c: and forge: conventions are accepted."})
        public List<String> excludedDishTags = new ArrayList<>(List.of(
                "c:dough", "forge:dough", "c:blank", "forge:blank",
                "c:raw_pasta", "forge:raw_pasta",
                "c:raw_meat", "c:raw_meats", "forge:raw_meat", "forge:raw_meats",
                "c:raw_fish", "c:raw_fishes", "forge:raw_fish", "forge:raw_fishes"));
        public List<String> excludedDishItems = new ArrayList<>(List.of("farmersdelight:raw_pasta"));
        @Comment("Item path suffixes excluded from dish mastery, for example pie_crust.")
        public List<String> excludedDishPathSuffixes = new ArrayList<>(List.of("pie_crust"));
        @Comment("Mod ids to ignore entirely.")
        public List<String> excludedMods = new ArrayList<>();
        @Comment("When not empty, only these mods contribute dishes.")
        public List<String> includedOnlyMods = new ArrayList<>();
        @Comment("A dish must carry one of these item tags to count.")
        public List<String> requiredItemTags = new ArrayList<>();
        @Comment("Set false to accept recipes whose output is not food.")
        public boolean requireEdibleOutput = true;
        @Comment({
                "Ignore storage blocks being unpacked back into food.",
                "A crate of potatoes crafted back into potatoes is one ingredient",
                "in and nine of the same food out - not cooking, but otherwise",
                "indistinguishable from it. Only vanilla crafting is affected, so",
                "a cutting board turning one input into several portions still",
                "counts."})
        public boolean ignoreUnpackingRecipes = true;
        @Comment({
                "Whether vanilla recipes earn mastery.",
                "On by default: bread and a baked potato are cooking too. Set false",
                "to turn the whole minecraft namespace off in one go, for a pack",
                "that wants the journal to be about its food addons."})
        public boolean trackVanillaRecipes = true;
    }

    @Comment("Knobs you should not need. Change them if a profile is misbehaving.")
    public static final class Advanced {
        @Comment("Milliseconds between profile autosaves. Range 5000 - 3600000.")
        public int autosaveIntervalMillis = 60_000;
        @Comment("Recent-activity entries retained per profile. Range 1 - 64.")
        public int activityHistorySize = 32;
        @Comment("How many dishes a player may pin at once. Range 1 - 16.")
        public int maxPinnedRecipes = 5;
        @Comment({
                "Journal entries sent per network packet. Range 8 - 256.",
                "Lower this only if a very large modpack trips the packet size limit."})
        public int journalPageSize = 64;
    }

    /**
     * Clamps every value into a workable range.
     *
     * @return human-readable notes about anything that had to be corrected;
     *         empty when the file was already sane
     */
    public List<String> validate() {
        List<String> issues = new ArrayList<>();
        if (progression == null) progression = new Progression();
        if (antiFarming == null) antiFarming = new AntiFarming();
        if (socialRewards == null) socialRewards = new SocialRewards();
        if (mastery == null) mastery = new Mastery();
        if (streaks == null) streaks = new Streaks();
        if (challenges == null) challenges = new Challenges();
        if (multiplayer == null) multiplayer = new Multiplayer();
        if (automation == null) automation = new Automation();
        if (compatibility == null) compatibility = new Compatibility();
        if (advanced == null) advanced = new Advanced();
        if(todayDish==null)todayDish=new TodayDish();
        if(todayDish.groups==null)todayDish.groups=new ArrayList<>();
        todayDish.groups.removeIf(java.util.Objects::isNull);
        for(var group:todayDish.groups){
            group.fromDay=clamp(group.fromDay,1,1_000_000,"todayDish.groups.fromDay",issues);
            group.weight=clamp(group.weight,0,1_000_000,"todayDish.groups.weight",issues);
            if(!Double.isFinite(group.weight))group.weight=0;
            if(group.mods==null)group.mods=new ArrayList<>();
            group.mods=new ArrayList<>(group.mods.stream().filter(java.util.Objects::nonNull)
                    .map(String::strip).filter(id->!id.isEmpty()&&id.matches("[a-z0-9_.-]+")).distinct().toList());
        }
        if (discovery == null) discovery = new Discovery();
        if (discovery.mode == null) discovery.mode = ConfigEnums.DiscoveryMode.COOK_RECIPE;
        if (discovery.shared == null) discovery.shared = SharedDiscovery.PERSONAL;
        if (foodEffects == null) foodEffects = new FoodEffects();
        if (flavors == null) flavors = new Flavors();
        if (nutritionDisplay == null) nutritionDisplay = new NutritionDisplay();
        if (foodModeDefaults == null) foodModeDefaults = new FoodModeDefaults();
        if (schemes == null) schemes = new Schemes();
        if(signatureDish==null)signatureDish=new SignatureDish();
        signatureDish.perfectChanceBonus=Double.isFinite(signatureDish.perfectChanceBonus)?Math.max(0,Math.min(1,signatureDish.perfectChanceBonus)):.10;
        signatureDish.changeExperienceLevels=clamp(signatureDish.changeExperienceLevels,0,1000,"signatureDish.changeExperienceLevels",issues);
        if (schemes.defaultFlavorScheme == null) schemes.defaultFlavorScheme = "";
        if (schemes.defaultCategoryScheme == null) schemes.defaultCategoryScheme = "";

        progression.xpMultiplier = clamp(progression.xpMultiplier, 0.0, 100.0,
                "progression.xpMultiplier", issues);
        progression.baseXpPerPreparation = clamp(progression.baseXpPerPreparation, 0, 10_000,
                "progression.baseXpPerPreparation", issues);
        progression.discoveryBonusXp = clamp(progression.discoveryBonusXp, 0, 100_000,
                "progression.discoveryBonusXp", issues);
        progression.ingredientDiscoveryXp = clamp(progression.ingredientDiscoveryXp, 0, 100_000,
                "progression.ingredientDiscoveryXp", issues);
        progression.methodDiscoveryXp = clamp(progression.methodDiscoveryXp, 0, 100_000,
                "progression.methodDiscoveryXp", issues);
        progression.experimentationXp = clamp(progression.experimentationXp, 0, 100_000,
                "progression.experimentationXp", issues);
        progression.experimentationDailyCap = clamp(progression.experimentationDailyCap, 0, 1_000_000,
                "progression.experimentationDailyCap", issues);
        progression.baseXpPerMeal = clamp(progression.baseXpPerMeal, 0, 10_000,
                "progression.baseXpPerMeal", issues);
        progression.baseXpPerMealServedToOthers = clamp(progression.baseXpPerMealServedToOthers, 0, 100_000,
                "progression.baseXpPerMealServedToOthers", issues);
        progression.levelCurveBase = clamp(progression.levelCurveBase, 1, 1_000_000,
                "progression.levelCurveBase", issues);
        progression.levelCurveLinear = clamp(progression.levelCurveLinear, 0, 1_000_000,
                "progression.levelCurveLinear", issues);
        progression.levelCurveQuadratic = clamp(progression.levelCurveQuadratic, 0, 1_000_000,
                "progression.levelCurveQuadratic", issues);
        progression.maxLevel = clamp(progression.maxLevel, 1, 10_000,
                "progression.maxLevel", issues);

        antiFarming.fullXpPreparations = clamp(antiFarming.fullXpPreparations, 0, 10_000,
                "antiFarming.fullXpPreparations", issues);
        antiFarming.minimumMultiplier = clamp(antiFarming.minimumMultiplier, 0.0, 1.0,
                "antiFarming.minimumMultiplier", issues);
        if (antiFarming.tiers == null) {
            antiFarming.tiers = new ArrayList<>();
        }
        antiFarming.tiers.removeIf(tier -> tier == null);
        for (ServerConfig.AntiFarming.Tier tier : antiFarming.tiers) {
            tier.upToPreparations = clamp(tier.upToPreparations, 1, 1_000_000,
                    "antiFarming.tiers.upToPreparations", issues);
            tier.multiplier = clamp(tier.multiplier, 0.0, 10.0,
                    "antiFarming.tiers.multiplier", issues);
        }
        antiFarming.tiers.sort((left, right) ->
                Integer.compare(left.upToPreparations, right.upToPreparations));

        if (mastery.thresholds == null || mastery.thresholds.isEmpty()) {
            mastery.thresholds = new ArrayList<>(MasteryCurve.DEFAULT_THRESHOLDS);
            issues.add("mastery.thresholds was empty; restored the defaults");
        }
        if (mastery.scope == null) {
            mastery.scope = ConfigEnums.MasteryScope.DISH;
            issues.add("mastery.scope was not SUBCATEGORY or DISH; using DISH");
        }
        if (mastery.dishThresholds == null || mastery.dishThresholds.isEmpty()) {
            mastery.dishThresholds = new ArrayList<>(List.of(1, 5, 15, 30, 60));
            issues.add("mastery.dishThresholds was empty; restored the defaults");
        }
        mastery.pointsPerPreparation = clamp(mastery.pointsPerPreparation, 0, 10_000,
                "mastery.pointsPerPreparation", issues);
        mastery.pointsPerMeal = clamp(mastery.pointsPerMeal, 0, 10_000,
                "mastery.pointsPerMeal", issues);
        mastery.pointsPerFirstMeal = clamp(mastery.pointsPerFirstMeal, 0, 10_000,
                "mastery.pointsPerFirstMeal", issues);
        mastery.pointsPerServing = clamp(mastery.pointsPerServing, 0, 10_000,
                "mastery.pointsPerServing", issues);
        mastery.intendedMethodBonus = clamp(mastery.intendedMethodBonus, 0, 10_000,
                "mastery.intendedMethodBonus", issues);
        mastery.xpBonusPerMasteredDish = clamp(mastery.xpBonusPerMasteredDish, 0.0, 1.0,
                "mastery.xpBonusPerMasteredDish", issues);
        mastery.xpBonusCap = clamp(mastery.xpBonusCap, 0.0, 10.0,
                "mastery.xpBonusCap", issues);

        if (mastery.bonuses == null) {
            mastery.bonuses = new Bonuses();
            issues.add("mastery.bonuses was missing; restored the defaults");
        }
        mastery.bonuses.valheimHealthOffsets = validatedStarValues(mastery.bonuses.valheimHealthOffsets,
                new Bonuses().valheimHealthOffsets, -1, 10, "mastery.bonuses.valheimHealthOffsets", issues);
        mastery.bonuses.valheimRegenCoefficients = validatedStarValues(mastery.bonuses.valheimRegenCoefficients,
                new Bonuses().valheimRegenCoefficients, 0, 10, "mastery.bonuses.valheimRegenCoefficients", issues);
        mastery.bonuses.valheimHealthPerStar = clamp(mastery.bonuses.valheimHealthPerStar,0,10,"mastery.bonuses.valheimHealthPerStar",issues);
        mastery.bonuses.valheimRegenPerStar = clamp(mastery.bonuses.valheimRegenPerStar,0,10,"mastery.bonuses.valheimRegenPerStar",issues);
        mastery.bonuses.valheimDurationPerStar = clamp(mastery.bonuses.valheimDurationPerStar,0,10,"mastery.bonuses.valheimDurationPerStar",issues);
        mastery.bonuses.vanillaSaturationMultiplierPerStar = clamp(mastery.bonuses.vanillaSaturationMultiplierPerStar,0,10,"mastery.bonuses.vanillaSaturationMultiplierPerStar",issues);
        mastery.bonuses.vanillaMaxStarSaturation = (float)clamp(mastery.bonuses.vanillaMaxStarSaturation,0,20,"mastery.bonuses.vanillaMaxStarSaturation",issues);
        mastery.bonuses.vanillaPerfectNutritionMultiplier = clamp(mastery.bonuses.vanillaPerfectNutritionMultiplier,1,10,"mastery.bonuses.vanillaPerfectNutritionMultiplier",issues);
        mastery.bonuses.cookingSpeedAtMaxRank = clamp(mastery.bonuses.cookingSpeedAtMaxRank,
                0.0, 20.0, "mastery.bonuses.cookingSpeedAtMaxRank", issues);
        mastery.bonuses.perfectChanceAtMaxRank = clamp(mastery.bonuses.perfectChanceAtMaxRank,
                0.0, 1.0, "mastery.bonuses.perfectChanceAtMaxRank", issues);
        mastery.bonuses.minimumPerfectChance = clamp(mastery.bonuses.minimumPerfectChance,
                0.0, 1.0, "mastery.bonuses.minimumPerfectChance", issues);
        if (!Double.isFinite(mastery.bonuses.minimumPerfectChance)) mastery.bonuses.minimumPerfectChance = 0.01;
        mastery.bonuses.masteryPerMealServedToOthers = clamp(
                mastery.bonuses.masteryPerMealServedToOthers, 0, 100,
                "mastery.bonuses.masteryPerMealServedToOthers", issues);
        if (socialRewards == null) socialRewards = new SocialRewards();
        socialRewards.fullMeals = clamp(socialRewards.fullMeals, 0, 1_000_000, "socialRewards.fullMeals", issues);
        socialRewards.halfMeals = clamp(socialRewards.halfMeals, socialRewards.fullMeals, 1_000_000, "socialRewards.halfMeals", issues);
        socialRewards.quarterMeals = clamp(socialRewards.quarterMeals, socialRewards.halfMeals, 1_000_000, "socialRewards.quarterMeals", issues);
        socialRewards.halfMultiplier = clamp(socialRewards.halfMultiplier, 0, 1, "socialRewards.halfMultiplier", issues);
        socialRewards.quarterMultiplier = clamp(socialRewards.quarterMultiplier, 0, socialRewards.halfMultiplier, "socialRewards.quarterMultiplier", issues);
        socialRewards.minimumMultiplier = clamp(socialRewards.minimumMultiplier, 0, socialRewards.quarterMultiplier, "socialRewards.minimumMultiplier", issues);

        streaks.graceDays = clamp(streaks.graceDays, 1, 365, "streaks.graceDays", issues);

        challenges.dailyChallengeCount = clamp(challenges.dailyChallengeCount, 0, 20,
                "challenges.dailyChallengeCount", issues);
        challenges.challengeXpMultiplier = clamp(challenges.challengeXpMultiplier, 0.0, 100.0,
                "challenges.challengeXpMultiplier", issues);
        challenges.dailyRewardItems = new ArrayList<>(
                com.chi__you.cuisinejournal.challenge.ChallengeReward.normalizeItems(challenges.dailyRewardItems));

        multiplayer.leaderboardSize = clamp(multiplayer.leaderboardSize, 1, 100,
                "multiplayer.leaderboardSize", issues);
        multiplayer.inspectPermissionLevel = clamp(multiplayer.inspectPermissionLevel, 0, 4,
                "multiplayer.inspectPermissionLevel", issues);
        multiplayer.adminPermissionLevel = clamp(multiplayer.adminPermissionLevel, 0, 4,
                "multiplayer.adminPermissionLevel", issues);

        automation.reducedCreditMultiplier = clamp(automation.reducedCreditMultiplier, 0.0, 1.0,
                "automation.reducedCreditMultiplier", issues);
        compatibility.workstationContextTicks = clamp(compatibility.workstationContextTicks, 1, 200,
                "compatibility.workstationContextTicks", issues);

        advanced.autosaveIntervalMillis = clamp(advanced.autosaveIntervalMillis, 5_000, 3_600_000,
                "advanced.autosaveIntervalMillis", issues);
        advanced.activityHistorySize = clamp(advanced.activityHistorySize, 1, 64,
                "advanced.activityHistorySize", issues);
        advanced.maxPinnedRecipes = clamp(advanced.maxPinnedRecipes, 1, 16,
                "advanced.maxPinnedRecipes", issues);
        advanced.journalPageSize = clamp(advanced.journalPageSize, 8, 256,
                "advanced.journalPageSize", issues);

        return issues;
    }

    private static int clamp(int value, int min, int max, String field, List<String> issues) {
        int clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            issues.add(field + " was " + value + "; clamped to " + clamped);
        }
        return clamped;
    }

    private static List<Double> validatedStarValues(List<Double> values, List<Double> defaults,
                                                    double min, double max, String field, List<String> issues) {
        if (values == null || values.size() != 6) {
            issues.add(field + " requires six values; restored defaults");
            return defaults;
        }
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < 6; i++) result.add(values.get(i) == null ? defaults.get(i)
                : clamp(values.get(i), min, max, field + "[" + i + "]", issues));
        return result;
    }

    private static double clamp(double value, double min, double max, String field,
                                List<String> issues) {
        if (Double.isNaN(value)) {
            issues.add(field + " was not a number; reset to " + min);
            return min;
        }
        double clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            issues.add(field + " was " + value + "; clamped to " + clamped);
        }
        return clamped;
    }
}
