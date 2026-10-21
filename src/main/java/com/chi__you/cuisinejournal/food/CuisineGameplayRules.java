package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.config.ConfigManager;

/** Server rules, or their synced client copy when rendering or predicting. */
public final class CuisineGameplayRules {
    private CuisineGameplayRules() {}
    public static boolean flavorsEnabled() {
        if (CuisineFoodRuntime.isClientThread()) {
            return ClientFoodRuleView.current().flavors();
        }
        return ConfigManager.server().flavors.enabled;
    }
    public static boolean nutritionDisplayEnabled() {
        if (CuisineFoodRuntime.isClientThread()) {
            return ClientFoodRuleView.current().nutrition();
        }
        return ConfigManager.server().nutritionDisplay.enabled;
    }
    public static boolean starsEnabled() {
        if (CuisineFoodRuntime.isClientThread()) {
            return ClientFoodRuleView.current().stars();
        }
        return ConfigManager.server().mastery.enabled;
    }
    /** Native/configured food effects only; flavor effects follow flavorsEnabled(). */
    public static boolean effectsEnabled() {
        if (CuisineFoodRuntime.isClientThread()) {
            return ClientFoodRuleView.current().nativeEffects();
        }
        return ConfigManager.server().foodEffects.enabled;
    }

    public static boolean nativeEffectsEnabled() {
        if (CuisineFoodRuntime.isClientThread()) {
            return nativeEffectsEnabled(effectsEnabled(), CuisineFoodClientState.active(),
                    CuisineFoodClientState.preserveNativeEffects());
        }
        return nativeEffectsEnabled(effectsEnabled(), CuisineFoodRuntime.active(),
                CuisineFoodConfig.PRESERVE_NATIVE_EFFECTS.get());
    }

    public static boolean nativeEffectsEnabled(boolean enabled, boolean valheim, boolean preserve) {
        return enabled && (!valheim || preserve);
    }
}
