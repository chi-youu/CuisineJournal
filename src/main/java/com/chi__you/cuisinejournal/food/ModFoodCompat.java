package com.chi__you.cuisinejournal.food;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.effect.MobEffectInstance;
import java.util.*;

/** Explicit dispatch only; individual host algorithms live in their named adapters. */
public final class ModFoodCompat {
    private static final Set<String> HOSTS=Set.of("ltc2","crockpot","barbequesdelight","kitchenkarrot","drinkbeer",
            "cosmopolitan","bountifulfares","papercraft_magic_decoration","farmersrespite","nethervinery",
            "simple_cream","muffins_thaidelight","alexsmobsdelight","cavedelight","undergardendelight",
            "seasonals","peculiars","abnormals_delight","arsdelight","festive_delight","dumplings_delight",
            "slime_delight","jellyfishingdelight","crabbers_delight_expand","veggiesdelight","vanillacookbook",
            "natures_delight","ramadandelight","mineraldelight","galospheric_delight","onion_onion","sniffer","kaleidoscope_grilling",
            "untameddelight","delighto_flight","lendersdelight","aetherdelight","cornexpansion");
    private ModFoodCompat() {}

    public static boolean host(ItemStack stack){return !stack.isEmpty()&&HOSTS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());}
    public static boolean hostNamespace(String namespace){return HOSTS.contains(namespace);}
    private static boolean hasEffectPreview(ItemStack stack) {
        return (host(stack)||stack.hasTag()&&stack.getTag().contains("HotUntil"))&&!NetherVineryFoodCompat.delayed(stack);
    }

    /** Guaranteed native effects for display only; never supplies managed reserves. */
    public static List<MobEffectInstance> previewEffects(ItemStack stack) {
        if (hasEffectPreview(stack) && !LetsDoFood.host(stack)) return effects(stack, true);
        if (LetsDoFood.host(stack)) return LetsDoFood.effects(stack, true);
        return KaleidoscopeFood.effects(stack, true);
    }

    public static FoodProperties properties(ItemStack stack, FoodProperties original) {
        original=ProcedurePlacedFoods.properties(stack,original);
        original=CustomPlacedFoods.properties(stack,original);
        original=KitchenKarrotFoodCompat.properties(stack,original);
        return DrinkBeerFoodCompat.properties(stack,original);
    }

    public static List<MobEffectInstance> effects(ItemStack stack, boolean guaranteed) {
        List<MobEffectInstance> result=new ArrayList<>(KitchenKarrotFoodCompat.effects(stack));
        var mixed=DrinkBeerFoodCompat.effects(stack);
        if(mixed!=null){result.addAll(mixed);return List.copyOf(result);}
        var properties=stack.getItem().getFoodProperties(stack,null);properties=properties(stack,properties);
        if(properties!=null)for(var pair:properties.getEffects())if(pair.getSecond()>0&&(!guaranteed||pair.getSecond()>=1))result.add(new MobEffectInstance(pair.getFirst()));
        return List.copyOf(result);
    }
}
