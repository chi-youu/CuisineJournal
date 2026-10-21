package com.chi__you.cuisinejournal.food;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Existing effects retain their lifetime; absent effects start at level zero before adding the bonus. */
public final class FlavorEffectLevels {
    private FlavorEffectLevels() {}
    public static int level(int original,int bonus) {return (int)Math.min(256L,Math.max(1L,(long)original+Math.max(0,bonus)));}
    public static List<CuisineFoodDefinitions.Effect> apply(List<CuisineFoodDefinitions.Effect> effects,Map<ResourceLocation,Integer> bonuses) {
        return apply(effects,bonuses,0);
    }
    public static List<CuisineFoodDefinitions.Effect> apply(List<CuisineFoodDefinitions.Effect> effects,
            Map<ResourceLocation,Integer> bonuses,int newEffectDurationTicks) {
        Map<ResourceLocation,CuisineFoodDefinitions.Effect> result=new LinkedHashMap<>();
        for(var effect:effects)result.put(effect.id(),new CuisineFoodDefinitions.Effect(effect.id(),
                level(effect.level(),bonuses.getOrDefault(effect.id(),0)),effect.activeFraction(),effect.durationTicks()));
        bonuses.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            if(entry.getValue()>0)result.putIfAbsent(entry.getKey(),new CuisineFoodDefinitions.Effect(entry.getKey(),
                    level(0,entry.getValue()),1,newEffectDurationTicks));
        });
        return result.values().stream().limit(64).toList();
    }
}
