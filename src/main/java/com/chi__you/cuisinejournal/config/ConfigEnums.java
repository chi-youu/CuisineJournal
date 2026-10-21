package com.chi__you.cuisinejournal.config;

/**
 * The mode switches a server or player can flip.
 *
 * <p>Shared serialized values for server rules and client preferences.</p>
 */
public final class ConfigEnums {
    private ConfigEnums() {
    }

    /** How a dish becomes discovered. */
    public enum DiscoveryMode {
        /** Default: the player has to actually prepare it. */
        COOK_RECIPE,
        /** Eating the dish counts, however it was obtained. */
        EAT_OUTPUT,
        /** Simply having the item in inventory counts. */
        OBTAIN_OUTPUT,
        /** Configuration-file-only: permanently discover every registered dish. */
        ALWAYS_VISIBLE
    }


    /** What mastery is allowed to affect. */
    public enum MasteryRewards {
        /** Statistics, badges and journal labels only. Nothing touches gameplay. */
        COSMETIC_ONLY,
        /** Small progression-side bonuses: slightly more Cooking XP. */
        LIGHT,
        /** Noticeable but balanced; still progression-side by default. */
        STANDARD,
        /** Every individual bonus is read from configuration. */
        CUSTOM
    }

    /** Who gets credit when a machine cooks something. */
    public enum AutomationCredit {
        /** Default. No player is credited unless one is actually responsible. */
        NO_CREDIT,
        /** Credit the placing player where the game genuinely records one. */
        OWNER_CREDIT,
        /** As OWNER_CREDIT, at a reduced XP rate. */
        REDUCED_CREDIT,
        /** Treat automated output as if the owner had cooked it by hand. */
        FULL_CREDIT
    }

    /** Whose discoveries are shared. */
    public enum SharedDiscovery {
        /** Default. Discovery is personal. */
        PERSONAL,
        /** Anyone on your scoreboard team reveals dishes for the team. */
        TEAM,
        /** The first player to discover a dish reveals it for everyone. */
        SERVER
    }

    /** Which clock drives cooking streaks. */
    public enum StreakClock {
        /** Default. Streaks advance with in-game days. */
        MINECRAFT_DAYS,
        DISABLED
    }


    /** What star ranks belong to. */
    public enum MasteryScope {
        /** Stars belong to a subcategory: all its dishes' preparations add up; discovery raises the cap. */
        SUBCATEGORY,
        /** Each dish has its own stars from its own mastery points (original Meal Mastery). */
        DISH
    }

    /** Presets a server or player may apply deliberately. */
    public enum Preset {
        /** Journal and statistics, minimal progression pressure. */
        VANILLA_PLUS,
        /** Faster progression and discovery, softer anti-farming. */
        COZY,
        /** Emphasises discovery and completion. */
        COMPLETIONIST,
        /** Balanced multiplayer defaults. */
        SERVER,
        /** No gameplay bonuses whatsoever. */
        COSMETIC
    }
}
