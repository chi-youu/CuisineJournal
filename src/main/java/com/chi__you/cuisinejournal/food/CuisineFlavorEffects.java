package com.chi__you.cuisinejournal.food;

import com.chi__you.cuisinejournal.culinary.MealStamp;
import com.chi__you.cuisinejournal.recipe.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * Flavor of a finished food. Sources come from the food definition's {@code flavor_sources}
 * when the field is present (including an empty array); otherwise recipe inputs are traced
 * recursively until source items. Named tags stay tags rather than expanding all members.
 */
public final class CuisineFlavorEffects {
    private CuisineFlavorEffects() {}
    public record Resolved(List<ResourceLocation> flavors,List<CuisineFoodDefinitions.Effect> effects,FlavorModifiers modifiers,
                           List<String> problems,Map<ResourceLocation,Integer> effectLevelBonuses) {
        public Resolved {flavors=List.copyOf(flavors);effects=List.copyOf(effects);problems=List.copyOf(problems);effectLevelBonuses=Map.copyOf(effectLevelBonuses);}
        public Resolved(List<ResourceLocation> flavors,List<CuisineFoodDefinitions.Effect> effects,FlavorModifiers modifiers,List<String> problems) {
            this(flavors,effects,modifiers,problems,Map.of());
        }
    }
    private static final ThreadLocal<Boolean> SUPPRESSED=ThreadLocal.withInitial(()->false);

    /** Runs {@code body} as if no ingredient had a flavor (the "before" column of the preview). */
    public static <T> T withoutFlavors(java.util.function.Supplier<T> body) {
        boolean previous=SUPPRESSED.get();SUPPRESSED.set(true);
        try {return body.get();} finally {SUPPRESSED.set(previous);}
    }

