package com.chi__you.cuisinejournal.food;

/** Valheim food formulas shared by runtime calculations and focused tests. */
public final class CuisineFoodMath {
    private CuisineFoodMath() {
    }

    public static int remainingAfterAdvance(int remaining, int ticks) {
        return (int) Math.max(0L, (long) remaining - Math.max(0, ticks));
    }
    public static int remainingAfterSleep(int remaining,long ticks) {
        return (int)Math.max(0L,(long)Math.max(0,remaining)-Math.max(0,ticks));
    }

    public static float maxHealth(int startingHearts, int maxFoodHearts,
                                  float foodNutrition, float drinkNutrition, float drinkBonus,
                                  boolean hasDrink) {
        float nutrition = foodNutrition + drinkNutrition;
        if (hasDrink) {
            nutrition *= 1.0F + drinkBonus;
        }
        float health = Math.min(maxFoodHearts * 2.0F, startingHearts * 2.0F + nutrition);
        return roundedHealthPoints(health);
    }

    public static float healPerPulse(float foodRegen, float drinkRegen,
                                     float drinkBonus, boolean hasDrink) {
        float regen = 0.25F + foodRegen + drinkRegen;
        return (hasDrink ? regen * (1.0F + drinkBonus) : regen) / 20.0F;
    }

    public static float effectiveSaturation(float saturation) {
        float value = Math.max(0, saturation);
        return value < 0.6F ? 0.3F + 0.5F * value : value;
    }

    /** Extra maximum health in points (two points are one heart). */
    public static float foodHealthPoints(int nutrition, float saturation) {
        return foodHealthPoints(nutrition, saturation, 2);
    }

    public static float slotScore(int nutrition, float saturation) {
        return Math.max(0, nutrition) * (0.75F + 0.25F * effectiveSaturation(saturation));
    }

    public static int automaticSlots(int nutrition, float saturation, double oneSlotLimit,
                                     int twoSlotNutritionLimit, double twoSlotSizeLimit) {
        if (slotScore(nutrition, saturation) <= oneSlotLimit + 1e-6) return 1;
        double size = Math.max(0, nutrition) * (0.5 + 0.5 * effectiveSaturation(saturation));
        return nutrition > twoSlotNutritionLimit && size > twoSlotSizeLimit + 1e-6 ? 3 : 2;
    }

    /** Standard quality is independent of the stamp so mastery cannot change slot cost. */
    public static float foodHealthPoints(int nutrition, float saturation, int slots) {
        return foodHealthPoints(nutrition, saturation, slots, 0);
    }

    /** Offset is relative to two-star quality, before the high-value reduction. */
    public static float foodHealthPoints(int nutrition, float saturation, int slots, double coefficientOffset) {
        double coefficient = slots <= 1 ? 1.2 : slots == 2 ? 1.5 : 1.8;
        double size = Math.max(0, nutrition) * (0.5 + 0.5 * effectiveSaturation(saturation));
        float standard = reduceHealthPoints((float)(size * coefficient));
        float grown = reduceHealthPoints((float)(size * Math.max(0, coefficient + coefficientOffset)));
        // Cap standard one-slot capacity, then carry only the actual mastery gain/loss.
        float health = slots <= 1 ? Math.min(11, standard) + grown - standard : grown;
        return roundedHealthPoints(Math.max(0, health));
    }

    public static float reduceHealthPoints(float raw) {
        if (raw <= 16) return raw;
        if (raw <= 36) return 16 + (raw - 16) * 0.75F;
        return 31 + (raw - 36) * 0.2F;
    }

    public static float roundedHealthPoints(float points) {
        return Math.round(points);
    }

    /** Actual health points per second; the 0.03 coefficient is in hearts per second. */
    public static float foodRegenPerSecond(int nutrition, float saturation) {
        return foodRegenPerSecond(nutrition, saturation, 0.03);
    }

    public static float foodRegenPerSecond(int nutrition, float saturation, double coefficient) {
        float rawHearts = (float)(Math.max(0, nutrition) * effectiveSaturation(saturation) * Math.max(0, coefficient));
        float hearts = rawHearts <= 0.6F ? rawHearts : 0.6F + (rawHearts - 0.6F) * 0.25F;
        return hearts * 2;
    }

    public static float healthAfterCapacityIncrease(float health, float oldMaximum, float newMaximum) {
        return Math.min(newMaximum, health + Math.max(0, newMaximum - oldMaximum));
    }

    public static boolean combatRecoveryReady(long now, long lastCombat, int delayTicks) {
        return lastCombat == Long.MIN_VALUE || now >= lastCombat && now - lastCombat >= Math.max(0, delayTicks);
    }

    public static int durationTicks(int foodSecondsPerNutrition, int drinkDurationSeconds, int nutrition, boolean drink) {
        double seconds = drink ? drinkDurationSeconds : (double) foodSecondsPerNutrition * Math.max(0, nutrition);
        return Math.max(CuisineFoodService.MIN_DURATION_TICKS,
                (int) Math.min(Integer.MAX_VALUE, seconds * 20));
    }
}
