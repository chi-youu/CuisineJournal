package com.chi__you.cuisinejournal.recipe;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** One complete replacement stage selected by the downstream product's stamped stars. */
public record FlavorStage(@com.google.gson.annotations.SerializedName("min_stars") int minStars, List<Effect> effects,
                          FlavorModifiers modifiers, FlavorModifiers foodModifiers, FlavorModifiers drinkModifiers,
                          List<EffectLevelBonus> effectLevelBonuses,
                          @com.google.gson.annotations.SerializedName("effect_target") String effectTarget) {
    public FlavorStage {
        effectTarget = effectTarget == null ? "all" : effectTarget;
        if (!Set.of("all", "food", "drink").contains(effectTarget))
            throw new IllegalArgumentException("Invalid flavor effect target");
        if(minStars<0||minStars>5)throw new IllegalArgumentException("Invalid flavor star stage");
        effects = List.copyOf(effects);
        if(effects.size()>64)throw new IllegalArgumentException("Too many flavor effects");
        Set<ResourceLocation> seen=new HashSet<>();
        for(var effect:effects)if(effect.id()==null||effect.level()<1||effect.level()>256
                ||effect.durationSeconds()<1||effect.durationSeconds()>2592000
                ||!Float.isFinite(effect.activeFraction())||effect.activeFraction()<0||effect.activeFraction()>1
                ||!seen.add(effect.id()))throw new IllegalArgumentException("Invalid flavor effect");
        modifiers=modifiers==null?FlavorModifiers.NONE:modifiers;
        foodModifiers=foodModifiers==null?FlavorModifiers.NONE:foodModifiers;
        drinkModifiers=drinkModifiers==null?FlavorModifiers.NONE:drinkModifiers;
        effectLevelBonuses=effectLevelBonuses==null?List.of():List.copyOf(effectLevelBonuses);
        if(effectLevelBonuses.size()>64)throw new IllegalArgumentException("Too many effect level bonuses");
        Set<ResourceLocation> bonusIds=new HashSet<>();
        for(var bonus:effectLevelBonuses)if(bonus.id()==null||bonus.levels()<1||bonus.levels()>255||!bonusIds.add(bonus.id()))
            throw new IllegalArgumentException("Invalid effect level bonus");
    }
    public record EffectLevelBonus(ResourceLocation id,int levels) {}
    public FlavorStage(int minStars,List<Effect> effects,FlavorModifiers modifiers,FlavorModifiers foodModifiers,
                       FlavorModifiers drinkModifiers,List<EffectLevelBonus> effectLevelBonuses) {
        this(minStars,effects,modifiers,foodModifiers,drinkModifiers,effectLevelBonuses,"all");
    }
    public boolean effectsApplyTo(boolean drink) {
        return effectTarget.equals("all") || effectTarget.equals(drink ? "drink" : "food");
    }
    public FlavorStage(int minStars,List<Effect> effects,FlavorModifiers modifiers,FlavorModifiers foodModifiers,FlavorModifiers drinkModifiers) {
        this(minStars,effects,modifiers,foodModifiers,drinkModifiers,List.of());
    }
    public FlavorStage(int minStars,List<Effect> effects) { this(minStars,effects,FlavorModifiers.NONE,FlavorModifiers.NONE,FlavorModifiers.NONE); }
    public FlavorModifiers modifiersFor(boolean drink) { return modifiers.plus(drink?drinkModifiers:foodModifiers); }
    public record Effect(ResourceLocation id, int level,
                         @com.google.gson.annotations.SerializedName("duration_seconds") int durationSeconds,
                         @com.google.gson.annotations.SerializedName("active_fraction") float activeFraction) {}

    public static List<FlavorStage> read(com.google.gson.JsonArray array) {
        if (array.size() > 6) throw new IllegalArgumentException("Too many flavor stages");
        Map<Integer, FlavorStage> result = new TreeMap<>();
        for (var raw : array) {
            JsonObject stage = raw.getAsJsonObject();
            int stars = integer(stage, "min_stars", -1);
            if (stars < 0 || stars > 5 || result.containsKey(stars)) throw new IllegalArgumentException("Invalid flavor star stage");
            List<Effect> effects = new ArrayList<>();
            var effectArray=stage.has("effects")?stage.getAsJsonArray("effects"):new com.google.gson.JsonArray();
            if (effectArray.size() > 64) throw new IllegalArgumentException("Too many flavor effects");
            Set<ResourceLocation> ids = new HashSet<>();
            for (var value : effectArray) {
                var json = value.getAsJsonObject();
                ResourceLocation id = ResourceLocation.parse(json.get("id").getAsString());
                int level = integer(json, "level", 1);
                int seconds = integer(json, "duration_seconds", 20);
                float fraction = json.has("active_fraction") ? json.get("active_fraction").getAsFloat() : 1;
                if (level < 1 || level > 256 || seconds < 1 || seconds > 2592000
                        || !Float.isFinite(fraction) || fraction < 0 || fraction > 1 || !ids.add(id))
                    throw new IllegalArgumentException("Invalid flavor effect");
                effects.add(new Effect(id, level, seconds, fraction));
            }
            List<EffectLevelBonus> bonuses=new ArrayList<>();
            if(stage.has("effect_level_bonuses"))for(var value:stage.getAsJsonArray("effect_level_bonuses")) {
                var json=value.getAsJsonObject();
                bonuses.add(new EffectLevelBonus(ResourceLocation.parse(json.get("id").getAsString()),integer(json,"levels",1)));
            }
            result.put(stars, new FlavorStage(stars, effects,FlavorModifiers.read(stage.getAsJsonObject("modifiers")),
                    FlavorModifiers.read(stage.getAsJsonObject("food_modifiers")),FlavorModifiers.read(stage.getAsJsonObject("drink_modifiers")),bonuses,
                    stage.has("effect_target") ? stage.get("effect_target").getAsString() : "all"));
        }
        return List.copyOf(result.values());
    }

    private static int integer(JsonObject json, String field, int fallback) {
        if (!json.has(field)) return fallback;
        var raw = json.get(field);
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()
                || !raw.getAsString().matches("-?[0-9]+")) throw new IllegalArgumentException(field + " must be an integer");
        return raw.getAsInt();
    }

    public static List<Effect> select(List<FlavorStage> stages, int stars) {
        FlavorStage selected=selectStage(stages,stars);
        return selected==null?List.of():selected.effects;
    }

    public static FlavorStage selectStage(List<FlavorStage> stages,int stars) {
        FlavorStage selected = null;
        for (FlavorStage stage : stages) if (stage.minStars <= Math.max(0, Math.min(5, stars))
                && (selected == null || stage.minStars > selected.minStars)) selected = stage;
        return selected;
    }

    public JsonObject toJson() {
        var json=new JsonObject();json.addProperty("min_stars",minStars);
        if (!effectTarget.equals("all")) json.addProperty("effect_target", effectTarget);
        var array=new com.google.gson.JsonArray();
        for(var effect:effects) {
            var row=new JsonObject();row.addProperty("id",effect.id.toString());row.addProperty("level",effect.level);
            row.addProperty("duration_seconds",effect.durationSeconds);row.addProperty("active_fraction",effect.activeFraction);array.add(row);
        }
        json.add("effects",array);
        if(!effectLevelBonuses.isEmpty()) {
            var bonuses=new com.google.gson.JsonArray();
            for(var bonus:effectLevelBonuses){var row=new JsonObject();row.addProperty("id",bonus.id().toString());row.addProperty("levels",bonus.levels());bonuses.add(row);}
            json.add("effect_level_bonuses",bonuses);
        }
        if(!modifiers.equals(FlavorModifiers.NONE))json.add("modifiers",modifiers.toJson());
        if(!foodModifiers.equals(FlavorModifiers.NONE))json.add("food_modifiers",foodModifiers.toJson());
        if(!drinkModifiers.equals(FlavorModifiers.NONE))json.add("drink_modifiers",drinkModifiers.toJson());
        return json;
    }
}
