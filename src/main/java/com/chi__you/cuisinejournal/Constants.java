package com.chi__you.cuisinejournal;

/**
 * Shared identifiers that remain safe to load inside plain unit tests.
 *
 * <p>Nothing here may touch Minecraft classes: the test source set runs without
 * a game runtime.</p>
 */
public final class Constants {
    public static final String MOD_ID = "cuisinejournal";
    public static final String MOD_NAME = "Cuisine Journal";
    public static final String MINECRAFT_VERSION = "1.20.1";

    /** Optional host identifier; the journal also works without Farmer's Delight. */
    public static final String FARMERS_DELIGHT_ID = "farmersdelight";

    public static final String COMMAND = "cuisinejournal";
    public static final String COMMAND_ALIAS_SHORT = "cj";

    /** Bumped whenever the persisted profile layout changes. See {@code data/ProfileMigrations}. */
    public static final int PROFILE_SCHEMA_VERSION = 1;

    private Constants() {
    }
}
