package com.chi__you.cuisinejournal.config;


import java.util.ArrayList;
import java.util.List;

/**
 * Client-only preferences.
 *
 * <p>Nothing here can influence progression. The client asks the server for
 * data and decides how to draw it; that is the whole extent of its authority
 *.</p>
 */
public final class ClientConfig {

    /** Where the compact stats panel beside a container screen appears. */
    public enum SidePanel {
        /** Beside every container screen. */
        ALWAYS,
        /** Only cooking surfaces, or screens actually holding a tracked dish. */
        CULINARY,
        OFF
    }


    public Journal journal = new Journal();
    public FoodHud foodHud = new FoodHud();
    public CookingHud cookingHud = new CookingHud();
    public Notifications notifications = new Notifications();
    public Tooltips tooltips = new Tooltips();

    public static final class Tooltips {
        @Comment("Hold Shift to show cooking history and bonus details in food tooltips.")
        public boolean requireModifier = true;
    }

    @Comment("The journal screen, opened with the keybind (J by default).")
    public static final class Journal {
        @Comment("Initial page: overview, ingredients, collections, challenges or statistics.")
        public String defaultPage = "overview";
        @Comment("Lines of recent activity on the overview. Range 0 - 32.")
        public int recentActivityLines = 6;
        @Comment("Dish journal home page shows every food in one grid instead of category cards.")
        public boolean allFoodsView = false;
        @Comment({
                "The compact stats panel docked beside a cooking screen.",
                "CULINARY shows it beside cooking stations only, ALWAYS beside every",
                "container, OFF never. It never appears beside the inventory or the",
                "creative tabs whichever is set."})
        public SidePanel sidePanel = SidePanel.CULINARY;
    }

    @Comment("Food slot HUD appearance. Independent of the cooking experience bar.")
    public static final class FoodHud {
        @Comment("Use the larger SOL-style food slot icons.")
        public boolean useLargeFoodIcons = true;
    }

    @Comment("Optional cooking level and experience bar, not vanilla enchanting XP. Off by default.")
    public static final class CookingHud {
        @Comment("Off by default: the HUD must not be permanently cluttered.")
        public boolean enabled = false;
        @Comment("Show the bar only briefly after XP is gained.")
        public boolean onlyWhileActive = true;
        @Comment("How long it stays up, in ticks. Range 20 - 1200.")
        public int visibleTicks = 60;
        @Comment("TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT or BOTTOM_RIGHT.")
        public Anchor anchor = Anchor.TOP_LEFT;
        @Comment("Pixels from that corner. Range -512 - 512.")
        public int offsetX = 4;
        public int offsetY = 4;
        @Comment("Range 0.5 - 3.0.")
        public float scale = 1.0F;
        @Comment("Show tracked recipe requirements independently of the cooking XP HUD.")
        public boolean showPinnedRecipes = true;

        public enum Anchor {
            TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
        }
    }

    @Comment("Toasts and popups. Each type is individually suppressible.")
    public static final class Notifications {
        public boolean discoveries = true;
        public boolean masteryRanks = true;
        public boolean levelUps = true;
        public boolean challenges = true;
        public boolean milestones = true;
        public boolean xpPopups = true;
        @Comment("Rapid gains are summed into one popup rather than spamming.")
        public int xpPopupAggregationTicks = 30;
        @Comment("Play progression notification sounds. Does not control journal button sounds.")
        public boolean playSounds = true;
        @Comment("Toasts per second before further ones are dropped.")
        public int maxToastsPerSecond = 2;
    }

    public List<String> validate() {
        List<String> issues = new ArrayList<>();
        if (tooltips == null) tooltips = new Tooltips();

        journal.defaultPage = normalisePage(journal.defaultPage);
        journal.recentActivityLines = clamp(journal.recentActivityLines, 0, 32,
                "journal.recentActivityLines", issues);

        cookingHud.visibleTicks = clamp(cookingHud.visibleTicks, 20, 1200, "cookingHud.visibleTicks", issues);
        cookingHud.offsetX = clamp(cookingHud.offsetX, -512, 512, "cookingHud.offsetX", issues);
        cookingHud.offsetY = clamp(cookingHud.offsetY, -512, 512, "cookingHud.offsetY", issues);
        cookingHud.scale = clamp(cookingHud.scale, 0.5F, 3.0F, "cookingHud.scale", issues);


        notifications.xpPopupAggregationTicks = clamp(notifications.xpPopupAggregationTicks, 1, 200,
                "notifications.xpPopupAggregationTicks", issues);
        notifications.maxToastsPerSecond = clamp(notifications.maxToastsPerSecond, 1, 20,
                "notifications.maxToastsPerSecond", issues);


        return issues;
    }

    public static String normalisePage(String id) {
        if (id == null) return "overview";
        return switch (id) {
            case "recipes" -> "collections";
            case "methods", "records" -> "statistics";
            case "overview", "ingredients", "collections", "challenges", "statistics" -> id;
            default -> "overview";
        };
    }

    @SuppressWarnings("unchecked")
    static void migrateSections(java.util.Map<String, Object> source) {
        Object legacy = source.remove("hud");
        if (!(legacy instanceof java.util.Map<?, ?> values)) return;
        var cooking = (java.util.Map<String, Object>) source.computeIfAbsent("cookingHud",
                ignored -> new java.util.LinkedHashMap<String, Object>());
        var food = (java.util.Map<String, Object>) source.computeIfAbsent("foodHud",
                ignored -> new java.util.LinkedHashMap<String, Object>());
        values.forEach((key, value) -> {
            if ("useLargeFoodIcons".equals(key)) food.putIfAbsent(key.toString(), value);
            else cooking.putIfAbsent(key.toString(), value);
        });
    }

    private static int clamp(int value, int min, int max, String field, List<String> issues) {
        int clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            issues.add(field + " was " + value + "; clamped to " + clamped);
        }
        return clamped;
    }

    private static float clamp(float value, float min, float max, String field,
                               List<String> issues) {
        if (Float.isNaN(value)) {
            issues.add(field + " was not a number; reset to " + min);
            return min;
        }
        float clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            issues.add(field + " was " + value + "; clamped to " + clamped);
        }
        return clamped;
    }
}
