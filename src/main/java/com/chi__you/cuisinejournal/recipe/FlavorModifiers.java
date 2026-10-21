package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonObject;

/**
 * Flat additions and additive multiplier bonuses; units match food definitions.
 * {@code drinkBonusMultiplier} adds to a drink's bonus (0.05 = five percentage points); foods ignore it.
 * The legacy field name is retained for saved presets and exported packs.
 */
public record FlavorModifiers(double healthPoints, double healthMultiplier,
                              double regenPerSecond, double regenMultiplier,
                              double durationSeconds, double durationMultiplier,
                              double drinkBonusMultiplier) {
    public static final FlavorModifiers NONE = new FlavorModifiers(0,0,0,0,0,0,0);
    public FlavorModifiers {
        for(double value:new double[]{healthPoints,healthMultiplier,regenPerSecond,regenMultiplier,durationSeconds,durationMultiplier,drinkBonusMultiplier})
            if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite flavor modifier");
    }
    public FlavorModifiers(double healthPoints, double healthMultiplier, double regenPerSecond, double regenMultiplier,
                           double durationSeconds, double durationMultiplier) {
        this(healthPoints,healthMultiplier,regenPerSecond,regenMultiplier,durationSeconds,durationMultiplier,0);
    }
    private static void finite(double value, double limit) {
        if (!Double.isFinite(value) || Math.abs(value) > limit) throw new IllegalArgumentException("Flavor modifier out of range");
    }
    public FlavorModifiers plus(FlavorModifiers other) {
        // Aggregates are calculated separately: entry validation limits are not a gameplay total cap.
        return new FlavorModifiers(healthPoints+other.healthPoints,healthMultiplier+other.healthMultiplier,
                regenPerSecond+other.regenPerSecond,regenMultiplier+other.regenMultiplier,
                durationSeconds+other.durationSeconds,durationMultiplier+other.durationMultiplier,
                drinkBonusMultiplier+other.drinkBonusMultiplier);
    }
    public static FlavorModifiers read(JsonObject json) {
        if (json == null) return NONE;
        var result=new FlavorModifiers(number(json,"health_points"),number(json,"health_multiplier"),
                number(json,"regen_per_second"),number(json,"regen_multiplier"),
                number(json,"duration_seconds"),number(json,"duration_multiplier"),number(json,"drink_bonus_multiplier"));
        finite(result.healthPoints,10000);finite(result.healthMultiplier,100);
        finite(result.regenPerSecond,1000);finite(result.regenMultiplier,100);
        finite(result.durationSeconds,2592000);finite(result.durationMultiplier,100);finite(result.drinkBonusMultiplier,100);
        return result;
    }
    private static double number(JsonObject json, String key) {
        if (!json.has(key)) return 0;
        var value=json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key+" must be numeric");
        return value.getAsDouble();
    }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();
        json.addProperty("health_points",healthPoints);json.addProperty("health_multiplier",healthMultiplier);
        json.addProperty("regen_per_second",regenPerSecond);json.addProperty("regen_multiplier",regenMultiplier);
        json.addProperty("duration_seconds",durationSeconds);json.addProperty("duration_multiplier",durationMultiplier);
        // Written only when used, so presets saved before this field compare equal.
        if(drinkBonusMultiplier!=0)json.addProperty("drink_bonus_multiplier",drinkBonusMultiplier);
        return json;
    }
    public double health(double base) { return apply(base,healthPoints,healthMultiplier); }
    public double regen(double base) { return apply(base,regenPerSecond,regenMultiplier); }
    public double duration(double baseSeconds) { return apply(baseSeconds,durationSeconds,durationMultiplier); }
    public double drinkBonus(double base) { return Math.max(0, base + drinkBonusMultiplier); }
    public int durationTicks(int baseTicks) {
        return Math.max(20,(int)Math.min(Integer.MAX_VALUE,Math.round(duration(baseTicks/20.0)*20)));
    }
    private static double apply(double base, double flat, double multiplier) {
        return Math.max(0, (base+flat)*Math.max(0,1+multiplier));
    }
}