    public static Resolved resolveStack(ItemStack stack,boolean drink) {
        if(SUPPRESSED.get() || !CuisineGameplayRules.flavorsEnabled())return new Resolved(List.of(),List.of(),FlavorModifiers.NONE,List.of());
        var sourceId=CuisineAlcohol.sourceId(stack);
        MealStamp stamp=MealStamp.read(stack);
        ResourceLocation dish=sourceId==null?net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()):sourceId;
        List<String> sources = new ArrayList<>(sourcesFor(dish));
        var actual=com.chi__you.cuisinejournal.food.IngredientProvenance.sources(stack);
        var dynamic=GrillingFoodCompat.actualSources(stack);if(dynamic!=null)actual=dynamic;
        if(actual!=null&&(CuisineFoodDefinitions.get(dish)==null||CuisineFoodDefinitions.get(dish).flavorSources()==null)){
            sources.clear();
            var own=IngredientCatalogueConfig.current().flavor(dish);
            if(own!=null&&own.source())sources.add(dish.toString());else sources.addAll(actual);
        }
        var definition = CuisineFoodDefinitions.get(dish);
        if (definition == null || definition.flavorSources() == null)
            for (ResourceLocation jelly : FruitJellyFlavorBridge.jellies(FruitJellyFlavorBridge.fruits(stack))) {
                sources.add(jelly.toString());
                sources.addAll(sourcesFor(jelly));
            }
        return resolveSources(sources,CuisineFoodQuality.stars(stack),drink,FlavorPresets.all(),
                !CuisineFoodRuntime.activeForCurrentThread());
    }

    public static int flavorStars(boolean dish, MealStamp stamp) {
        return CuisineFoodQuality.stars(dish,stamp);
    }
    public static List<CuisineFoodDefinitions.Effect> forStack(ItemStack stack) {
        var resolved=resolveStack(stack,CuisineFoodDefinition.isDrink(stack));
        return FlavorEffectLevels.apply(resolved.effects(),resolved.effectLevelBonuses(),
                CuisineFoodRuntime.activeForCurrentThread()?0:600);
    }

    private static volatile CulinaryRegistry tracedFor;
    private static final Map<ResourceLocation,List<String>> TRACED=new java.util.concurrent.ConcurrentHashMap<>();
    public static void clearTrace() { TRACED.clear(); tracedFor=null; }

    /** Explicit {@code flavor_sources}, or the ingredients traced from the dish's recipes. */
    public static List<String> sourcesFor(ResourceLocation dish) {
        if(dish==null)return List.of();
        var values=CuisineFoodDefinitions.get(dish);
        if(values!=null&&values.flavorSources()!=null)return values.flavorSources();
        CulinaryRegistry registry=CulinaryRegistries.current();
        if(tracedFor!=registry) {TRACED.clear();tracedFor=registry;}
        return TRACED.computeIfAbsent(dish,id-> {
            var trace=FlavorTrace.trace(id,registry::flavorInputs,registry::tagsOf,registry::flavorTagMembers,reference-> {
                var flavor=IngredientCatalogueConfig.current().flavor(IngredientCompatRules.current().alias(ResourceLocation.parse(reference)));
                return flavor!=null&&flavor.source();
            },item->{var definition=CuisineFoodDefinitions.get(item);return definition==null?null:definition.flavorSources();});
            if(trace.truncated())com.chi__you.cuisinejournal.CuisineJournalLog.LOGGER.warn("Flavor trace for {} exceeded 4096 nodes",id);
            return trace.sources();
        });
    }

    public static List<CuisineFoodDefinitions.Effect> resolveSources(List<String> references, int stars) {
        return resolveSources(references,stars,false,FlavorPresets.all()).effects();
    }

    public static Resolved resolveSources(List<String> references,int stars,boolean drink,
                                         Map<ResourceLocation,FlavorPresets.Preset> presets) {
        return resolveSources(references,stars,drink,presets,true);
    }

    public static Resolved resolveSources(List<String> references,int stars,boolean drink,
                                         Map<ResourceLocation,FlavorPresets.Preset> presets,boolean vanillaDuration) {
        if (!CuisineGameplayRules.flavorsEnabled()) return new Resolved(List.of(),List.of(),FlavorModifiers.NONE,List.of());
        Map<ResourceLocation, CuisineFoodDefinitions.Effect> effects = new LinkedHashMap<>();
        Set<String> resolved = new HashSet<>();
        Set<ResourceLocation> usedPresets=new LinkedHashSet<>();
        List<String> problems=new ArrayList<>();
        FlavorModifiers modifiers=FlavorModifiers.NONE;
        List<FlavorStage> selected=new ArrayList<>();
        Map<ResourceLocation,Integer> bonuses=new LinkedHashMap<>();
        List<String> queue=new ArrayList<>(references);
        CulinaryRegistry registry=CulinaryRegistries.current();
        for (int index=0;index<queue.size();index++) {
            String reference=queue.get(index);
            boolean tag = reference.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? reference.substring(1) : reference);
            if (id == null) { problems.add("Invalid flavor source: "+reference); continue; }
            var rules = IngredientCompatRules.current();
            var group = tag ? rules.mergeFor(id) : null;
            ResourceLocation key = tag ? group == null ? IngredientCompatRules.canonical(id) : group.id() : rules.alias(id);
            if (!resolved.add((tag ? "#" : "") + key)) continue;
            if (!tag) {
                // An item inherits the flavor of the tag entries it belongs to.
                for (ResourceLocation entry : registry.tagsOf(key)) queue.add("#" + entry);
            }
            List<ResourceLocation> tagIds = new ArrayList<>(List.of(key, id));
            if (tag) { var entry = registry.tags().get(key); if (entry != null) tagIds.addAll(entry.ids()); }
            var flavor = tag ? IngredientCatalogueConfig.current().tagFlavor(tagIds)
                    : IngredientCatalogueConfig.current().flavor(key);
            if (flavor == null || !flavor.source()) continue;
            var inline=FlavorStage.selectStage(flavor.stages(),stars);
            if(inline!=null)selected.add(inline);
            for(var presetId:flavor.flavors()) {
                if(!usedPresets.add(presetId))continue;
                var preset=presets.get(presetId);
                if(preset==null) {problems.add("Unknown flavor: "+presetId);continue;}
                if(!preset.enabled())continue;
                var stage=FlavorStage.selectStage(preset.stages(),stars);
                if(stage!=null)selected.add(stage);
            }
        }
        for(var stage:selected) {
            modifiers=modifiers.plus(stage.modifiersFor(drink));
            if (!stage.effectsApplyTo(drink)) continue;
            for(var bonus:stage.effectLevelBonuses())bonuses.merge(CuisineEffectIds.resolve(bonus.id()),bonus.levels(),(a,b)->Math.min(255,a+b));
            for (var effect : stage.effects()) {
                var effectId = CuisineEffectIds.resolve(effect.id());
                var next = new CuisineFoodDefinitions.Effect(effectId, effect.level(), effect.activeFraction(),
                        vanillaDuration ? effect.durationSeconds()*20 : 0);
                effects.merge(effectId, next, (a,b) -> a.level() != b.level() ? a.level() > b.level() ? a : b
                        : vanillaDuration ? a.durationTicks() >= b.durationTicks() ? a : b
                        : a.activeFraction() >= b.activeFraction() ? a : b);
            }
        }
        return new Resolved(new ArrayList<>(usedPresets),effects.values().stream().limit(64).toList(),modifiers,problems,bonuses);
    }

    public static void applyVanilla(net.minecraft.server.level.ServerPlayer player, ItemStack stack) {
        if (CuisineFoodRuntime.active()) return;
        for (var setting : forStack(stack)) {
            var effect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getOptional(setting.id()).orElse(null);
            if (effect == null || setting.activeFraction() <= 0) continue;
            if (effect.isInstantenous()) effect.applyInstantenousEffect(null, null, player, setting.level()-1, 1);
            else player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,
                    setting.durationTicks(), setting.level()-1, false, false));
        }
    }
}
