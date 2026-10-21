package com.chi__you.cuisinejournal.config;

/** A mode change applies a starting point, not a permanent lock on its dependent settings. */
public final class FoodModeDefaults {
    private FoodModeDefaults() {}
    public static void initializeExisting(ServerConfig config,String mode){
        if(config.foodModeDefaults.lastAppliedMode.isBlank())config.foodModeDefaults.lastAppliedMode=mode;
    }
    public static boolean apply(ServerConfig config, String mode) {
        if (!mode.equals("valheim") && !mode.equals("vanilla")) throw new IllegalArgumentException("Unknown food mode");
        if (mode.equals(config.foodModeDefaults.lastAppliedMode)) return false;
        boolean valheim = mode.equals("valheim");
        config.flavors.enabled = valheim;
        config.nutritionDisplay.enabled = !valheim;
        config.foodModeDefaults.lastAppliedMode = mode;
        return true;
    }
}
